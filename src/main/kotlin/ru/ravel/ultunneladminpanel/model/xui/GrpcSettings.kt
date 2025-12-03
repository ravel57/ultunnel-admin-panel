package ru.ravel.ultunneladminpanel.model.xui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class GrpcSettings(
	val serviceName: String? = null,

	val authority: String? = null,

	val idleTimeout: String? = null,

	val multiMode: Boolean? = null,

	val initialWindowsSize: Int? = null,

	val permitWithoutStream: Boolean? = null,

	val pingTimeout: String? = null,
)