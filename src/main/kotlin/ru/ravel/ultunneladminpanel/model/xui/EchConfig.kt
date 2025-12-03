package ru.ravel.ultunneladminpanel.model.xui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class EchConfig(
	val enabled: Boolean? = null,
	val config: String? = null
)