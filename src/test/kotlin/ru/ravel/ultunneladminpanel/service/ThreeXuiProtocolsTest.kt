package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import ru.ravel.ultunneladminpanel.model.Proxy
import ru.ravel.ultunneladminpanel.model.ProxyType
import ru.ravel.ultunneladminpanel.model.config.ConfigDataAmneziaWireguard
import ru.ravel.ultunneladminpanel.model.config.ConfigDataHysteria
import ru.ravel.ultunneladminpanel.model.config.ConfigTemplate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shapes below are the ones 3x-ui v3 returns for an "amneziawg" and a "hysteria" inbound.
 */
class ThreeXuiProtocolsTest {

	private val objectMapper = ObjectMapper()

	private val amneziaSettings = """
		{
		  "server": {
		    "privateKey": "serverPrivate",
		    "publicKey": "serverPublic",
		    "subnetIp": "10.8.0.1",
		    "subnetCidr": 24,
		    "mtu": 0,
		    "primaryDns": "1.1.1.1",
		    "secondaryDns": "8.8.8.8",
		    "jc": 4,
		    "jmin": 50,
		    "jmax": 1000,
		    "s1": 15,
		    "s2": 30,
		    "s3": 0,
		    "s4": 0,
		    "h1": "1234567",
		    "h2": "7654321",
		    "h3": "1357924",
		    "h4": "",
		    "i1": "<b 0xf1>",
		    "randomTrailers": true,
		    "disableCookies": false
		  },
		  "clients": [
		    {
		      "email": "alice-amneziawg",
		      "privateKey": "clientPrivate",
		      "publicKey": "clientPublic",
		      "allowedIPs": ["10.8.0.2/32"],
		      "preSharedKey": "psk",
		      "keepAlive": 25,
		      "enable": true
		    }
		  ]
		}
	""".trimIndent()

	@Test
	fun `amneziawg config repeats every obfuscation parameter of the inbound`() {
		val settings = objectMapper.readTree(amneziaSettings)
		val client = settings.path("clients").first()

		val config = AmneziaWgConfigBuilder.build(
			server = settings.path("server"),
			client = client,
			endpoint = "1.2.3.4",
			port = 51820,
			remark = "awg - alice",
		)

		assertEquals(
			"""
			[Interface]
			PrivateKey = clientPrivate
			Address = 10.8.0.2/32
			DNS = 1.1.1.1, 8.8.8.8
			MTU = 1420
			Jc = 4
			Jmin = 50
			Jmax = 1000
			S1 = 15
			S2 = 30
			H1 = 1234567
			H2 = 7654321
			H3 = 1357924
			H4 = 4
			I1 = <b 0xf1>
			RandomTrailers = on

			# awg - alice
			[Peer]
			PublicKey = serverPublic
			PresharedKey = psk
			AllowedIPs = 0.0.0.0/0, ::/0
			Endpoint = 1.2.3.4:51820
			PersistentKeepalive = 25
			""".trimIndent(),
			config,
		)
	}

	@Test
	fun `amneziawg mtu falls back to 1420 minus s4`() {
		assertEquals(1400, AmneziaWgConfigBuilder.effectiveMtu(0, 20))
		assertEquals(1280, AmneziaWgConfigBuilder.effectiveMtu(0, 500))
		assertEquals(1380, AmneziaWgConfigBuilder.effectiveMtu(1380, 20))
	}

