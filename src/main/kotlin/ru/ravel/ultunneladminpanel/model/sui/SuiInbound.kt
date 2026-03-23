package ru.ravel.ultunneladminpanel.model.sui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class SuiInbound(
	val addrs: List<SuiInboundAddr>? = null,
	val id: Long? = null,
	val listen: String? = null,
	val listen_port: Int? = null,
	val out_json: OutJson? = null,
	val tag: String? = null,
	val tls_id: Long? = null,
	val type: String? = null,
)