package ru.ravel.ultunneladminpanel.model.sui

data class Client(
	val id: Long,
	val enable: Boolean,
	val name: String,
	val inbounds: List<Long>,
	val volume: Long,
	val expiry: Long,
	val down: Long,
	val up: Long,
	val desc: String,
	val group: String
)