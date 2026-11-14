package ru.ravel.ultunneladminpanel.service

import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.dto.BulkProxyError
import ru.ravel.ultunneladminpanel.dto.BulkProxyResult
import ru.ravel.ultunneladminpanel.dto.ProxyBulkRequest
import ru.ravel.ultunneladminpanel.dto.UserProxyTypeHost
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.config.ConfigData
import ru.ravel.ultunneladminpanel.repository.ConfigDataRepository
import ru.ravel.ultunneladminpanel.repository.ProxyRepository
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import ru.ravel.ultunneladminpanel.repository.UserRepository
import java.time.LocalDate


@Service
class UserService(
	private val userRepository: UserRepository,
	private val proxyServerRepository: ProxyServerRepository,
	private val proxyServerService: ProxyServerService,
	private val configDataRepository: ConfigDataRepository,
	/* Self reference: bulk actions must run every user in its own transaction, so they have to go through the proxy. */
	private val self: ObjectProvider<UserService>,
) {

	private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)

	fun getAllUsers(): List<User> {
		return userRepository.findAll().onEach {
			it?.proxiesConfigs?.map { proxyConfig ->
				proxyConfig.fillFields()
			}
		}
	}

	fun addNewUser(user: User): User {
		with(user) {
			secretKey = generateSecretKey()
			isEnabled = true
			isForFree = false
			createdDate = LocalDate.now()
			nextPaymentDate = createdDate!!.plusYears(1)
		}
		userRepository.save(user)
		return user
	}


	fun editUser(user: User): User {
		return userRepository.save(user)
	}


	/**
	 * Rotates the key a user authenticates with. It is the same key the client
	 * application uses for the configuration download and for control.ultunnel.ru,
	 * so rotating it logs every device of that user out of both at once.
	 */
	@Transactional
	fun regenerateSecretKey(userId: Long): User {
		val user = userRepository.findById(userId).orElseThrow()
		user.secretKey = generateSecretKey()
		return userRepository.save(user)
	}


	@Transactional
	fun setUserEnabled(userId: Long, isEnabled: Boolean): User {
		val user = userRepository.findById(userId).orElseThrow()
		user.isEnabled = isEnabled
		return userRepository.save(user)
	}


    @Transactional
    fun deleteProxyFromUser(request: UserProxyTypeHost) {
        userRepository.lockUser(request.userId) ?: error("User no longer exists")
        val user = userRepository.findById(request.userId).orElseThrow()
        val server = proxyServerRepository.findById(request.proxyServerId).orElseThrow()
        val proxy = server.proxies.orEmpty().firstOrNull { it.type == request.type }
            ?: error("Protocol is not configured on this server")
        val selected = user.proxiesConfigs.firstOrNull { config ->
            config.proxy?.id == proxy.id && (request.userProxyId == null || config.id == request.userProxyId)
        }
        // Retry after a successful deletion is harmless, and can clear a remaining remote identity.
        if (request.type != ru.ravel.ultunneladminpanel.model.ProxyType.SSH) {
            val outcome = proxyServerService.deleteUserProxiesBatch(request.proxyServerId, request.type, listOf(user))
            outcome[request.userId]?.getOrThrow() ?: error("Panel did not confirm client deletion")
            val proxyIds = server.proxies.orEmpty().mapNotNull { it.id }.toSet()
            val ids = user.proxiesConfigs.filter { it.proxy?.id in proxyIds && it.type == request.type.configType }
                .mapNotNull { it.id }.toSet()
            proxyServerService.deleteConfirmedConfigIds(proxyIds, request.type.configType, ids)
        } else if (selected != null) {
            user.proxiesConfigs.remove(selected)
            userRepository.saveAndFlush(user)
            configDataRepository.delete(selected)
        }
    }


	@Transactional
	fun addProxyToUser(userProxyTypeHost: UserProxyTypeHost): ConfigData {
        userRepository.lockUser(userProxyTypeHost.userId) ?: error("User no longer exists")
		val user = userRepository.findById(userProxyTypeHost.userId).orElseThrow()
		val proxyServer = proxyServerRepository.findById(userProxyTypeHost.proxyServerId).orElseThrow()
		/*
		 * Fail loudly instead of returning null: an empty 200 reaches the UI as a config card
		 * with nothing in it, which looks like the protocol was added while nothing was ever
		 * asked of the proxy panel.
		 */
		val proxy = proxyServer.proxies
			?.find { it.type == userProxyTypeHost.type }
			?: throw NoSuchElementException(
				"Server ${proxyServer.id} (${proxyServer.host}) has no ${userProxyTypeHost.type} proxy; " +
					"it has ${proxyServer.proxies.orEmpty().map { it.type }}"
			)

		val configData = try {
			proxyServerService.createUserProxy(proxyServer.host!!, proxy, user)
		} catch (e: Exception) {
			logger.error(
				"failed to add {} to user {} on server {} ({}): {}",
				userProxyTypeHost.type, user.name, proxyServer.id, proxyServer.host, e.message, e,
			)
			throw e
		}

		configData.serverName = proxyServer.name
		configDataRepository.save(configData)
		user.proxiesConfigs.add(configData)
		userRepository.save(user)
		return configData
	}


	/* Adds the protocol to every user. Users who already have it are skipped, one failed user does not stop the rest. */
    fun addProxyToAllUsers(request: ProxyBulkRequest): BulkProxyResult {
        val proxyId = requireProxyId(request)
        if (request.type == ru.ravel.ultunneladminpanel.model.ProxyType.SSH) {
            return forEachUser { user -> self.getObject().addProxyToUserIfAbsent(user.id!!, proxyId, request) }
        }
        val users = userRepository.findAll().filterNotNull().filter { it.id != null }
        val pending = users.filter { user -> user.proxiesConfigs.none { it.proxy?.id == proxyId } }
        var skipped = users.size - pending.size
        var affected = 0
        val errors = mutableListOf<BulkProxyError>()
        val remote = try {
            proxyServerService.createUserProxiesBatch(request.proxyServerId, proxyId, pending)
        } catch (error: Exception) {
            pending.associate { it.id!! to Result.failure<ConfigData>(error) }
        }
        pending.forEach { user ->
            try {
                val config = remote[user.id]?.getOrThrow() ?: error("Missing batch result")
                if (self.getObject().saveBatchProxyConfig(user.id!!, proxyId, request.proxyServerId, config)) affected++
                else skipped++
            } catch (error: Exception) {
                errors.add(BulkProxyError(user.id, user.name, error.message ?: "Bulk addition failed"))
            }
        }
        return BulkProxyResult(total = users.size, affected = affected, skipped = skipped,
            failed = errors.size, configsRemoved = affected, errors = errors)
    }

    @Transactional
    fun saveBatchProxyConfig(userId: Long, proxyId: Long, serverId: Long, config: ConfigData): Boolean {
        userRepository.lockUser(userId) ?: error("User no longer exists")
        val user = userRepository.findById(userId).orElseThrow()
        if (user.proxiesConfigs.any { it.proxy?.id == proxyId }) return false
        val server = proxyServerRepository.findById(serverId).orElseThrow()
        config.proxy = server.proxies.orEmpty().firstOrNull { it.id == proxyId }
            ?: error("Protocol no longer exists on the server")
        config.serverName = server.name
        configDataRepository.save(config)
        user.proxiesConfigs.add(config)
        userRepository.save(user)
        return true
    }


	/*
	 * Removes the protocol from every user, and from config_data as a whole.
	 * Going through User.proxiesConfigs alone is not enough: configs that are no
	 * longer linked to a user, and legacy configs pointing at a sibling proxy of
	 * the same server, would stay in the database. So the scope is every proxy of
	 * the server, restricted to configs of this protocol's config type.
	 */
    fun deleteProxyFromAllUsers(request: ProxyBulkRequest): BulkProxyResult {
        if (request.type == ru.ravel.ultunneladminpanel.model.ProxyType.SSH) {
            return self.getObject().deleteLocalProxyFromAllUsers(request)
        }
        val proxyIds = requireServerProxyIds(request)
        val users = userRepository.findAll().filterNotNull().filter { it.id != null }
        val configs = configDataRepository.findAllByProxy_IdInAndType(proxyIds, request.type.configType)
        val snapshotIds = configs.mapNotNull { it.id }.toSet()
        val byUser = users.associate { user -> user.id!! to user.proxiesConfigs
            .mapNotNull { it.id }.filter { it in snapshotIds }.toSet() }
        val remote = try {
            proxyServerService.deleteUserProxiesBatch(request.proxyServerId, request.type, users)
        } catch (error: Exception) {
            users.associate { it.id!! to Result.failure<Boolean>(error) }
        }
        val failures = mutableMapOf<Long, BulkProxyError>()
        val confirmed = mutableListOf<User>()
        val affected = mutableSetOf<Long>()
        users.forEach { user ->
            try {
                val changed = remote[user.id]?.getOrThrow() ?: error("Missing batch deletion result")
                if (changed) affected.add(user.id!!)
                confirmed.add(user)
            } catch (error: Exception) {
                failures[user.id!!] = BulkProxyError(user.id, user.name, error.message ?: "Panel deletion failed")
            }
        }
        var removed = 0
        confirmed.filter { byUser[it.id].orEmpty().isNotEmpty() }.chunked(200).forEach { batch ->
            val ids = batch.flatMap { byUser[it.id].orEmpty() }.toSet()
            try {
                removed += proxyServerService.deleteConfirmedConfigIds(proxyIds, request.type.configType, ids)
                batch.forEach { affected.add(it.id!!) }
            } catch (error: Exception) {
                batch.forEach { user -> failures[user.id!!] = BulkProxyError(user.id, user.name,
                    error.message ?: "Failed to save local deletion; retry the bulk action") }
            }
        }
        affected.removeAll(failures.keys)
        return BulkProxyResult(total = users.size, affected = affected.size,
            skipped = users.size - affected.size - failures.size, failed = failures.size,
            configsRemoved = removed, errors = failures.values.toList())
    }

	@Transactional
	fun deleteLocalProxyFromAllUsers(request: ProxyBulkRequest): BulkProxyResult {
		val serverProxyIds = requireServerProxyIds(request)
		val configType = request.type.configType

		// Count the users by the very config rows that are about to be deleted:
		// compare scalar IDs only, the way unlinkConfigsFromUsers does.
		val targetConfigIds = configDataRepository
			.findAllByProxy_IdInAndType(serverProxyIds, configType)
			.mapNotNull { it.id }
			.toHashSet()

		val users = userRepository.findAll().filterNotNull().filter { it.id != null }
		val affectedUserIds = users
			.filter { user ->
				user.proxiesConfigs.any { config ->
					val configId = config.id
					configId != null && targetConfigIds.contains(configId)
				}
			}
			.mapNotNull { it.id }
			.toSet()

		val configsRemoved = proxyServerService.deleteConfigsForProxies(serverProxyIds, configType)

		return BulkProxyResult(
			total = users.size,
			affected = affectedUserIds.size,
			skipped = users.size - affectedUserIds.size,
			failed = 0,
			configsRemoved = configsRemoved,
			errors = emptyList(),
		)
	}


	@Transactional
	fun addProxyToUserIfAbsent(userId: Long, proxyId: Long, request: ProxyBulkRequest): Boolean {
        userRepository.lockUser(userId) ?: error("User no longer exists")
		val user = userRepository.findById(userId).orElseThrow()
		if (user.proxiesConfigs.any { it.proxy?.id == proxyId }) {
			return false
		}
		addProxyToUser(UserProxyTypeHost(userId, request.type, request.proxyServerId))
		return true
	}


	private fun forEachUser(action: (User) -> Boolean): BulkProxyResult {
		val users = userRepository.findAll().filterNotNull().filter { it.id != null }
		var affected = 0
		var skipped = 0
		val errors = mutableListOf<BulkProxyError>()

		users.forEach { user ->
			try {
				if (action(user)) affected++ else skipped++
			} catch (e: Exception) {
				errors.add(
					BulkProxyError(
						userId = user.id,
						userName = user.name,
						message = e.message ?: e::class.simpleName ?: "Unknown error",
					)
				)
			}
		}

		return BulkProxyResult(
			total = users.size,
			affected = affected,
			skipped = skipped,
			failed = errors.size,
			configsRemoved = affected,
			errors = errors,
		)
	}


	/* Every proxy of the server: legacy configs may hang off a sibling proxy row. */
	private fun requireServerProxyIds(request: ProxyBulkRequest): Set<Long> {
		val proxyServer = proxyServerRepository.findById(request.proxyServerId).orElseThrow()
		val proxies = proxyServer.proxies.orEmpty()
		if (proxies.none { it.type == request.type }) {
			throw NoSuchElementException(
				"Proxy ${request.type} is not configured on server ${request.proxyServerId}"
			)
		}
		return proxies.mapNotNull { it.id }.toSet()
	}


	private fun requireProxyId(request: ProxyBulkRequest): Long {
		val proxyServer = proxyServerRepository.findById(request.proxyServerId).orElseThrow()
		val proxy = proxyServer.proxies
			?.firstOrNull { it.type == request.type }
			?: throw NoSuchElementException(
				"Proxy ${request.type} is not configured on server ${request.proxyServerId}"
			)
		return proxy.id ?: throw IllegalStateException("Proxy ${request.type} has no id")
	}


	companion object {
		private const val ALL_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

		fun generateSecretKey(): String {
			val password = StringBuilder()
			for (i in 0..20) {
				password.append(ALL_CHARACTERS.random())
			}
			return password.toList().shuffled().joinToString("")
		}
	}

}
