//package ru.ravel.ultunneladminpanel.controller
//
//import jakarta.servlet.http.HttpServletResponse
//import org.springframework.http.ResponseEntity
//import org.springframework.security.core.Authentication
//import org.springframework.security.core.context.SecurityContextHolder
//import org.springframework.web.bind.annotation.GetMapping
//import org.springframework.web.bind.annotation.RequestMapping
//import org.springframework.web.bind.annotation.RestController
//
//@RestController
//@RequestMapping("/auth")
//class AuthController {
//
//	@GetMapping("/status")
//	fun getStatus(response: HttpServletResponse): ResponseEntity<Any> {
//		val auth: Authentication? = SecurityContextHolder.getContext().authentication
//		return when {
//			auth != null && auth.isAuthenticated && auth.principal != "anonymousUser" -> {
//				response.status = HttpServletResponse.SC_OK
//				ResponseEntity.ok(mapOf("authenticated" to auth.isAuthenticated, "user" to auth.name))
//			}
//			auth != null -> {
//				response.status = HttpServletResponse.SC_UNAUTHORIZED
//				ResponseEntity.ok(mapOf("authenticated" to auth.isAuthenticated))
//			}
//			else -> {
//				ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body(mapOf("authenticated" to false))
//			}
//		}
//	}
//}