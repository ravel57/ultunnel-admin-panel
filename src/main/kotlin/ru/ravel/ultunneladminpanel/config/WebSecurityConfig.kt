package ru.ravel.ultunneladminpanel.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import ru.ravel.ultunneladminpanel.service.AuthService


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class WebSecurityConfig(
	val authService: AuthService,
) {

	@Bean
	fun userDetailsService(): UserDetailsService {
		return authService
	}


	@Bean
	fun passwordEncoder(): PasswordEncoder {
		return BCryptPasswordEncoder(12)
	}


	@Bean
	fun sessionRegistry(): SessionRegistry {
		return SessionRegistryImpl()
	}


	@Bean
	fun authenticationProvider(): AuthenticationProvider {
		val provider = DaoAuthenticationProvider()
		provider.setUserDetailsService(userDetailsService())
		provider.setPasswordEncoder(passwordEncoder())
		return provider
	}



	@Bean
	@Throws(Exception::class)
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		return http
			.csrf { it.disable() }
			.authorizeHttpRequests { authorize ->
				authorize
					.requestMatchers("/js/**", "/css/**", "/logo.png", "/favicon.png").permitAll()
					.requestMatchers("/login", "/auth/**", "/api/v1/get-users-proxy-servers-singbox").permitAll()
					.anyRequest().authenticated()
			}
			.formLogin {}
			.build()
	}
}

