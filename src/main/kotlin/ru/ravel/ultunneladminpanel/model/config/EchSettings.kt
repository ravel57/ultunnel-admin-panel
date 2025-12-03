package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonProperty


data class EchSettings(
	val enabled: Boolean = true,
	@JsonProperty("pq_signature_schemes_enabled")
	val pqSignatureSchemesEnabled: Boolean = true,
	val config: List<String> = emptyList()
)