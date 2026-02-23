package ru.ravel.ultunneladminpanel.model.sui

data class InboundItem(
	val id: Long,
	val listen: String,
	val listen_port: Int,
	val tag: String,
	val tls_id: Long? = null,
	val type: String,
	val users: List<String>
)