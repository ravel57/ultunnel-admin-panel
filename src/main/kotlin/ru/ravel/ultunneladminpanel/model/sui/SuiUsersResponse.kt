package ru.ravel.ultunneladminpanel.model.sui


data class SuiUsersResponse(
	val success: Boolean,
	val msg: String,
	val obj: ObjUsersResponse
)