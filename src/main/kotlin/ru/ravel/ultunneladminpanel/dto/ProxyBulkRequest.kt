package ru.ravel.ultunneladminpanel.dto

import ru.ravel.ultunneladminpanel.model.ProxyType

data class ProxyBulkRequest(
	val type: ProxyType,
	val proxyServerId: Long,
)
