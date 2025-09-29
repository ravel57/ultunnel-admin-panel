package ru.ravel.ultunneladminpanel.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class HysteriaUser (
	var name: String? = null,
	var password: String? = null,
	var enabled: Boolean? = null,
	var uuid: String? = null,
)