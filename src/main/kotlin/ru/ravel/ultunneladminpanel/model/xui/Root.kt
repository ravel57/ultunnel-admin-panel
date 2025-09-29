package ru.ravel.ultunneladminpanel.model.xui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class Root(
	val success: Boolean? = null,
	val msg: String? = null,
	val obj: List<Obj>? = null,
)