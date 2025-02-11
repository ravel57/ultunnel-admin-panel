package ru.ravel.ultunneladminpanel.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails


@Entity
class Admin(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	private var login: String? = null,

	private var password: String? = null,
) : UserDetails {

	override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
		return mutableListOf(Role.ADMIN)
	}

	override fun getPassword(): String {
		return password!!
	}

	override fun getUsername(): String {
		return login!!
	}

}