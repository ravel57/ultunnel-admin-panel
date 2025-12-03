package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id


@Entity
data class ConfigDataTrojan(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "trojan",

	var tag: String? = "proxy",

	@JsonIgnore
	var trojanServer: String? = null,

	@JsonProperty("server_port")
	var serverPort: Long,

	@JsonProperty("password")
	var password: String,

	@JsonIgnore
	var sni: String? = null,

	@JsonIgnore
	var alpn: List<String>? = listOf("h2", "http/1.1"),

	@JsonIgnore
	var fp: String? = "chrome",

) : ConfigData(
	id = id,
	type = type,
	server = trojanServer ?: "",
	serverName = trojanServer
)
{

	@JsonProperty("tls")
	fun tls(): Map<String, Any?> {
		return mapOf(
			"enabled" to true,
			"server_name" to sni,
			"alpn" to alpn,
			"utls" to mapOf(
				"enabled" to true,
				"fingerprint" to fp
			)
		)
	}

	override fun fillFields(): ConfigDataTrojan {
		if (this.server.isNullOrBlank()) {
			throw IllegalStateException("Trojan server is null — ошибка генерации")
		}
		this.type = "trojan"
		this.tag = "proxy"
		this.url = buildString {
				append("trojan://${password}@${server}:${serverPort}")
				append("?type=tcp&security=tls")
				append("&fp=${fp}")
				append("&alpn=${alpn?.joinToString(",")}")
				append("&sni=${sni}")
			}
		return this
	}

}