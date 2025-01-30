package ru.ravel.ultunneladminpanel.dto

import java.util.*

data class HysteriaUser (
	var name: String,
	var password: String,
	var enabled: Boolean,
	var uuid: UUID,
)