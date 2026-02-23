package ru.ravel.ultunneladminpanel.model.sui

data class RouteRule(
	val action: String,
	val protocol: List<String>? = null
)