package ru.ravel.ultunneladminpanel.model.sui

data class TlsServer(
	val enabled: Boolean,
	val key_path: String,
	val certificate_path: String,
	val server_name: String,
	val alpn: List<String>,
	val min_version: String,
	val max_version: String,
	val cipher_suites: List<String>
)