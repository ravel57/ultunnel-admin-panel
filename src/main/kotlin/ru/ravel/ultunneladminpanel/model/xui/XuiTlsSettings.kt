package ru.ravel.ultunneladminpanel.model.xui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = true)
data class XuiTlsSettings(
	val serverName: String? = null,
	val minVersion: String? = null,
	val maxVersion: String? = null,
	val cipherSuites: String? = null,
	val rejectUnknownSni: Boolean? = null,
	val verifyPeerCertInNames: List<String>? = null,
	val disableSystemRoot: Boolean? = null,
	val enableSessionResumption: Boolean? = null,
	val certificates: List<CertObject>? = null,
	val alpn: List<String>? = null,

	val echServerKeys: String? = null,
	val echForceQuery: String? = null,

	val settings: TlsSettingsInternal? = null
)