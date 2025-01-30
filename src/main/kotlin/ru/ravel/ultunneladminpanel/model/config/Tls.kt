package ru.ravel.ultunneladminpanel.model.config

data class Tls(
	var alpn: List<String>,
	var enabled: Boolean,
	var insecure: Boolean,
)