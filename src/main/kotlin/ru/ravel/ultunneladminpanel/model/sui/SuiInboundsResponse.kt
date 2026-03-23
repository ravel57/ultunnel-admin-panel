package ru.ravel.ultunneladminpanel.model.sui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class SuiInboundsResponse(
	val success: Boolean? = null,
	val msg: String? = null,
	val obj: SuiInboundsObj? = null,
)