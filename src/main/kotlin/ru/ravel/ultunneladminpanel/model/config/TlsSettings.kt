package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonProperty

data class TlsSettings(
	val enabled: Boolean = true,

	@JsonProperty("server_name")
	val serverName: String,

	val alpn: List<String> = listOf("h2"),

	val utls: UtlsSettings = UtlsSettings(),

//	val ech: EchSettings
)