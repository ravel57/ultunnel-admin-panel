package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty


@JsonInclude(JsonInclude.Include.NON_NULL)
data class TlsSettings(
	val enabled: Boolean = true,

	@JsonProperty("server_name")
	val serverName: String,

	val alpn: List<String>? = null,

	val utls: UtlsSettings? = null,

//	val ech: EchSettings
)