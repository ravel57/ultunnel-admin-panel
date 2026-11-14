package ru.ravel.ultunneladminpanel.service.control

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.repository.UserRepository

/**
 * Authentication for control.ultunnel.ru. There is no session and no password:
 * the caller — a client application or the user's browser — presents the secret
 * key the admin panel generated for that user.
 */
@Service
class ControlAuthService(
	private val userRepository: UserRepository,
) {

	/** Header, bearer token or query parameter, in that order. */
	fun extractKey(request: HttpServletRequest): String? {
		request.getHeader(SECRET_KEY_HEADER)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

		val authorization = request.getHeader("Authorization")?.trim().orEmpty()
		if (authorization.startsWith("Bearer ", ignoreCase = true)) {
			authorization.removeRange(0, 7).trim().takeIf { it.isNotEmpty() }?.let { return it }
		}

		// Last resort, for a client that can only build a URL. Kept out of the UI:
		// a key in a query string ends up in proxy and browser history logs.
		return request.getParameter("secretKey")?.trim()?.takeIf { it.isNotEmpty() }
	}

	fun authenticate(request: HttpServletRequest): User {
		val key = extractKey(request)
			?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Secret key is missing")
		return authenticate(key)
	}

	fun authenticate(secretKey: String): User {
		val user = userRepository.findBySecretKey(secretKey)
			?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown secret key")
		if (user.isEnabled == false) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, "The subscription is disabled")
		}
		return user
	}

	companion object {
		const val SECRET_KEY_HEADER = "X-Secret-Key"
		const val USER_ATTRIBUTE = "ru.ravel.ultunnel.control.user"
	}
}
