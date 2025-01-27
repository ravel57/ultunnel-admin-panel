package ru.ravel.ultunneladminpanel.service

import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.dto.UserProxyTypeHost
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.UsersProxy
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import ru.ravel.ultunneladminpanel.repository.UserProxyRepository
import ru.ravel.ultunneladminpanel.repository.UserRepository
import java.time.ZonedDateTime


@Service
class UserService(
	val userRepository: UserRepository,
	val userProxyRepository: UserProxyRepository,
	val proxyServerRepository: ProxyServerRepository,
	val proxyServerService: ProxyServerService,
) {

	fun addNewUser(user: User): User {
		user.secretKey = generateSecretKey()
		user.isEnabled = true
		user.createdDate = ZonedDateTime.now()
		userRepository.save(user)
		return user
	}


	fun editUser(user: User): User {
		return userRepository.save(user)
	}


	fun addProxyToUser(userProxyTypeHost: UserProxyTypeHost): UsersProxy? {
		val user = userRepository.findById(userProxyTypeHost.userId).orElseThrow()
		val proxyServer = proxyServerRepository.findById(userProxyTypeHost.proxyServerId).orElseThrow()
		val usersProxy = proxyServer
			?.proxies
			?.find { it.type == userProxyTypeHost.type }
			?.let { proxy ->
				val connectionData = proxyServerService.createUserProxy(proxyServer.host!!, proxy, user)
				val usersProxy = UsersProxy(connectionData = connectionData)
				userProxyRepository.save(usersProxy)
				user.proxies.add(usersProxy)
				userRepository.save(user)
				return@let usersProxy
			}
		return usersProxy
	}

}


fun generateSecretKey(): String {
	val allCharacters: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	val password = StringBuilder()
	for (i in 0..20) {
		password.append(allCharacters.random())
	}
	return password.toList().shuffled().joinToString("")
}