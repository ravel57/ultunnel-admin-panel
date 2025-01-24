package ru.ravel.ultunneladminpanel.dto

import ru.ravel.ultunneladminpanel.model.ProxyType

data class UserProxyTypeHost(
	val userId: Long,
	val type: ProxyType,
	val host: String,
)
