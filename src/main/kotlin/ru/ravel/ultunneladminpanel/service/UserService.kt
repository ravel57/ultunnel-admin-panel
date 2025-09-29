package ru.ravel.ultunneladminpanel.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
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
	private val proxyRepository: ProxyRepository,
	private val configDataRepository: ConfigDataRepository,
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