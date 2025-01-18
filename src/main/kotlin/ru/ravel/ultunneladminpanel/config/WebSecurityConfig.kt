//package ru.ravel.ultunneladminpanel.config
//
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.security.authentication.AuthenticationManager
//import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
//import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
//import org.springframework.security.config.annotation.web.builders.HttpSecurity
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
//import org.springframework.security.crypto.password.NoOpPasswordEncoder
//import org.springframework.security.crypto.password.PasswordEncoder
//import org.springframework.security.web.SecurityFilterChain
//
//@Configuration
//@EnableWebSecurity
//@EnableMethodSecurity
//class WebSecurityConfig {
//
//	@Bean
//	@Throws(Exception::class)
//	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
//		return http
//			.csrf { it.disable() }
//			.authorizeHttpRequests { authorize ->
//				authorize
//					.requestMatchers("/js/**", "/css/**", "/logo.png", "/favicon.png", "/login", "/auth/**").permitAll()
//					.anyRequest().authenticated()
//			}
//			.formLogin { form ->
//				form
//					.loginPage("/login")
//					.loginProcessingUrl("/auth/login")
//					.defaultSuccessUrl("/", true)
//					.permitAll()
//			}
//			.logout { logout ->
//				logout
//					.logoutUrl("/auth/logout")
//					.logoutSuccessUrl("/login")
//					.invalidateHttpSession(true)
//					.deleteCookies("JSESSIONID")
//					.permitAll()
//			}
//			.build()
//	}
//
//	@Bean
//	@Throws(Exception::class)
//	fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
//		return authConfig.authenticationManager
//	}
//
//	@Bean
//	fun passwordEncoder(): PasswordEncoder {
//		return NoOpPasswordEncoder.getInstance()
//	}
//}
//
