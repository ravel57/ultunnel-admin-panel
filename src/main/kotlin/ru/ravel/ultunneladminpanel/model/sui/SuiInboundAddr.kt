package ru.ravel.ultunneladminpanel.model.sui

data class SuiInboundAddr(
	val remark: String? = null,
	val server: String? = null,
	val server_port: Int? = null,
	val tls: SuiTlsInline? = null,
)