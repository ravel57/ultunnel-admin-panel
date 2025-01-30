package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*


@Entity
data class ConfigDataWireguardPeer(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	var server: String? = null,

	@JsonProperty("server_port")
	var serverPort: Int? = null,

	@JsonProperty("public_key")
	var publicKey: String? = null,

	@JsonProperty("pre_shared_key")
	var preSharedKey: String? = null,

	@ElementCollection
	@JsonProperty("allowed_ips")
	var allowedIps: List<String>? = null,

	@ElementCollection
	var reserved: List<Int>? = null,

	@ManyToOne
	@JoinColumn(name = "config_id")
	var config: ConfigDataWireguard? = null
)
