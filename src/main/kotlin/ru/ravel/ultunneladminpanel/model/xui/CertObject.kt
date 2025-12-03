package ru.ravel.ultunneladminpanel.model.xui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class CertObject(
	val certificateFile: String? = null,
	val keyFile: String? = null,
	val ocspStapling: Int? = null,
	val usage: String? = null,
	val certificate: List<String>? = null,
	val key: List<String>? = null
)