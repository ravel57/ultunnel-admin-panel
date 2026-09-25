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
	SSH("ssh"),
	WIREGUARD("wireguard"),
	AMNEZIA_WG("amnezia_wireguard"),
	NAIVE("naive"),
	;
}
