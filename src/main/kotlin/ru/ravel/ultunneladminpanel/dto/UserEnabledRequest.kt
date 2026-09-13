package ru.ravel.ultunneladminpanel.dto

data class UserEnabledRequest(
	val userId: Long,
	val isEnabled: Boolean,
)
