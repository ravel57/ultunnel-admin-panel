package ru.ravel.ultunneladminpanel.model.sui

data class SingBoxConfig(
	val log: LogConfig,
	val dns: DnsConfig,
	val route: RouteConfig,
	val experimental: ExperimentalConfig
)