package ru.ravel.ultunneladminpanel.model.xui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class TlsSettingsInternal(
	val allowInsecure: Boolean? = null,
	val fingerprint: String? = null,
	val echConfigList: String? = null
)