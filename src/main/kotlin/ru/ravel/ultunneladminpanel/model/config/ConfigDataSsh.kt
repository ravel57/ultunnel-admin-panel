package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*

@Entity
data class ConfigDataSsh(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	@JsonProperty("type")
	override var type: String? = "ssh",

	@JsonProperty("server")
	override var server: String?,

	@JsonProperty("server_port")
	var serverPort: Long,

	@Column(name = "user_name")
	var user: String,

	var password: String,
) : ConfigData(id = id, server = server, type = type) {
	override fun fillFields(): ConfigDataSsh {
		this.server = super.server
		this.type = super.type
		return this
	}
}