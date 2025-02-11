package ru.ravel.ultunneladminpanel.service

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.repository.AdminRepository

@Service
class AuthService(
	var repository: AdminRepository
) : UserDetailsService {

	@Throws(UsernameNotFoundException::class)
	override fun loadUserByUsername(username: String): UserDetails {
		return repository.findByLogin(username.trim())
			?: throw UsernameNotFoundException("incorrect login")
	}

}