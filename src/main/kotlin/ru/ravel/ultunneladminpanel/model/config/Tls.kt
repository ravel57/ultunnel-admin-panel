package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty


@JsonInclude(JsonInclude.Include.NON_NULL)
data class Tls(
	var alpn: List<String>,
	var enabled: Boolean,
	var insecure: Boolean,

	/* sing-box needs the SNI explicitly: 3x-ui issues the certificate for the inbound's serverName, not for the bare IP. */
	@JsonProperty("server_name")
	var serverName: String? = null,
)