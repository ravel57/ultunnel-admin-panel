package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import ru.ravel.ultunneladminpanel.model.config.ConfigDataNativeProxy
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object NativeProxyConfigBuilder {
    private fun encode(value: String) = URLEncoder.encode(value, UTF_8).replace("+", "%20")
    private fun text(node: JsonNode, key: String) = node.path(key).asText("").takeIf { it.isNotBlank() }

    fun build(protocol: String, host: String, port: Int, email: String, password: String,
              uuid: String?, settings: JsonNode, stream: JsonNode, mapper: ObjectMapper): ConfigDataNativeProxy {
        val server = settings.path("server").takeIf { it.isObject } ?: settings
        val tlsSettings = stream.path("tlsSettings")
        var sni = text(server, "server_name") ?: text(server, "sni")
            ?: text(tlsSettings, "serverName") ?: host
        if (protocol == "shadowtls") {
            sni = text(settings, "server_name") ?: text(settings, "handshake_server")?.let {
                URI("tls://$it").host
            } ?: error("ShadowTLS handshake server is missing")
        }
        val tls = linkedMapOf<String, Any?>("enabled" to true, "server_name" to sni)
        if (protocol != "vless") require(password.isNotBlank()) { "Panel returned no password" }
        val primary = linkedMapOf<String, Any?>("type" to protocol, "tag" to "proxy",
            "server" to host, "server_port" to port, "password" to password, "tls" to tls)
        val auxiliary = mutableListOf<Map<String, Any?>>()
        val address = "${if (host.contains(':')) "[$host]" else host}:$port"
        val sniQuery = "sni=${encode(sni)}"
        val url = when (protocol) {
            "vless" -> {
                require(!uuid.isNullOrBlank()) { "Panel returned no VLESS UUID" }
                primary.remove("password"); primary["uuid"] = uuid
                val network = text(stream, "network") ?: "tcp"
                if (network == "xhttp") {
                    val transport = stream.path("xhttpSettings")
                    primary["transport"] = mapOf("type" to "xhttp", "path" to (text(transport, "path") ?: "/"),
                        "mode" to (text(transport, "mode") ?: "auto"), "host" to sni)
                } else require(network == "tcp" || network == "raw") { "Unsupported VLESS transport $network" }
                val path = text(stream.path("xhttpSettings"), "path") ?: "/"
                "vless://${encode(uuid)}@$address?security=tls&$sniQuery&type=${encode(network)}&path=${encode(path)}"
            }
            "trojan" -> "trojan://${encode(password)}@$address?security=tls&type=tcp&$sniQuery"
            "naive" -> {
                primary["username"] = email
                primary["quic"] = settings.path("http3").asBoolean(false)
                primary["udp_over_tcp"] = mapOf("enabled" to true, "version" to 2)
                "naive+https://${encode(email)}:${encode(password)}@$address?$sniQuery" +
                    if (settings.path("http3").asBoolean(false)) "&quic=1" else ""
            }
            "hysteria2" -> {
                tls["alpn"] = listOf("h3")
                val mask = stream.path("finalmask").path("udp").firstOrNull { it.path("type").asText() == "salamander" }?.path("settings")
                val obfsPassword = mask?.let { text(it, "password") }
                var obfsQuery = ""
                if (obfsPassword != null) {
                    val size = text(mask, "packetSize")
                    val range = size?.split("-")?.map { it.trim().toInt() }
                    val obfs = linkedMapOf<String, Any?>("type" to if (range == null) "salamander" else "gecko", "password" to obfsPassword)
                    if (range != null) {
                        require(range.size in 1..2 && range.first() > 0 && range.last() >= range.first()) { "Invalid Gecko range" }
                        obfs["min_packet_size"] = range.first(); obfs["max_packet_size"] = range.last()
                        obfsQuery += "&minPacketSize=${range.first()}&maxPacketSize=${range.last()}"
                    }
                    primary["obfs"] = obfs
                    obfsQuery += "&obfs=${obfs["type"]}&obfs-password=${encode(obfsPassword)}"
                }
                "hysteria2://${encode(password)}@$address?$sniQuery$obfsQuery"
            }
            "tuic" -> {
                require(!uuid.isNullOrBlank()) { "3x-ui returned no TUIC UUID" }
                primary["uuid"] = uuid
                val congestion = text(server, "congestion_control") ?: "cubic"
                val relay = text(server, "udp_relay_mode") ?: "native"
                primary["congestion_control"] = congestion
                primary["udp_relay_mode"] = relay
                tls["alpn"] = server.path("alpn").takeIf { it.isArray && !it.isEmpty }
                    ?.map { it.asText() } ?: listOf("h3")
                "tuic://${encode(uuid)}:${encode(password)}@$address?$sniQuery&congestion_control=${encode(congestion)}&udp_relay_mode=${encode(relay)}&alpn=${encode((tls["alpn"] as List<*>).joinToString(","))}"
            }
            "anytls" -> "anytls://${encode(password)}@$address?$sniQuery&insecure=0"
            "masque" -> {
                primary["username"] = email
                primary["path"] = text(stream.path("masqueSettings"), "path") ?: "/.well-known/masque/ip/*/*/"
                val http2 = tlsSettings.path("alpn").any { it.asText() == "h2" } &&
                    !tlsSettings.path("alpn").any { it.asText() == "h3" }
                primary["http2"] = http2
                tls["alpn"] = listOf(if (http2) "h2" else "h3")
                // MASQUE has no standard share URI; the sing-box JSON subscription carries it.
                ""
            }
            "trusttunnel" -> {
                primary["username"] = email
                val quic = settings.path("http3").asBoolean(false)
                primary["quic"] = quic
                trustTunnelLink(sni, address, email, password, quic)
            }
            "shadowtls" -> {
                val innerPassword = text(settings, "shadowsocks_password")
                    ?: error("ShadowTLS Shadowsocks password is missing")
                auxiliary += primary.toMap() + mapOf("tag" to "proxy-shadowtls", "version" to 3)
                primary.clear()
                primary.putAll(mapOf("type" to "shadowsocks", "tag" to "proxy",
                    "server" to host, "server_port" to port,
                    "method" to "chacha20-ietf-poly1305", "password" to innerPassword,
                    "detour" to "proxy-shadowtls", "udp_over_tcp" to mapOf("enabled" to true, "version" to 2)))
                val credential = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("chacha20-ietf-poly1305:$innerPassword".toByteArray(UTF_8))
                fun escape(value: String) = value.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=")
                val plugin = "shadow-tls;version=3;host=${escape(sni)};password=${escape(password)}"
                "ss://$credential@$address?plugin=${encode(plugin)}&uot=1"
            }
            else -> error("Unsupported native protocol $protocol")
        }
        return ConfigDataNativeProxy(mapper.writeValueAsString(primary), mapper.writeValueAsString(auxiliary)).apply {
            this.type = protocol
            this.server = host
            this.url = url
        }
    }

    fun wireguard(host: String, port: Int, settings: JsonNode, client: JsonNode, mapper: ObjectMapper): ConfigDataNativeProxy {
        val secret = text(settings, "secretKey") ?: error("WireGuard server key is missing")
        val raw = if (secret.length == 64 && secret.all { it in "0123456789abcdefABCDEF" })
            secret.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        else Base64.getDecoder().decode(secret)
        val privateKey = java.security.KeyFactory.getInstance("XDH").generatePrivate(
            java.security.spec.XECPrivateKeySpec(java.security.spec.NamedParameterSpec.X25519, raw))
        val basepoint = java.security.KeyFactory.getInstance("XDH").generatePublic(
            java.security.spec.XECPublicKeySpec(java.security.spec.NamedParameterSpec.X25519, java.math.BigInteger.valueOf(9)))
        val agreement = javax.crypto.KeyAgreement.getInstance("X25519")
        agreement.init(privateKey); agreement.doPhase(basepoint, true)
        val publicKey = Base64.getEncoder().encodeToString(agreement.generateSecret())
        val addresses = AmneziaWgConfigBuilder.allowedIps(client)
        require(addresses.isNotEmpty()) { "WireGuard client address is missing" }
        val outbound = mapOf("type" to "wireguard", "tag" to "proxy", "address" to addresses,
            "private_key" to (text(client, "privateKey") ?: error("Client private key is missing")),
            "mtu" to settings.path("mtu").asInt(1420),
            "peers" to listOf(mapOf("address" to host, "port" to port, "public_key" to publicKey,
                "pre_shared_key" to (text(client, "preSharedKey") ?: ""),
                "allowed_ips" to listOf("0.0.0.0/0", "::/0"))))
        return ConfigDataNativeProxy(outboundJson = mapper.writeValueAsString(outbound), endpoint = true).apply {
            type = "wireguard"; server = host; url = ""
        }
    }

    private fun trustTunnelLink(sni: String, address: String, user: String, password: String, quic: Boolean): String {
        val output = ByteArrayOutputStream()
        fun varint(value: Int) {
            require(value in 0..16383) { "TrustTunnel field is too long" }
            if (value < 64) output.write(value) else {
                output.write((value shr 8) or 0x40)
                output.write(value and 255)
            }
        }
        fun field(tag: Int, bytes: ByteArray) {
            varint(tag); varint(bytes.size); output.write(bytes)
        }
        field(0, byteArrayOf(2))
        field(1, sni.toByteArray(UTF_8)); field(2, address.toByteArray(UTF_8))
        field(5, user.toByteArray(UTF_8)); field(6, password.toByteArray(UTF_8))
        field(9, byteArrayOf((if (quic) 2 else 1).toByte()))
        return "tt://?" + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }
}
