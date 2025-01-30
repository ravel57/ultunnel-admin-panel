package ru.ravel.ultunneladminpanel.model.xui

data class Obj(
	val id: Long? = null,
	val up: Long? = null,
	val down: Long? = null,
	val total: Long? = null,
	val remark: String? = null,
	val enable: Boolean? = null,
	val expiryTime: Long? = null,
	val clientStats: List<ClientStat>? = null,
	val listen: String? = null,
	val port: Long? = null,
	val protocol: String? = null,
	val settings: String? = null,
	val streamSettings: String? = null,
	val tag: String? = null,
	val sniffing: String? = null,
	val allocate: String? = null,
)