package ru.ravel.ultunneladminpanel.model

import org.jetbrains.annotations.Contract
import org.springframework.security.core.GrantedAuthority


enum class Role(
	val role: String
) : GrantedAuthority {
	ADMIN("Администратор"),
	;

	@Contract(pure = true)
	override fun getAuthority(): String {
		return "ROLE_${name}"
	}

}