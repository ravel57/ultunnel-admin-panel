package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.JsonNode

/**
 * Builds the AmneziaWG client .conf out of one "amneziawg" inbound of the 3x-ui panel.
 *
 * Field order and the optional-field rules mirror the panel's own exporter
 * (internal/sub/service.go → amneziaWGConfigText): an AmneziaWG handshake only succeeds when
 * the client repeats the server's obfuscation parameters exactly, so every one of them is
 * copied over, including the ones the panel leaves at their defaults.
 */
object AmneziaWgConfigBuilder {

	/* Protocol defaults of the magic headers: a native client needs all four to be present. */
	private val headerDefaults = listOf("1", "2", "3", "4")

	private const val DEFAULT_MTU = 1420
	private const val MAX_MTU = 65535
	private const val MIN_MTU = 1280

	/**
	 * [server] is settings.server of the inbound, [client] its entry in settings.clients,
	 * [endpoint]/[port] the address the client dials, [remark] the comment above the peer.
	 */
	fun build(
		server: JsonNode,
		client: JsonNode,
		endpoint: String,
		port: Long,
		remark: String,
	): String {
		val privateKey = client.path("privateKey").asText().takeIf { it.isNotBlank() }
			?: error("3x-ui AmneziaWG client has no private key")
		val peerPublicKey = server.path("publicKey").asText().takeIf { it.isNotBlank() }
			?: error("3x-ui AmneziaWG inbound has no server public key")
		val address = allowedIps(client)
		if (address.isEmpty()) {
			error("3x-ui AmneziaWG client has no tunnel address")
		}

		val lines = mutableListOf(
			"[Interface]",
			"PrivateKey = $privateKey",
			"Address = ${address.joinToString(", ")}",
		)

		val dns = dnsServers(server)
		if (dns.isNotEmpty()) {
			lines += "DNS = ${dns.joinToString(", ")}"
		}
		/* Always emitted: without it the client keeps its own 1420 and fragments once S4 grows. */
		lines += "MTU = ${effectiveMtu(server.path("mtu").asInt(0), server.path("s4").asInt(0))}"

		lines += "Jc = ${server.path("jc").asInt(0)}"
		lines += "Jmin = ${server.path("jmin").asInt(0)}"
		lines += "Jmax = ${server.path("jmax").asInt(0)}"
		lines += "S1 = ${server.path("s1").asInt(0)}"
		lines += "S2 = ${server.path("s2").asInt(0)}"
		server.path("s3").asInt(0).takeIf { it > 0 }?.let { lines += "S3 = $it" }
		server.path("s4").asInt(0).takeIf { it > 0 }?.let { lines += "S4 = $it" }

		headerDefaults.forEachIndexed { index, default ->
			val key = "h${index + 1}"
			val value = server.path(key).asText("").trim().takeIf { it.isNotEmpty() } ?: default
			lines += "H${index + 1} = $value"
		}

		(1..5).forEach { index ->
			server.path("i$index").asText("").takeIf { it.isNotEmpty() }?.let { lines += "I$index = $it" }
		}

		listOf(
			"HeaderProtectionKey" to "headerProtectionKey",
			"ContentPaddingAddition" to "contentPaddingAddition",
			"RekeyAfterTime" to "rekeyAfterTime",
			"RekeyTimeout" to "rekeyTimeout",
			"RejectAfterTime" to "rejectAfterTime",
			"KeepaliveTimeout" to "keepaliveTimeout",
			"MaxHandshakeAttempts" to "maxHandshakeAttempts",
		).forEach { (key, field) ->
			server.path(field).asText("").takeIf { it.isNotEmpty() }?.let { lines += "$key = $it" }
		}

		if (server.path("randomTrailers").asBoolean(false)) {
			lines += "RandomTrailers = on"
		}
		if (server.path("disableCookies").asBoolean(false)) {
			lines += "DisableCookies = on"
		}

		lines += ""
		if (remark.isNotBlank()) {
			lines += "# $remark"
		}
		lines += "[Peer]"
		lines += "PublicKey = $peerPublicKey"
		client.path("preSharedKey").asText("").takeIf { it.isNotEmpty() }
			?.let { lines += "PresharedKey = $it" }
		lines += "AllowedIPs = 0.0.0.0/0, ::/0"
		lines += "Endpoint = ${endpoint}:${port}"
		keepAlive(client)?.let { lines += "PersistentKeepalive = $it" }

		return lines.joinToString("\n")
	}

	fun allowedIps(client: JsonNode): List<String> {
		val node = client.path("allowedIPs")
		if (!node.isArray) return emptyList()
		return node.mapNotNull { it.asText().trim().takeIf { value -> value.isNotEmpty() } }
	}

	fun dnsServers(server: JsonNode): List<String> {
		return listOf("primaryDns", "secondaryDns")
			.mapNotNull { server.path(it).asText("").trim().takeIf { value -> value.isNotEmpty() } }
	}

	/* The admin's value when set, otherwise 1420 minus the S4 junk, never below 1280. */
	fun effectiveMtu(configuredMtu: Int, s4: Int): Int {
		if (configuredMtu > 0) {
			return minOf(configuredMtu, MAX_MTU)
		}
		return maxOf(DEFAULT_MTU - maxOf(s4, 0), MIN_MTU)
	}

	/* The panel stores it either as a number or as an AmneziaWG "min-max" range; 0 means off. */
	private fun keepAlive(client: JsonNode): String? {
		val raw = client.path("keepAlive").asText("").trim()
		if (raw.isEmpty() || raw == "0") return null
		return raw
	}

}