	@Test
	fun `amneziawg config is exported as an importable vpn link`() {
		val settings = objectMapper.readTree(amneziaSettings)
		val client = settings.path("clients").first()
		val text = AmneziaWgConfigBuilder.build(settings.path("server"), client, "1.2.3.4", 51820, "awg")

		val configData = ConfigDataAmneziaWireguard(
			server = "1.2.3.4",
			serverPort = 51820,
			localAddress = AmneziaWgConfigBuilder.allowedIps(client),
			privateKey = "clientPrivate",
			peerPublicKey = "serverPublic",
			preSharedKey = "psk",
			jc = 4,
			jmin = 50,
			jmax = 1000,
			clientConfig = text,
		).apply {
			proxy = Proxy(serverIp = "1.2.3.4", type = ProxyType.AMNEZIA_WG)
		}

		configData.fillFields()

		val link = configData.url.orEmpty()
		assertTrue(link.startsWith("vpn://"), "unexpected link: $link")
		assertEquals(text, String(Base64.getUrlDecoder().decode(link.removePrefix("vpn://"))))

		/*
		 * WireGuard (plain or AmneziaWG) is a sing-box *endpoint* since 1.11.0, not an outbound
		 * (removed in 1.13.0): "type" is the plain "wireguard" every endpoint uses, the tunnel
		 * address is "address" (not "local_address"), and the peer moves into a "peers" array
		 * instead of top-level "peer_public_key"/"server"/"server_port".
		 */
		val outbound = objectMapper.readTree(ConfigTemplate.serializeConfig(configData, ignoreUrl = true, platform = "desktop"))
		assertEquals("wireguard", outbound.path("type").asText())
		assertEquals("proxy", outbound.path("tag").asText())
		assertEquals("clientPrivate", outbound.path("private_key").asText())
		assertEquals("10.8.0.2/32", outbound.path("address").first().asText())
		assertTrue(outbound.path("peer_public_key").isMissingNode, "the legacy top-level peer fields must not reach the client config")
		assertTrue(outbound.path("local_address").isMissingNode, "the legacy outbound field name must not reach the client config")
		assertEquals(4, outbound.path("jc").asInt())
		assertEquals(50, outbound.path("jmin").asInt())
		assertEquals(1000, outbound.path("jmax").asInt())

		val peer = outbound.path("peers").first()
		assertEquals("1.2.3.4", peer.path("address").asText())
		assertEquals(51820, peer.path("port").asInt())
		assertEquals("serverPublic", peer.path("public_key").asText())
		assertEquals("psk", peer.path("pre_shared_key").asText())
	}

	@Test
	fun `hysteria2 config carries the sni, alpn and obfuscation of the inbound`() {
		val configData = ConfigDataHysteria(
			password = "auth-token",
			server = "vpn.example.com",
			serverPort = 443,
			sni = "vpn.example.com",
			alpnList = "h3",
			insecure = true,
			obfsType = "salamander",
			obfsPassword = "obfs secret",
			portHopping = "20000-30000",
		).apply {
			proxy = Proxy(serverIp = "1.2.3.4", type = ProxyType.HYSTERIA2)
		}

		configData.fillFields()

		assertEquals(
			"hysteria2://auth-token@1.2.3.4:443?insecure=1&sni=vpn.example.com&alpn=h3" +
				"&obfs=salamander&obfs-password=obfs+secret&mport=20000-30000",
			configData.url,
		)

		val outbound = objectMapper.readTree(ConfigTemplate.serializeConfig(configData, ignoreUrl = true, platform = "desktop"))
		assertEquals("hysteria2", outbound.path("type").asText())
		assertEquals("auth-token", outbound.path("password").asText())
		assertEquals(443, outbound.path("server_port").asInt())
		assertEquals("vpn.example.com", outbound.path("tls").path("server_name").asText())
		assertEquals("h3", outbound.path("tls").path("alpn").first().asText())
		assertEquals("salamander", outbound.path("obfs").path("type").asText())
		assertEquals("obfs secret", outbound.path("obfs").path("password").asText())
	}

	@Test
	fun `hysteria2 config without obfuscation leaves the obfs block out`() {
		val configData = ConfigDataHysteria(
			password = "auth-token",
			server = "1.2.3.4",
			serverPort = 443,
		).apply {
			proxy = Proxy(serverIp = "1.2.3.4", type = ProxyType.HYSTERIA2)
		}
		configData.fillFields()

		val outbound = objectMapper.readTree(ConfigTemplate.serializeConfig(configData, ignoreUrl = true, platform = "desktop"))
		assertTrue(outbound.path("obfs").isMissingNode, "obfs must be absent when the inbound has none")
		assertEquals("hysteria2://auth-token@1.2.3.4:443?insecure=1&alpn=h3", configData.url)
	}

}
