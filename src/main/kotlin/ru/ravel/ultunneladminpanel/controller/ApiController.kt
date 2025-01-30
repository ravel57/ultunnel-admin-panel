package ru.ravel.ultunneladminpanel.controller

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ravel.ultunneladminpanel.dto.UserProxyTypeHost
import ru.ravel.ultunneladminpanel.model.Proxy
import ru.ravel.ultunneladminpanel.model.ProxyServer
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.config.ConfigTemplate
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


	@GetMapping("/get-users-proxies")
	fun getUsersProxyServers(@RequestParam secretKey: String): ResponseEntity<Any> {
		return ResponseEntity.ok().body(proxyServerService.getProxyServer(secretKey))
	}


	@GetMapping("/get-users-proxy-servers-singbox")
	fun getUsersProxyServersSingbox(
		@RequestParam secretKey: String,
		response: HttpServletResponse,
	): ResponseEntity<Any> {
		val configData = proxyServerService.getProxyServer(secretKey)
		val configTemplate = configData.map { ConfigTemplate().getConfig(it) }
		val filename = "configs.json"
		response.contentType = "application/json"
		response.setHeader("Content-Disposition", "attachment; filename=\"$filename\"")
		response.outputStream.use { output ->
			output.write("[${configTemplate.joinToString(",\n")}]".toByteArray())
		}
		return ResponseEntity.ok().build()
	}


}