//package ru.ravel.ultunneladminpanel.component
//
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.security.authentication.AuthenticationProvider
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
//import org.springframework.security.core.Authentication
//import org.springframework.security.core.GrantedAuthority
//import org.springframework.security.core.userdetails.User
//import org.springframework.security.core.userdetails.UserDetails
//import org.springframework.stereotype.Component
//
//@Component
//class CustomAuthenticationProvider : AuthenticationProvider {
//
//	@Value("\${auth.login}")
//	lateinit var login: String
//
//	@Value("\${auth.password}")
//	lateinit var password: String
//
//	override fun authenticate(authentication: Authentication): Authentication? {
//		val username = authentication.name
//		val credentials = authentication.credentials.toString()
//
//		return if (username == login && credentials == password) {
//			val authorities: List<GrantedAuthority> = emptyList()
//			val userDetails: UserDetails = User(username, password, authorities)
//			UsernamePasswordAuthenticationToken(userDetails, password, authorities)
//		} else {
//			null
//		}
//	}
//
//	override fun supports(authentication: Class<*>): Boolean {
//		return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
//	}
//}