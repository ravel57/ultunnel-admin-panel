package ru.ravel.ultunneladminpanel.model.sui

data class TlsItem(
	val id: Long,
	val name: String,
	val server: TlsServer,
	val client: TlsClient
)