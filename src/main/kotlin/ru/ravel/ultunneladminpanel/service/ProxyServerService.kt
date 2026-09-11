package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.transaction.Transactional
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.megoru.impl.WgEasyAPI
import org.megoru.io.UnsuccessfulHttpException
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.component.createUnsafeOkHttpClient
import ru.ravel.ultunneladminpanel.model.Proxy
import ru.ravel.ultunneladminpanel.model.ProxyServer
import ru.ravel.ultunneladminpanel.model.ProxyType.*
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.config.*
import ru.ravel.ultunneladminpanel.model.sui.SuiInbound
import ru.ravel.ultunneladminpanel.model.sui.SuiInboundsResponse
import ru.ravel.ultunneladminpanel.model.xui.Root
import ru.ravel.ultunneladminpanel.model.xui.StreamSettings
import ru.ravel.ultunneladminpanel.model.xui.ThreeXuiType
import ru.ravel.ultunneladminpanel.repository.ProxyRepository
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import ru.ravel.ultunneladminpanel.repository.UserRepository
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*


@Service
class ProxyServerService(
	private val sshService: SshService,
	private val proxyServerRepository: ProxyServerRepository,
	private val proxyRepository: ProxyRepository,
	private val userRepository: UserRepository,
	private val hysteriaService: HysteriaService,
) {

	private data class ThreeXuiV3Session(
		val cookie: String,
		val csrfToken: String,
	)

	private fun mergeCookies(cookies: MutableMap<String, String>, setCookieHeaders: List<String>) {
		setCookieHeaders.forEach { header ->
			val cookie = header.substringBefore(';').trim()
			val separator = cookie.indexOf('=')
			if (separator > 0) {
				cookies[cookie.substring(0, separator)] = cookie.substring(separator + 1)
			}
		}
	}

	private fun cookieHeader(cookies: Map<String, String>): String {
		return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
	}

	private fun openThreeXuiV3Session(
		client: OkHttpClient,
		baseUrl: String,
		proxy: Proxy,
		objectMapper: ObjectMapper,
	): ThreeXuiV3Session {
		val cookies = linkedMapOf<String, String>()

		val csrfRequest = Request.Builder()
			.url("$baseUrl/csrf-token")
			.get()
			.build()

		val csrfToken = client.newCall(csrfRequest).execute().use { response ->
			val responseBody = response.body?.string().orEmpty()
			check(response.isSuccessful) {
				"3x-ui CSRF request failed: HTTP ${response.code}: $responseBody"
			}
			check(responseBody.isNotBlank()) { "3x-ui returned an empty CSRF response" }
			mergeCookies(cookies, response.headers.values("Set-Cookie"))

			val root = objectMapper.readTree(responseBody)
			check(root.path("success").asBoolean(false)) {
				root.path("msg").asText("3x-ui failed to create a CSRF session")
			}
			root.path("obj").asText().also {
				check(it.isNotBlank()) { "3x-ui returned an empty CSRF token" }
			}
		}

		val loginJson = objectMapper.createObjectNode()
			.put("username", proxy.login)
			.put("password", proxy.password)
		val loginBody = objectMapper.writeValueAsBytes(loginJson)
			.toRequestBody("application/json".toMediaType())
		val loginRequest = Request.Builder()
			.header("Content-Type", "application/json")
			.header("Cookie", cookieHeader(cookies))
			.header("X-CSRF-Token", csrfToken)
			.url("$baseUrl/login")
			.post(loginBody)
			.build()

		client.newCall(loginRequest).execute().use { response ->
			val responseBody = response.body?.string().orEmpty()
			check(response.isSuccessful) {
				"3x-ui login failed: HTTP ${response.code}: $responseBody"
			}
			check(responseBody.isNotBlank()) { "3x-ui returned an empty login response" }
			mergeCookies(cookies, response.headers.values("Set-Cookie"))

			val root = objectMapper.readTree(responseBody)
			check(root.path("success").asBoolean(false)) {
				root.path("msg").asText("3x-ui rejected the credentials")
			}
		}

		return ThreeXuiV3Session(
			cookie = cookieHeader(cookies),
			csrfToken = csrfToken,
		)
	}

	private fun decodeJsonNode(objectMapper: ObjectMapper, node: JsonNode): JsonNode {
		if (!node.isTextual) return node
		val raw = node.asText().trim()
		if (raw.isBlank()) return node
		return runCatching { objectMapper.readTree(raw) }.getOrDefault(node)
	}

	private fun findText(node: JsonNode?, key: String): String? {
		if (node == null || node.isNull || node.isMissingNode) return null

		if (node.isObject) {
			val direct = node.get(key)
			if (direct?.isTextual == true && direct.asText().isNotBlank()) {
				return direct.asText()
			}

			val fields = node.fields()
			while (fields.hasNext()) {
				val found = findText(fields.next().value, key)
				if (found != null) return found
			}
		}

		if (node.isArray) {
			for (child in node) {
				val found = findText(child, key)
				if (found != null) return found
			}
		}

		return null
	}

	fun getAllServers(): MutableList<ProxyServer> {
		return proxyServerRepository.findAll()
	}

	fun addNewServer(proxyServer: ProxyServer): ProxyServer {
		proxyServer.proxies?.forEach {
			proxyRepository.save(it)
		}
		proxyServerRepository.save(proxyServer)
		return proxyServer
	}


	fun editServer(proxyServer: ProxyServer): ProxyServer {
		return proxyServerRepository.save(proxyServer)
	}


	fun addProxyToServer(proxyServerId: Long, proxy: Proxy): Proxy {
		val proxyServer = proxyServerRepository.findById(proxyServerId).orElseThrow().also {
			proxyRepository.save(proxy)
		}
		proxyServer?.proxies?.add(proxy)
		proxyServer?.let { proxyServerRepository.save(it) }
		return proxy
	}


	@Transactional
	fun createUserProxy(host: String, proxy: Proxy, user: User): ConfigData {
		val objectMapper = ObjectMapper()
		when (proxy.type!!) {
			VLESS -> {
				var json = "{\"username\":\"${proxy.login}\",\"password\":\"${proxy.password}\"}"
				var body = json.toRequestBody("application/json".toMediaType())
				val url = if (proxy.useSubDomain!!) {
					"https://${proxy.subdomain}.${host}"
				} else {
					"https://${host}:${proxy.port}"
				}
				var request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/login")
					.post(body)
					.build()
				var response = createUnsafeOkHttpClient().newCall(request).execute()
				val cooke = response.headers["Set-Cookie"]
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/panel/api/inbounds/list")
					.header("Cookie", cooke.toString())
					.get()
					.build()
				response = createUnsafeOkHttpClient().newCall(request).execute()
				val string = response.body?.string()
				val readValue = objectMapper.readValue(string, Root::class.java)
				val protocol = ThreeXuiType.VLESS.name.lowercase()
				val inbound = readValue.obj?.last { it.protocol == protocol }
				val port = inbound?.port
				val id = inbound?.id
				val stream = objectMapper.readValue(inbound?.streamSettings, StreamSettings::class.java)
//				val ech = stream.tlsSettings?.settings?.echConfigList ?: ""
				val sni = stream.tlsSettings?.serverName
				val uuid = UUID.randomUUID().toString()
				json = """{
					"id": ${id},
					"settings": "{\"clients\":[{\"id\":\"${uuid}\",\"alterId\":0,\"email\":\"${user.name}-vless\",\"limitIp\":0,\"totalGB\":0,\"expiryTime\":0,\"enable\":true,\"tgId\":\"\",\"subId\":\"\"}]}"
				}"""
				body = json.toRequestBody("application/json".toMediaType())
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/panel/api/inbounds/addClient")
					.header("Cookie", cooke.toString())
					.post(body)
					.build()
				createUnsafeOkHttpClient().newCall(request).execute()
				val server = if (proxy.useSubDomain!!) {
					"${proxy.subdomain}.${host}"
				} else {
					host
				}
				return ConfigDataVless(
					type = protocol,
					uuid = uuid,
					server = server,
					serverPort = port!!,
					tls = TlsSettings(
						enabled = true,
						serverName = sni ?: host,
						alpn = stream.tlsSettings?.alpn ?: listOf("h2"),
						utls = UtlsSettings(
							enabled = true,
							fingerprint = "chrome"
						),
					),
					transport = TransportSettings(
						type = "grpc",
						serviceName = stream.grpcSettings?.serviceName ?: "GunService",
						idleTimeout = "15s",
						pingTimeout = "15s",
					),
				).apply {
					this.proxy = proxy
				}
			}

			TROJAN -> {
				var loginJson = "{\"username\":\"${proxy.login}\",\"password\":\"${proxy.password}\"}"
				var body = loginJson.toRequestBody("application/json".toMediaType())
				val url = if (proxy.useSubDomain!!) {
					"https://${proxy.subdomain}.${host}"
				} else {
					"https://${host}:${proxy.port}"
				}
				var request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/login")
					.post(body)
					.build()
				var response = createUnsafeOkHttpClient().newCall(request).execute()
				val cookie = response.headers["Set-Cookie"]
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/panel/api/inbounds/list")
					.header("Cookie", cookie.toString())
					.get()
					.build()
				response = createUnsafeOkHttpClient().newCall(request).execute()
				val inboundList = objectMapper.readValue(response.body?.string(), Root::class.java)
				val protocol = ThreeXuiType.TROJAN.name.lowercase()
				val port = inboundList.obj?.last { it.protocol == protocol }?.port!!
				val id = inboundList.obj.last { it.protocol == protocol }.id!!

				val uuidPassword = UUID.randomUUID().toString()
				val clientJson = """{
				  "id": $id,"settings": "{\"clients\":[{\"password\":\"$uuidPassword\",\"email\":\"${user.name}-trojan\",\"limitIp\":0,\"totalGB\":0,\"expiryTime\":0,\"enable\":true,\"tgId\":\"\",\"subId\":\"${proxy.subdomain ?: ""}\",\"comment\":\"\",\"reset\":0}]}"
				}""".trimIndent()

				body = clientJson.toRequestBody("application/json".toMediaType())

				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/panel/api/inbounds/addClient")
					.header("Cookie", cookie.toString())
					.post(body)
					.build()
				createUnsafeOkHttpClient().newCall(request).execute()
				val sniHost = if (proxy.useSubDomain == true) {
					"${proxy.subdomain}.${host}"
				} else {
					host
				}
				return ConfigDataTrojan(
					trojanServer = host,
					serverPort = port,
					password = uuidPassword,
					sni = sniHost,
					fp = "chrome",
					alpn = listOf("h2"),
				).apply {
					this.proxy = proxy
				}
			}


			TROJAN2 -> {
				val baseUrl = if (proxy.useSubDomain == true) {
					"https://${proxy.subdomain}.${host}"
				} else {
					"https://${host}:${proxy.port}"
				}
				val client = createUnsafeOkHttpClient()
				val session = openThreeXuiV3Session(client, baseUrl, proxy, objectMapper)

				val listRequest = Request.Builder()
					.header("Accept", "application/json")
					.header("Cookie", session.cookie)
					.header("X-CSRF-Token", session.csrfToken)
					.url("$baseUrl/panel/api/inbounds/list")
					.get()
					.build()

				val listRoot = client.newCall(listRequest).execute().use { response ->
					val responseBody = response.body?.string().orEmpty()
					check(response.isSuccessful) {
						"3x-ui inbound list failed: HTTP ${response.code}: $responseBody"
					}
					check(responseBody.isNotBlank()) { "3x-ui returned an empty inbound list" }

					val root = objectMapper.readTree(responseBody)
					check(root.path("success").asBoolean(false)) {
						root.path("msg").asText("3x-ui failed to return inbounds")
					}
					root
				}

				val protocol = ThreeXuiType.TROJAN.name.lowercase()
				val inbound = listRoot.path("obj")
					.elements()
					.asSequence()
					.filter { it.path("protocol").asText().equals(protocol, ignoreCase = true) }
					.lastOrNull()
					?: error("No Trojan inbound found in 3x-ui")

				val inboundId = inbound.path("id").asInt(-1)
				val inboundPort = inbound.path("port").asLong(-1)
				check(inboundId > 0) { "3x-ui returned an invalid Trojan inbound id" }
				check(inboundPort in 1L..65535L) { "3x-ui returned an invalid Trojan inbound port" }

				val email = "${user.name}-trojan2"
				val clientsRequest = Request.Builder()
					.header("Accept", "application/json")
					.header("Cookie", session.cookie)
					.header("X-CSRF-Token", session.csrfToken)
					.url("$baseUrl/panel/api/clients/list")
					.get()
					.build()
				val existingClient = client.newCall(clientsRequest).execute().use { response ->
					val responseBody = response.body?.string().orEmpty()
					check(response.isSuccessful) {
						"3x-ui client list failed: HTTP ${response.code}: $responseBody"
					}
					check(responseBody.isNotBlank()) { "3x-ui returned an empty client list" }
					val root = objectMapper.readTree(responseBody)
					check(root.path("success").asBoolean(false)) {
						root.path("msg").asText("3x-ui failed to return clients")
					}
					root.path("obj")
						.elements()
						.asSequence()
						.firstOrNull { it.path("email").asText() == email }
				}

				val password = existingClient?.path("password")?.asText()
					?.takeIf { it.isNotBlank() }
					?: UUID.randomUUID().toString().replace("-", "")
				val subId = existingClient?.path("subId")?.asText()
					?.takeIf { it.isNotBlank() }
					?: UUID.randomUUID().toString()

				val requestJson: JsonNode
				val requestUrl: String
				if (existingClient == null) {
					val clientNode = objectMapper.createObjectNode().apply {
						put("password", password)
						put("email", email)
						put("limitIp", 0)
						put("totalGB", 0L)
						put("expiryTime", 0L)
						put("enable", true)
						put("tgId", 0L)
						put("subId", subId)
						put("comment", "")
						put("reset", 0)
					}
					requestJson = objectMapper.createObjectNode().apply {
						set<ObjectNode>("client", clientNode)
						set<JsonNode>("inboundIds", objectMapper.createArrayNode().add(inboundId))
					}
					requestUrl = "$baseUrl/panel/api/clients/add"
				} else {
					// The external client may already exist when the first local DB transaction failed.
					// Attach is idempotent in 3x-ui and also covers a client that exists globally
					// but is not yet attached to this inbound.
					requestJson = objectMapper.createObjectNode().apply {
						set<JsonNode>("inboundIds", objectMapper.createArrayNode().add(inboundId))
					}
					requestUrl = "$baseUrl/panel/api/clients/${URLEncoder.encode(email, UTF_8.name())}/attach"
				}

				val upsertClientBody = objectMapper.writeValueAsBytes(requestJson)
					.toRequestBody("application/json".toMediaType())
				val upsertClientRequest = Request.Builder()
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.header("Cookie", session.cookie)
					.header("X-CSRF-Token", session.csrfToken)
					.url(requestUrl)
					.post(upsertClientBody)
					.build()

				client.newCall(upsertClientRequest).execute().use { response ->
					val responseBody = response.body?.string().orEmpty()
					check(response.isSuccessful) {
						"3x-ui client upsert failed: HTTP ${response.code}: $responseBody"
					}
					check(responseBody.isNotBlank()) { "3x-ui returned an empty client upsert response" }
					val root = objectMapper.readTree(responseBody)
					check(root.path("success").asBoolean(false)) {
						root.path("msg").asText("3x-ui failed to create or attach the Trojan client")
					}
				}

				val serverHost = if (proxy.useSubDomain == true) {
					"${proxy.subdomain}.${host}"
				} else {
					host
				}
				val streamSettings = decodeJsonNode(objectMapper, inbound.path("streamSettings"))
				val tlsSettings = streamSettings.path("tlsSettings")
				val realitySettings = streamSettings.path("realitySettings")
				val security = streamSettings.path("security").asText()
				val sni = if (security.equals("reality", ignoreCase = true)) {
					realitySettings.path("serverNames")
						.takeIf { it.isArray && it.size() > 0 }
						?.get(0)
						?.asText()
				} else {
					findText(tlsSettings, "serverName")
				}
				val alpnNode = tlsSettings.path("alpn")
				val alpn = if (alpnNode.isArray && alpnNode.size() > 0) {
					alpnNode.elements()
						.asSequence()
						.map { it.asText() }
						.filter { it.isNotBlank() }
						.toList()
						.ifEmpty { listOf("h2") }
				} else {
					listOf("h2")
				}
				val fingerprint = findText(streamSettings, "fingerprint") ?: "chrome"

				return ConfigDataTrojan(
					type = "trojan",
					trojanServer = serverHost,
					serverPort = inboundPort,
					password = password,
					sni = sni ?: serverHost,
					fp = fingerprint,
					alpn = alpn,
				).apply {
					this.proxy = proxy
				}
			}

			HYSTERIA2 -> {
				val addNewUser = hysteriaService.addNewUser(proxy, host, user)
				val hysteriaHost = if (proxy.useSubDomain!!) {
					"${proxy.subdomain}.${host}"
				} else {
					host
				}
				return ConfigDataHysteria(
					password = "${addNewUser.uuid}:${addNewUser.password}",
					server = hysteriaHost,
					serverPort = proxy.proxyPort!!,
				).apply {
					this.proxy = proxy
				}
			}

			SSH -> {
				val password = sshService.addNewUser(proxy, host, user)
				return ConfigDataSsh(
					server = host,
					serverPort = proxy.proxyPort!!,
					password = password,
					user = user.name!!,
				).apply {
					this.proxy = proxy
				}
			}

			WIREGUARD, AMNEZIA_WG -> {
				val url = if (proxy.useSubDomain!!) {
					"https://${proxy.subdomain}.${host}"
				} else {
					"https://${host}:${proxy.port}"
				}
				val api = WgEasyAPI.Builder()
					.password(proxy.password)
					.host(url)
					.build()
				try {
					if (api.clients.none { it.name == user.name }) {
						api.createClient(user.name)
					}
					api.getConfig(api.getClientByName(user.name)!!.id, "vpn")
				} catch (e: UnsuccessfulHttpException) {
					println(e.message)
				}
				val file = File("vpn.conf")
				val config = file.readText().trim()
				file.delete()
				return WireguardConfigParser.parseConfig(config, host)
			}

			NAIVE -> {
				val loginBody: RequestBody = FormBody.Builder()
					.add("user", proxy.login!!)
					.add("pass", proxy.password!!)
					.build()
				val url = if (proxy.useSubDomain!!) {
					"https://${proxy.subdomain}.${host}"
				} else {
					"https://${host}:${proxy.port}"
				}
				var request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/api/login")
					.post(loginBody)
					.build()
				var response = createUnsafeOkHttpClient().newCall(request).execute()
				val cooke = response.headers["Set-Cookie"]
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/api/inbounds")
					.header("Cookie", cooke.toString())
					.get()
					.build()
				response = createUnsafeOkHttpClient().newCall(request).execute()
				var string = response.body?.string()
				val readValueInbounds = objectMapper.readValue(string, ObjectNode::class.java)
				val count = readValueInbounds.get("obj").get("inbounds").size()
				var inbound: SuiInbound? = null
				for (i in 0 until count) {
					request = Request.Builder()
						.header("Content-Type", "application/json")
						.url("${url}/api/inbounds?id=${i + 1}")
						.header("Cookie", cooke.toString())
						.get()
						.build()
					response = createUnsafeOkHttpClient().newCall(request).execute()
					val string = response.body?.string()
					val readValueInbounds = objectMapper.readValue(string, SuiInboundsResponse::class.java)
					val first = readValueInbounds.obj?.inbounds?.first()
					if (first?.type?.equals("naive", ignoreCase = true) == true) {
						inbound = first
						break
					}
				}
				val port = inbound?.listen_port?.toLong()
				val id = inbound?.id
				val sniOrIp = if (proxy.serverIp != null) {
					proxy.serverIp
				} else {
					inbound?.addrs?.first()?.server!!
				}
				val sni = inbound?.addrs?.first()?.server!!
				val password = UserService.generateSecretKey()

				fun enc(s: String): String = URLEncoder.encode(s, UTF_8.name())

				val json = """{
				  "enable": true,
				  "name": "${user.name}",
				  "config": {
					"naive": {
					  "username": "${user.name}",
					  "password": "$password"
					}
				  },
				  "inbounds": ${listOf(id).joinToString(prefix = "[", postfix = "]")},
				  "links": [],
				  "volume": 0,
				  "expiry": 0,
				  "up": 0,
				  "down": 0,
				  "desc": "",
				  "group": ""
				}"""
				val newUserBody = FormBody.Builder()
					.add("object", "clients")
					.add("action", "new")
					.addEncoded("data", enc(json))
					.build()
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/api/save")
					.header("Cookie", cooke.toString())
					.post(newUserBody)
					.build()
				createUnsafeOkHttpClient().newCall(request).execute()
				return ConfigDataNaive(
					server = sniOrIp,
					serverPort = port!!,
					username = user.name!!,
					password = password,
					tls = TlsSettings(
						enabled = true,
						serverName = sni,
					),
				).apply {
					this.proxy = proxy
				}
			}
		}
	}


	fun getProxyServer(secretKey: String): List<ConfigData> {
		return userRepository.findBySecretKey(secretKey)
			?.proxiesConfigs
			?.map { it.fillFields() }
			?: emptyList()
	}

}