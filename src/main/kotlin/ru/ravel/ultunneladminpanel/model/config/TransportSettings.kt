package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonProperty


data class TransportSettings(
	@JsonProperty("type")
	val type: String = "grpc",

	@JsonProperty("service_name")
	val serviceName: String = "GunService",

	@JsonProperty("idle_timeout")
	val idleTimeout: String = "15s",

	@JsonProperty("ping_timeout")
	val pingTimeout: String = "15s",

	@JsonProperty("permit_without_stream")
	val permitWithoutStream: Boolean = false
)