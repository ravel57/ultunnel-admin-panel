package ru.ravel.ultunneladminpanel.model.config

data class ConfigTemplateWithServers(
	val server: String,
	val configs: List<String>,
)