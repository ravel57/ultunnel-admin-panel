package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*

/**
 * WireGuard is an *endpoint* in sing-box since 1.11.0 (the classic outbound was removed in
 * 1.13.0): no more top-level server/server_port/peer_public_key, and the fields below that
 * described the single peer of that outbound now build a one-element "peers" array instead
 * (see [peersJson]). "system_interface"/"interface_name"/"network"/"gso" do not exist on the
 * endpoint at all, so they stay out of the JSON (DB columns are untouched for parsers that
 * still fill them, e.g. [ru.ravel.ultunneladminpanel.service.WireguardConfigParser]).
 */
@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
class ConfigDataWireguard(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "wireguard",

	var tag: String? = "proxy",

	server: String? = null,

	@JsonIgnore
	var serverPort: Long? = null,

	@JsonIgnore
	var systemInterface: Boolean? = null,

	@JsonIgnore
	var interfaceName: String? = null,

	@ElementCollection
	@JsonProperty("address")
	var localAddress: List<String>? = null,

	@JsonProperty("private_key")
	var privateKey: String? = null,

	@JsonIgnore
	@OneToMany(
		mappedBy = "config",
		fetch = FetchType.EAGER,
		cascade = [CascadeType.ALL],
		targetEntity = ConfigDataWireguardPeer::class
	)
	var peers: List<ConfigDataWireguardPeer>? = null,

	@JsonIgnore
	var peerPublicKey: String? = null,

	@JsonIgnore
	var preSharedKey: String? = null,

	@JsonIgnore
	@ElementCollection
	var reserved: List<Int>? = null,

	var workers: Int? = null,

	var mtu: Int? = null,

	@JsonIgnore
	var network: String? = null,

	@JsonIgnore
	var gso: Boolean? = null,

) : ConfigData(id = id, type = type, server = server) {

    // Hibernate persists the inherited field; avoid a second, unmapped backing field.
    @get:JsonIgnore
    override var server: String?
        get() = super.server
        set(value) { super.server = value }

	@get:JsonProperty("peers")
	val peersJson: List<WireGuardPeerJson>?
		get() = buildWireGuardPeers(peers, server, serverPort, peerPublicKey, preSharedKey, reserved)

	override fun fillFields(): ConfigDataWireguard {
		this.server = this.server ?: proxy?.serverIp
		this.type = "wireguard"
		this.tag = "proxy"
		this.url = ""
		return this
	}

}
