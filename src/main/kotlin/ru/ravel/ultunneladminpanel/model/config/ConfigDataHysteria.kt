package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

@Entity
data class ConfigDataHysteria(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "hysteria2",

	@JsonProperty("down_mbps")
	@Transient
	var downMbps: Long? = 0,

	var password: String? = null,

	override var server: String?,

	@JsonProperty("server_port")
	var serverPort: Long? = null,

	@Transient
	var tls: Tls? = Tls(alpn = listOf("h3"), enabled = true, insecure = true),

	@JsonProperty("up_mbps")
	@Transient
	var upMbps: Long? = 0,

	@JsonProperty("domain_strategy")
	@Transient
	var domainStrategy: String? = "",

	@Transient
	var tag: String? = "proxy",

	/*
	 * The fields below are read from the 3x-ui hysteria inbound when the config is created and
	 * are stored as plain columns: tls/obfs above are rebuilt from them on every read, so a
	 * config keeps working after a panel restart without calling 3x-ui again.
	 */

	/* TLS SNI of the inbound (streamSettings.tlsSettings.serverName). */
	@JsonIgnore
	var sni: String? = null,

	/* ALPN of the inbound, comma separated; "h3" when the inbound does not set one. */
	@JsonIgnore
	var alpnList: String? = "h3",

	@JsonIgnore
	var insecure: Boolean? = true,

	/* salamander / gecko obfuscation of the inbound (streamSettings.finalmask), null when it is off. */
	@JsonIgnore
	var obfsType: String? = null,

	@JsonIgnore
	var obfsPassword: String? = null,

	/* UDP port hopping range of the inbound ("2000-3000,5000"), null when it is off. */
	@JsonIgnore
	var portHopping: String? = null,

	) : ConfigData(id = id, type = type, server = server) {

	@JsonIgnore
	fun alpn(): List<String> {
		return alpnList
			?.split(",")
			?.map { it.trim() }
			?.filter { it.isNotEmpty() }
			?.takeIf { it.isNotEmpty() }
			?: listOf("h3")
	}

	/* sing-box hysteria2 obfuscation block; omitted entirely when the inbound has no obfuscation. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("obfs")
	fun obfs(): Map<String, Any?>? {
		val password = obfsPassword?.takeIf { it.isNotBlank() } ?: return null
		return mapOf(
			"type" to (obfsType?.takeIf { it.isNotBlank() } ?: "salamander"),
			"password" to password,
		)
	}

	override fun fillFields(): ConfigDataHysteria {
		this.server = proxy?.serverIp ?: this.server
		this.type = super.type
		this.tag = "proxy"
		this.tls = Tls(
			alpn = alpn(),
			enabled = true,
			insecure = insecure ?: true,
			serverName = sni,
		)
		this.url = buildString {
			append("${type}://${password}@${server}:${serverPort}")
			append("?insecure=${if (insecure == false) 0 else 1}")
			sni?.takeIf { it.isNotBlank() }?.let { append("&sni=$it") }
			append("&alpn=${alpn().joinToString(",")}")
			obfsPassword?.takeIf { it.isNotBlank() }?.let {
				append("&obfs=${obfsType?.takeIf { type -> type.isNotBlank() } ?: "salamander"}")
				append("&obfs-password=${URLEncoder.encode(it, UTF_8.name())}")
			}
			portHopping?.takeIf { it.isNotBlank() }?.let { append("&mport=$it") }
		}
		return this
	}

}
