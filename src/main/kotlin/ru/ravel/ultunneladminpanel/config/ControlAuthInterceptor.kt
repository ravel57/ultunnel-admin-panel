package ru.ravel.ultunneladminpanel.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import ru.ravel.ultunneladminpanel.service.control.ControlAuthService

/**
 * Resolves the secret key of every /control/api/v1 call into a user and parks it
 * on the request, so the controller can take it as a @RequestAttribute instead of
 * repeating the lookup. A bad key never reaches the controller: the service throws
 * a ResponseStatusException that RequestErrorHandler turns into a JSON 401/403.
 */
@Component
class ControlAuthInterceptor(
	private val controlAuthService: ControlAuthService,
) : HandlerInterceptor {

	override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
		// CORS preflight carries no headers of its own to authenticate with.
		if (request.method.equals("OPTIONS", ignoreCase = true)) {
			return true
		}
		request.setAttribute(ControlAuthService.USER_ATTRIBUTE, controlAuthService.authenticate(request))
		return true
	}
}
