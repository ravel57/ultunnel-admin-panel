package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

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

	var password: String,

	override var server: String?,

	@JsonProperty("server_port")
	var serverPort: Long,

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
) : ConfigData(id = id, type = type, server = server) {

	override fun fillFields(): ConfigDataHysteria {
		this.server = super.server
		this.type = super.type
		this.tag = "proxy"
		this.tls = Tls(alpn = listOf("h3"), enabled = true, insecure = true)
		this.url = "${type}://${password}@${server}:${serverPort}?insecure=1"
		return this
	}

}