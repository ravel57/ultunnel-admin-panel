package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * One peer of a sing-box WireGuard *endpoint* ("peers": [...]). WireGuard stopped being an
 * outbound in sing-box 1.11.0 (removed in 1.13.0): the server/port/key that used to sit at the
 * top level of the outbound now belong to a peer entry instead.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WireGuardPeerJson(
	val address: String? = null,
	val port: Int? = null,
	@JsonProperty("public_key") val publicKey: String? = null,
	@JsonProperty("pre_shared_key") val preSharedKey: String? = null,
	@JsonProperty("allowed_ips") val allowedIps: List<String>? = null,
	@JsonProperty("persistent_keepalive_interval") val persistentKeepaliveInterval: Int? = null,
	val reserved: List<Int>? = null,
)

/**
 * Builds the sing-box peer list for a WireGuard endpoint from whichever of the two shapes
 * [ConfigDataWireguard]/[ConfigDataAmneziaWireguard] actually hold the peer: a real
 * [ConfigDataWireguardPeer] relation (e.g. parsed from a wg-easy .conf, which can list several
 * peers), or, when that is empty, the legacy flat single-peer fields every provisioning path
 * before it used (server/serverPort/peerPublicKey/preSharedKey/reserved).
 */
fun buildWireGuardPeers(
	relationPeers: List<ConfigDataWireguardPeer>?,
	legacyServer: String?,
	legacyServerPort: Long?,
	legacyPeerPublicKey: String?,
	legacyPreSharedKey: String?,
	legacyReserved: List<Int>?,
): List<WireGuardPeerJson>? {
	val fromRelation = relationPeers
		?.filter { !it.server.isNullOrBlank() || !it.publicKey.isNullOrBlank() }
		?.map {
			WireGuardPeerJson(
				address = it.server,
				port = it.serverPort,
				publicKey = it.publicKey,
				preSharedKey = it.preSharedKey,
				allowedIps = it.allowedIps?.takeIf { ips -> ips.isNotEmpty() } ?: DEFAULT_ALLOWED_IPS,
				reserved = it.reserved,
			)
		}
	if (!fromRelation.isNullOrEmpty()) {
		return fromRelation
	}
	if (legacyServer.isNullOrBlank() && legacyPeerPublicKey.isNullOrBlank()) {
		return null
	}
	return listOf(
		WireGuardPeerJson(
			address = legacyServer,
			port = legacyServerPort?.toInt(),
			publicKey = legacyPeerPublicKey,
			preSharedKey = legacyPreSharedKey,
			allowedIps = DEFAULT_ALLOWED_IPS,
			reserved = legacyReserved,
		)
	)
}

private val DEFAULT_ALLOWED_IPS = listOf("0.0.0.0/0", "::/0")
