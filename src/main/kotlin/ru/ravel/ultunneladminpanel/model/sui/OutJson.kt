package ru.ravel.ultunneladminpanel.model.sui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class OutJson(
	val dummy: String? = null
)