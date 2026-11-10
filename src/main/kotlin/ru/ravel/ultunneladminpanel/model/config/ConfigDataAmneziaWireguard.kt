package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*
import java.util.Base64

/**
 * AmneziaWG config of one user, provisioned through the 3x-ui panel ("amneziawg" inbound).
 *
 * The structured fields below describe the tunnel the way a WireGuard outbound does, while
 * [clientConfig] keeps the full AmneziaWG .conf text the panel's own settings produce — the
 * obfuscation parameters (Jc/Jmin/Jmax, S1-S4, H1-H4, I1-I5, AWG 3.x options) have no place in
 * a WireGuard outbound, and a client that drops any of them cannot complete the handshake.
 * [url] is the `vpn://` link the AmneziaVPN app imports, built from that same text.
 */
@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
class ConfigDataAmneziaWireguard(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "amnezia_wireguard",

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

	var gso: Boolean? = null,

	/* DNS servers of the inbound, comma separated. */
	@JsonIgnore
	var dns: String? = null,

	/* The full AmneziaWG client .conf, exactly as the panel's own export produces it. */
	@JsonIgnore
	@Column(columnDefinition = "text")
	var clientConfig: String? = null,

	) : ConfigData(id = id, type = type, server = server) {

	override fun fillFields(): ConfigDataAmneziaWireguard {
		this.server = proxy?.serverIp ?: this.server
		this.type = super.type
		this.tag = type?.lowercase()
		/* vpn:// is the AmneziaVPN import scheme, the same one 3x-ui emits in subscriptions. */
		this.url = clientConfig
			?.takeIf { it.isNotBlank() }
			?.let { "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
			?: ""
		return this
	}

}
