package ru.ravel.ultunneladminpanel.dto

data class BulkProxyResult(
	val total: Int,
	val affected: Int,
	val skipped: Int,
	val failed: Int,
	/* Config rows actually deleted, including ones no longer linked to any user. */
	val configsRemoved: Int,
	val errors: List<BulkProxyError>,
)

data class BulkProxyError(
	val userId: Long?,
	val userName: String?,
	val message: String,
)
