package ru.ravel.ultunneladminpanel.model

/**
 * [configType] is the value stored in config_data.type for configs of this protocol.
 * Config cleanup must be restricted by it: legacy rows of one protocol may point at
 * the Proxy row of another one, so proxy_id alone selects the wrong configs.
 */
enum class ProxyType(val configType: String) {
	VLESS("vless"),
	TROJAN("trojan"),
	TROJAN2("trojan"),
	HYSTERIA2("hysteria2"),
	TUIC("tuic"),
	MASQUE("masque"),
	ANYTLS("anytls"),
	SHADOWTLS("shadowtls"),
	TRUSTTUNNEL("trusttunnel"),
	SSH("ssh"),
	WIREGUARD("wireguard"),
	// Must uppercase to the enum name itself: the frontend falls back to comparing
	// config.type against this enum's name when it has no nested proxy to match by id
	// (ProxyCard.vue's matchesProtocol/normalizeType uppercase-compares both sides).
	AMNEZIA_WG("amnezia_wg"),
	NAIVE("naive"),
	;
}
