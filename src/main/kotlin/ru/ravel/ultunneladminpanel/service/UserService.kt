package ru.ravel.ultunneladminpanel.service

import jakarta.transaction.Transactional
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


	@Transactional
	fun setUserEnabled(userId: Long, isEnabled: Boolean): User {
		val user = userRepository.findById(userId).orElseThrow()
		user.isEnabled = isEnabled
		return userRepository.save(user)
	}


	@Transactional
	fun deleteProxyFromUser(userProxyTypeHost: UserProxyTypeHost) {
		val user = userRepository.findById(userProxyTypeHost.userId).orElseThrow()
		val proxyServer = proxyServerRepository.findById(userProxyTypeHost.proxyServerId).orElseThrow()
		val proxy = proxyServer.proxies
			?.firstOrNull { it.type == userProxyTypeHost.type }
			?: throw NoSuchElementException(
				"Proxy ${userProxyTypeHost.type} is not configured on server ${userProxyTypeHost.proxyServerId}"
			)

		val proxyId = proxy.id
			?: throw IllegalStateException("Proxy ${userProxyTypeHost.type} has no id")
		val config = user.proxiesConfigs.firstOrNull { config ->
			config.proxy?.id == proxyId &&
				(userProxyTypeHost.userProxyId == null || config.id == userProxyTypeHost.userProxyId)
		}
			?: throw NoSuchElementException(
				"User ${userProxyTypeHost.userId} does not have proxy ${userProxyTypeHost.type} on server ${userProxyTypeHost.proxyServerId}"
			)

		user.proxiesConfigs.remove(config)
		userRepository.saveAndFlush(user)
		configDataRepository.delete(config)
	}


	@Transactional
	fun addProxyToUser(userProxyTypeHost: UserProxyTypeHost): ConfigData? {
		val user = userRepository.findById(userProxyTypeHost.userId).orElseThrow()
		val proxyServer = proxyServerRepository.findById(userProxyTypeHost.proxyServerId).orElseThrow()
		val configData = proxyServer
			?.proxies
			?.find { it.type == userProxyTypeHost.type }
			?.let { proxy ->
				val configData = proxyServerService.createUserProxy(proxyServer.host!!, proxy, user)
				configData.serverName = proxyServer.name
				configDataRepository.save(configData)
				user.proxiesConfigs.add(configData)
				userRepository.save(user)
				return@let configData
			}
		return configData
	}


	/* Adds the protocol to every user. Users who already have it are skipped, one failed user does not stop the rest. */
	fun addProxyToAllUsers(request: ProxyBulkRequest): BulkProxyResult {
		val proxyId = requireProxyId(request)
		return forEachUser { user ->
			self.getObject().addProxyToUserIfAbsent(user.id!!, proxyId, request)
		}
	}


	/*
	 * Removes the protocol from every user, and from config_data as a whole.
	 * Going through User.proxiesConfigs alone is not enough: configs that are no
	 * longer linked to a user, and legacy configs pointing at a sibling proxy of
	 * the same server, would stay in the database. So the scope is every proxy of
	 * the server, restricted to configs of this protocol's config type.
	 */
	@Transactional
	fun deleteProxyFromAllUsers(request: ProxyBulkRequest): BulkProxyResult {
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
		val user = userRepository.findById(userId).orElseThrow()
		if (user.proxiesConfigs.any { it.proxy?.id == proxyId }) {
			return false
		}
		addProxyToUser(UserProxyTypeHost(userId, request.type, request.proxyServerId))
			?: throw NoSuchElementException(
				"Proxy ${request.type} is not configured on server ${request.proxyServerId}"
			)
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
