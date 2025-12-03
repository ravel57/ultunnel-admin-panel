package ru.ravel.ultunneladminpanel.model.xui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamSettings(
	val network: String? = null,
	val security: String? = null,
	val grpcSettings: GrpcSettings? = null,
	val tlsSettings: XuiTlsSettings? = null
)