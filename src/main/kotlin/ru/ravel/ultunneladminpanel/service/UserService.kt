package ru.ravel.ultunneladminpanel.service

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.dto.UserProxyTypeHost
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.config.ConfigData
import ru.ravel.ultunneladminpanel.repository.*
import java.time.ZonedDateTime


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
			it?.proxiesConfigs
				?.map { proxyConfig ->
					proxyConfig.fillFields()
				}
		}
	}

	fun addNewUser(user: User): User {
		user.secretKey = generateSecretKey()
		user.isEnabled = true
		user.createdDate = ZonedDateTime.now()
		user.nextPaymentDate = user.createdDate?.plusYears(1)
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
		fun generateSecretKey(): String {
			val allCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
			val password = StringBuilder()
			for (i in 0..20) {
				password.append(allCharacters.random())
			}
			return password.toList().shuffled().joinToString("")
		}
	}

}