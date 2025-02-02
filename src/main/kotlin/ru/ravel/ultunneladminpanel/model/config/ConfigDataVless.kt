package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id


@Entity
data class ConfigDataVless(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "vless",

	var tag: String? = "proxy",

	override var server: String?,

	@JsonProperty("server_port")
	var serverPort: Long,

	var uuid: String,

	@JsonProperty("packet_encoding")
	var packetEncoding: String = ""

) : ConfigData(id = id, type = type, server = server) {
	override fun fillFields(): ConfigDataVless {
		this.server = super.server
		this.type = super.type
		this.tag = "proxy"
		return this
	}
}