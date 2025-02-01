package ru.ravel.ultunneladminpanel.dto

import java.util.*

data class HysteriaUser (
	var name: String? = null,
	var password: String? = null,
	var enabled: Boolean? = null,
	var uuid: String? = null,
)