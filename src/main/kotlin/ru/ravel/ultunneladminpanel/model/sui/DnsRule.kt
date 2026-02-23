package ru.ravel.ultunneladminpanel.model.sui

data class DnsRule(
	val domain: List<String>? = null,
	val domainSuffix: List<String>? = null,
	val server: String? = null
)