package ru.ravel.ultunneladminpanel.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ravel.ultunneladminpanel.dto.UserProxyTypeHost
import ru.ravel.ultunneladminpanel.model.Proxy
import ru.ravel.ultunneladminpanel.model.ProxyServer
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.service.ProxyServerService
import ru.ravel.ultunneladminpanel.service.UserService


@RestController
@RequestMapping("/api/v1")
class ApiController(
	val proxyServerService: ProxyServerService,
	val userService: UserService,
) {

	@PostMapping("/add-new-server")
	fun addServer(
		@RequestBody proxyServer: ProxyServer,
	): ResponseEntity<Any> {
		return ResponseEntity.ok().body(proxyServerService.addNewServer(proxyServer))
	}

	@PostMapping("/edit-server")
	fun editServer(
		@RequestBody proxyServer: ProxyServer,
	): ResponseEntity<Any> {
		return ResponseEntity.ok().body(proxyServerService.editServer(proxyServer))
	}


	@PostMapping("/add-proxy-to/{proxyServerId}")
	fun addProxyToServer(
		@PathVariable proxyServerId: Long,
		@RequestBody proxy: Proxy,
	): ResponseEntity<Any> {
		return ResponseEntity.ok().body(proxyServerService.addProxyToServer(proxyServerId, proxy))
	}


	@PostMapping("/add-new-user")
	fun addNewUser(
		@RequestBody user: User,
	): ResponseEntity<Any> {
		return ResponseEntity.ok().body(userService.addNewUser(user))
	}


	@PostMapping("/edit-user")
	fun editUser(
		@RequestBody user: User,
	): ResponseEntity<Any> {
		return ResponseEntity.ok().body(userService.editUser(user))
	}


	@PostMapping("/add-proxy-to-user")
	fun addProxyToUser(
		@RequestBody userProxyTypeHost: UserProxyTypeHost,
	): ResponseEntity<Any> {
		return ResponseEntity.ok().body(userService.addProxyToUser(userProxyTypeHost))
	}


	@GetMapping("/get-user-proxy")
	fun getProxyServer(@RequestParam secretKey: String): ResponseEntity<Any> {
		return ResponseEntity.ok().body(proxyServerService.getProxyServer(secretKey))
	}

}