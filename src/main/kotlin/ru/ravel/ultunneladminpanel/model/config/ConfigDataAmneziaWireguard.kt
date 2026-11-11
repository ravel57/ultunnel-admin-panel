package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*
import ru.ravel.ultunneladminpanel.service.SerializationContext
import java.util.Base64

/**
 * AmneziaWG config of one user, provisioned through the 3x-ui panel ("amneziawg" inbound).
 *
 * sing-box has no separate AmneziaWG outbound or endpoint type: setting any of the obfuscation
 * fields below on an ordinary WireGuard *endpoint* (jc/jmin/jmax/s1-s4/h1-h4/...) is what turns
 * it into AmneziaWG 3.1 (`option.WireGuardAmneziaOptions`), the same way the classic outbound
 * was removed from sing-box in 1.13.0 in favor of the endpoint (see [ConfigDataWireguard]).
 * A client that drops any of these cannot complete the handshake, so they are copied from the
 * 3x-ui inbound's settings verbatim — field names match amneziawg-go's UAPI keys, same as the
 * panel's own [AmneziaWgConfigBuilder] exporter. [clientConfig] keeps that same text so [url]
 * (the `vpn://` link the AmneziaVPN app imports) can still be built from it.
 */
@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
class ConfigDataAmneziaWireguard(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	/*
	 * "amnezia_wg" is the panel's own bookkeeping value — it is what
	 * ProxyType.AMNEZIA_WG.configType is, and what UserService/ProxyServerService match
	 * against to tell "does this user already have AmneziaWG" and "delete every AmneziaWG
	 * config" from a plain WireGuard one. It also has to be the enum name uppercased, since
	 * ultunnel-front's ProxyCard.vue falls back to comparing it, case-insensitively, against
	 * the protocol's own "AMNEZIA_WG" when it has no nested proxy id to match by instead.
	 * It never reaches sing-box: [typeJson] below is the actual outbound/endpoint "type"
	 * sing-box sees, always the literal "wireguard".
	 */
	@JsonIgnore
	override var type: String? = "amnezia_wg",

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

	/* AmneziaWG 3.1 obfuscation (option.WireGuardAmneziaOptions); 3x-ui inbound's settings.server. */
	var jc: Int? = null,

	var jmin: Int? = null,

	var jmax: Int? = null,

	var s1: Int? = null,

	var s2: Int? = null,

	var s3: Int? = null,

	var s4: Int? = null,

	var h1: String? = null,

	var h2: String? = null,

	var h3: String? = null,

	var h4: String? = null,

	var i1: String? = null,

	var i2: String? = null,

	var i3: String? = null,

	var i4: String? = null,

	var i5: String? = null,

	@JsonProperty("header_protection_key")
	var headerProtectionKey: String? = null,

	@JsonProperty("content_padding_addition")
	var contentPaddingAddition: String? = null,

	@JsonProperty("rekey_after_time")
	var rekeyAfterTime: String? = null,

	@JsonProperty("rekey_timeout")
	var rekeyTimeout: String? = null,

	@JsonProperty("reject_after_time")
	var rejectAfterTime: String? = null,

	@JsonProperty("keepalive_timeout")
	var keepaliveTimeout: String? = null,

	@JsonProperty("max_handshake_attempts")
	var maxHandshakeAttempts: String? = null,

	@JsonProperty("random_trailers")
	var randomTrailers: Boolean? = null,

	@JsonProperty("disable_cookies")
	var disableCookies: Boolean? = null,

	/* DNS servers of the inbound, comma separated. */
	@JsonIgnore
	var dns: String? = null,

	/* The full AmneziaWG client .conf, exactly as the panel's own export produces it. */
	@JsonIgnore
	@Column(columnDefinition = "text")
	var clientConfig: String? = null,

	) : ConfigData(id = id, type = type, server = server) {

    // Hibernate persists the inherited field; avoid a second, unmapped backing field.
    @get:JsonIgnore
    override var server: String?
        get() = super.server
        set(value) { super.server = value }

	@get:JsonProperty("type")
	val typeJson: String
		get() = if (SerializationContext.isConfigCall()) "wireguard" else "amnezia_wg"

	// Admin cards need identity metadata; sing-box endpoints must not contain it.
	@get:JsonProperty("proxy")
	val proxyJson: Map<String, Any?>?
		get() = if (SerializationContext.isConfigCall()) null
		else proxy?.let { mapOf("id" to it.id, "type" to it.type) }

	@get:JsonProperty("server")
	val serverJson: String?
		get() = if (SerializationContext.isConfigCall()) null else server ?: proxy?.serverIp

	@get:JsonProperty("peers")
	val peersJson: List<WireGuardPeerJson>?
		get() = buildWireGuardPeers(peers, server, serverPort, peerPublicKey, preSharedKey, reserved)

	override fun fillFields(): ConfigDataAmneziaWireguard {
		this.server = this.server ?: proxy?.serverIp
		this.tag = "proxy"
		/* A native client needs all four magic headers present; same defaults as the exporter. */
		this.h1 = h1?.takeIf { it.isNotBlank() } ?: "1"
		this.h2 = h2?.takeIf { it.isNotBlank() } ?: "2"
		this.h3 = h3?.takeIf { it.isNotBlank() } ?: "3"
		this.h4 = h4?.takeIf { it.isNotBlank() } ?: "4"
		/* vpn:// is the AmneziaVPN import scheme, the same one 3x-ui emits in subscriptions. */
		this.url = clientConfig
			?.takeIf { it.isNotBlank() }
			?.let { "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
			?: ""
		return this
	}

}
