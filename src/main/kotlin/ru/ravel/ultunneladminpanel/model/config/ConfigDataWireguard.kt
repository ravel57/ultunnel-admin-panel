package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*

@Entity
class ConfigDataWireguard(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "wireguard",

	var tag: String? = type?.lowercase(),

	override var server: String? = null,

	@JsonProperty("server_port")
	var serverPort: Long? = null,

	@JsonProperty("system_interface")
	var systemInterface: Boolean? = null,

	@JsonProperty("interface_name")
	var interfaceName: String? = null,

	@ElementCollection
	@JsonProperty("local_address")
	var localAddress: List<String>? = null,

	@JsonProperty("private_key")
	var privateKey: String? = null,

	@OneToMany(
		mappedBy = "config",
		fetch = FetchType.EAGER,
		cascade = [CascadeType.ALL],
		targetEntity = ConfigDataWireguardPeer::class
	)
	var peers: List<ConfigDataWireguardPeer>? = null,

	@JsonProperty("peer_public_key")
	var peerPublicKey: String? = null,

	@JsonProperty("pre_shared_key")
	var preSharedKey: String? = null,

	@ElementCollection
	var reserved: List<Int>? = null,

	var workers: Int? = null,

	var mtu: Int? = null,

	var network: String? = null,

	var gso: Boolean? = null

) : ConfigData(id = id, type = type, server = server) {
	override fun fillFields(): ConfigDataWireguard {
		this.server = super.server
		this.type = super.type
		this.tag = type?.lowercase()
		this.url = ""
		return this
	}
}
