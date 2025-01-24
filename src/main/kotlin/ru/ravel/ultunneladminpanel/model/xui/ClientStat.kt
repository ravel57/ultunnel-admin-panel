package ru.ravel.ultunneladminpanel.model.xui

data class ClientStat(
	val id: Long? = null,
	val inboundId: Long? = null,
	val enable: Boolean? = null,
	val email: String? = null,
	val up: Long? = null,
	val down: Long? = null,
	val expiryTime: Long? = null,
	val total: Long? = null,
	val reset: Long? = null,
)

