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

	override var server: String?,

	@JsonProperty("server_port")
	var serverPort: Long,
	@JsonProperty("password")
	var password: String,

	// TLS
	@JsonProperty("sni")
	var sni: String = "",

	@JsonProperty("alpn")
	var alpn: List<String> = listOf("h2", "http/1.1"),

	@JsonProperty("fp")
	var fp: String = "chrome"

) : ConfigData(id = id, type = type, server = server) {
	override fun fillFields(): ConfigDataTrojan {
		this.type = "trojan"
		this.url =
			"trojan://${password}@${server}:${serverPort}" +
					"?type=tcp&security=tls" +
					"&fp=${fp}" +
					"&alpn=${alpn.joinToString(",")}" +
					"&sni=${sni}"

		return this
	}

}