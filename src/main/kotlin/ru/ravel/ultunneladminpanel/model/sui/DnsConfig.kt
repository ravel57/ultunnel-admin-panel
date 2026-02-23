package ru.ravel.ultunneladminpanel.model.sui

data class DnsConfig(
	val servers: List<DnsServer>,
	val rules: List<DnsRule>
)