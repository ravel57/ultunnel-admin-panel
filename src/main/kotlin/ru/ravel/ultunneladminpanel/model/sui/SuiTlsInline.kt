package ru.ravel.ultunneladminpanel.model.sui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class SuiTlsInline(
	val enabled: Boolean? = null,
	val insecure: Boolean? = null,
)