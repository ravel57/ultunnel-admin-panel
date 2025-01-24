package ru.ravel.ultunneladminpanel.service

import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.dto.UserProxyTypeHost
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.UsersProxy
import ru.ravel.ultunneladminpanel.repository.ProxyRepository
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import ru.ravel.ultunneladminpanel.repository.UserProxyRepository
import ru.ravel.ultunneladminpanel.repository.UserRepository


@Service
class UserService(
	val userRepository: UserRepository,
	val userProxyRepository: UserProxyRepository,
	val proxyServerRepository: ProxyServerRepository,
	val proxyRepository: ProxyRepository,
	val proxyServerService: ProxyServerService
) {

	companion object {
		private const val ALL_CHARACTERS: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	}


	fun addNewUser(user: User): User {
		user.secretKey = generateSecretKey()
		userRepository.save(user)
		return user
	}


	fun generateSecretKey(): String {
		val password = StringBuilder()
		for (i in 0..20) {
			password.append(ALL_CHARACTERS.random())
		}
		return password.toList().shuffled().joinToString("")
	}


	fun addProxyToUser(userProxyTypeHost: UserProxyTypeHost): UsersProxy? {
		val user = userRepository.findById(userProxyTypeHost.userId).orElseThrow()
		val usersProxy = proxyServerRepository.findAll()
			.find { it.host == userProxyTypeHost.host }
			?.proxies
			?.find { it.type == userProxyTypeHost.type }
			?.let { proxy ->
				val connectionData = proxyServerService.createUserProxy(userProxyTypeHost.host, proxy, user)
				val usersProxy = UsersProxy(connectionData = connectionData)
				userProxyRepository.save(usersProxy)
				user.proxies.add(usersProxy)
				userRepository.save(user)
				return@let usersProxy
			}
		return usersProxy
	}

}