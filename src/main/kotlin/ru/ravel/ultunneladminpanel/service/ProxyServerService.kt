package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
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
import ru.ravel.ultunneladminpanel.model.xui.ThreeXuiType
import ru.ravel.ultunneladminpanel.repository.ProxyRepository
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import ru.ravel.ultunneladminpanel.repository.UserRepository
import ru.ravel.ultunneladminpanel.repository.ConfigDataRepository
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
	private val configDataRepository: ConfigDataRepository,
	private val entityManager: EntityManager,
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
	fun deleteServer(serverId: Long) {
		// Keep only scalar proxy IDs while config cleanup runs. The cleanup uses
		// bulk/native mutations and loads ConfigData/User entities, so keeping the
		// original ProxyServer graph managed would leave stale ConfigData -> Proxy
		// references in the persistence context. When cascade removal later marks
		// all server proxies as deleted, Hibernate then fails on transaction flush
		// with TransientObjectException.
		val proxyIds = proxyServerRepository.findById(serverId).orElseThrow()
			.proxies
			.orEmpty()
			.mapNotNull { it.id }
			.toSet()

		deleteConfigsForProxies(proxyIds, configType = null)

		// Bulk deletes/updates bypass Hibernate managed state. Drop every stale
		// ConfigData/User/Proxy instance, then load a fresh server graph and let
		// orphan/cascade removal delete its proxies from a clean context.
		entityManager.clear()

		val serverToDelete = proxyServerRepository.findById(serverId).orElseThrow()
		proxyServerRepository.delete(serverToDelete)
		proxyServerRepository.flush()
	}


	@Transactional
	fun deleteProxy(proxyId: Long) {
		// Keep only scalar server IDs across the config cleanup. The cleanup uses
		// bulk/native mutations, so keeping ProxyServer/Proxy entities managed in
		// the same persistence context can leave Hibernate with stale references.
		val serverIds = proxyServerRepository.findAll()
			.filter { server -> server.proxies?.any { it.id == proxyId } == true }
			.mapNotNull { it.id }

		if (serverIds.isEmpty()) {
			throw NoSuchElementException("Proxy $proxyId is not attached to any server")
		}

		// Delete only configs of this protocol. Legacy rows of other protocols may
		// point at the same Proxy row; deleting those would drop configs the admin
		// never asked to remove (and used to fail on WireGuard child-table FKs).
		val proxyType = proxyRepository.findById(proxyId).orElseThrow().type
		deleteConfigsForProxies(setOf(proxyId), proxyType?.configType)

		// Whatever still references this proxy is a foreign-type legacy config:
		// keep the config, drop the invalid FK so the proxy itself can be removed.
		configDataRepository.detachAllFromProxy(proxyId)

		// Bulk deletes/updates bypass Hibernate's managed entity state. In the
		// Hysteria2 case ConfigData entities loaded during cleanup still referenced
		// this Proxy in memory even after their DB rows/FKs were changed. Removing
		// the proxy via orphanRemoval then failed with TransientObjectException.
		// Clear those stale managed objects and reload only the servers we need.
		entityManager.clear()

		val servers = proxyServerRepository.findAllById(serverIds)
		servers.forEach { server ->
			server.proxies?.removeIf { it.id == proxyId }
		}
		proxyServerRepository.saveAllAndFlush(servers)
	}


	/**
	 * Deletes user configs of [configType] ("vless", "hysteria2", ...) that belong to
	 * [proxyIds], or configs of every type when [configType] is null. Works straight on
	 * config_data, so configs that are no longer linked to any user are removed too.
	 * Returns the number of deleted configs.
	 */
	@Transactional
	fun deleteConfigsForProxies(proxyIds: Set<Long>, configType: String?): Int {
		if (proxyIds.isEmpty()) return 0

		// Read config_data directly: legacy/orphaned configs may no longer be
		// reachable through User.proxiesConfigs, while proxy_id still blocks the FK.
		val configsToDelete = if (configType == null) {
			configDataRepository.findAllByProxy_IdIn(proxyIds)
		} else {
			configDataRepository.findAllByProxy_IdInAndType(proxyIds, configType)
		}
		if (configsToDelete.isEmpty()) return 0

		val configIds = configsToDelete.mapNotNull { it.id }.toHashSet()
		if (configIds.isEmpty()) return 0

		unlinkConfigsFromUsers(configIds)
		deleteConfigChildRows(configIds)

		// Use a bulk delete instead of EntityManager.remove() for every ConfigData.
		// With JOINED inheritance, legacy/inconsistent subclass rows can otherwise
		// trigger StaleObjectStateException when Hibernate expects one exact row
		// to be deleted from (for example) config_data_vless.
		return configDataRepository.deleteAllByConfigIds(configIds)
	}


	/**
	 * WireGuard/Amnezia configs own @ElementCollection and peer rows. A JPQL bulk
	 * delete leaves those behind, and their FK to config_data_wireguard then aborts
	 * the whole delete, so remove them first.
	 */
	private fun deleteConfigChildRows(configIds: Set<Long>) {
		if (configIds.isEmpty()) return

		configDataRepository.deleteWireguardPeerAllowedIps(configIds)
		configDataRepository.deleteWireguardPeerReserved(configIds)
		configDataRepository.deleteWireguardPeers(configIds)
		configDataRepository.deleteWireguardLocalAddresses(configIds)
		configDataRepository.deleteWireguardReserved(configIds)
		configDataRepository.deleteAmneziaLocalAddresses(configIds)
		configDataRepository.deleteAmneziaReserved(configIds)
	}


	private fun unlinkConfigsFromUsers(configIds: Set<Long>) {
		if (configIds.isEmpty()) return

		// Compare scalar IDs only: data classes for legacy config entities may
		// still have nullable values that make equals/hashCode unsafe.
		userRepository.findAll().forEach { user ->
			val iterator = user.proxiesConfigs.iterator()
			while (iterator.hasNext()) {
				val configId = iterator.next().id
				if (configId != null && configIds.contains(configId)) {
					iterator.remove()
				}
			}
		}

		userRepository.flush()
	}


	private data class ThreeXuiSession(
		val baseUrl: String,
		val cookie: String,
		val csrfToken: String,
	)

	private fun threeXuiBaseUrl(host: String, proxy: Proxy): String {
		return if (proxy.useSubDomain == true) {
			"https://${proxy.subdomain}.${host}"
		} else {
			"https://${host}:${proxy.port}"
		}
	}

	private fun cookieHeader(setCookieHeaders: List<String>): String {
		return setCookieHeaders
			.map { it.substringBefore(';').trim() }
			.filter { it.isNotEmpty() }
			.joinToString("; ")
	}

	private fun mergeCookies(oldCookie: String, setCookieHeaders: List<String>): String {
		val cookies = linkedMapOf<String, String>()

		fun putCookie(raw: String) {
			val pair = raw.substringBefore(';').trim()
			val separator = pair.indexOf('=')
			if (separator > 0) {
				cookies[pair.substring(0, separator)] = pair.substring(separator + 1)
			}
		}

		oldCookie.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach(::putCookie)
		setCookieHeaders.forEach(::putCookie)

		return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
	}

	private fun readJsonResponse(
		request: Request,
		objectMapper: ObjectMapper,
		operation: String,
	): JsonNode {
		return createUnsafeOkHttpClient().newCall(request).execute().use { response ->
			val responseBody = response.body?.string().orEmpty()
			if (!response.isSuccessful) {
				error("3x-ui $operation failed: HTTP ${response.code}: $responseBody")
			}

			val root = objectMapper.readTree(responseBody)
				?: error("3x-ui $operation returned empty JSON")

			if (root.has("success") && !root.path("success").asBoolean()) {
				error("3x-ui $operation failed: ${root.path("msg").asText("unknown error")}")
			}

			root
		}
	}

	private class ThreeXuiCookieJar : CookieJar {
		private val cookies = mutableListOf<Cookie>()

		@Synchronized
		override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
			newCookies.forEach { newCookie ->
				cookies.removeAll {
					it.name == newCookie.name &&
						it.domain == newCookie.domain &&
						it.path == newCookie.path
				}
				cookies += newCookie
			}
		}

		@Synchronized
		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			val now = System.currentTimeMillis()
			cookies.removeAll { it.expiresAt < now }
			return cookies.filter { it.matches(url) }
		}

		@Synchronized
		fun cookieHeader(): String = cookies
			.filter { it.expiresAt >= System.currentTimeMillis() }
			.joinToString("; ") { "${it.name}=${it.value}" }
	}

	private fun loginThreeXui(host: String, proxy: Proxy, objectMapper: ObjectMapper): ThreeXuiSession {
		val baseUrl = threeXuiBaseUrl(host, proxy)
		val cookieJar = ThreeXuiCookieJar()
		val client = createUnsafeOkHttpClient()
			.newBuilder()
			.cookieJar(cookieJar)
			.build()

		// Keep one OkHttpClient for the whole browser-session flow. This is important:
		// Set-Cookie can be returned by an intermediate redirect, not only the final response.
		val csrfRequest = Request.Builder()
			.url("$baseUrl/csrf-token")
			.get()
			.build()

		val csrfToken = client.newCall(csrfRequest).execute().use { response ->
			val responseBody = response.body?.string().orEmpty()
			if (!response.isSuccessful) {
				error("3x-ui csrf-token failed: HTTP ${response.code}: $responseBody")
			}

			val root = objectMapper.readTree(responseBody)
				?: error("3x-ui csrf-token returned empty JSON")
			if (!root.path("success").asBoolean()) {
				error("3x-ui csrf-token failed: ${root.path("msg").asText("unknown error")}")
			}

			root.path("obj").asText().takeIf { it.isNotBlank() }
				?: error("3x-ui csrf-token is empty")
		}

		val loginJson = objectMapper.createObjectNode().apply {
			put("username", proxy.login)
			put("password", proxy.password)
		}
		val loginBody = objectMapper.writeValueAsString(loginJson)
			.toRequestBody("application/json".toMediaType())
		val loginRequest = Request.Builder()
			.url("$baseUrl/login")
			.header("Content-Type", "application/json")
			.header("X-CSRF-Token", csrfToken)
			.post(loginBody)
			.build()

		client.newCall(loginRequest).execute().use { response ->
			val responseBody = response.body?.string().orEmpty()
			if (!response.isSuccessful) {
				error("3x-ui login failed: HTTP ${response.code}: $responseBody")
			}

			val root = objectMapper.readTree(responseBody)
				?: error("3x-ui login returned empty JSON")
			if (!root.path("success").asBoolean()) {
				error("3x-ui login failed: ${root.path("msg").asText("unknown error")}")
			}
		}

		val authenticatedCookie = cookieJar.cookieHeader()
		if (authenticatedCookie.isBlank()) {
			error(
				"3x-ui login succeeded but no session cookie was received. " +
					"Check whether a reverse proxy is stripping Set-Cookie headers."
			)
		}

		return ThreeXuiSession(
			baseUrl = baseUrl,
			cookie = authenticatedCookie,
			csrfToken = csrfToken,
		)
	}

	private fun getThreeXuiInbound(
		session: ThreeXuiSession,
		protocol: String,
		objectMapper: ObjectMapper,
	): ObjectNode {
		val request = Request.Builder()
			.url("${session.baseUrl}/panel/api/inbounds/list")
			.header("Cookie", session.cookie)
			.get()
			.build()

		val root = readJsonResponse(request, objectMapper, "list inbounds")
		val inbounds = root.path("obj")
		if (!inbounds.isArray) error("3x-ui list inbounds returned invalid obj")

		return inbounds
			.filter { it.path("protocol").asText().equals(protocol, ignoreCase = true) }
			.lastOrNull() as? ObjectNode
			?: error("3x-ui inbound for protocol '$protocol' not found")
	}

	private fun listThreeXuiInbounds(
		session: ThreeXuiSession,
		objectMapper: ObjectMapper,
	): List<ObjectNode> {
		val request = Request.Builder()
			.url("${session.baseUrl}/panel/api/inbounds/list")
			.header("Accept", "application/json")
			.header("Cookie", session.cookie)
			.get()
			.build()

		val root = readJsonResponse(request, objectMapper, "list inbounds")
		val inbounds = root.path("obj")
		if (!inbounds.isArray) error("3x-ui list inbounds returned invalid obj")

		return inbounds.mapNotNull { it as? ObjectNode }
	}

	/**
	 * The inbound new clients of [protocol] are provisioned into. One server may run several
	 * inbounds of the same protocol, so Proxy.proxyPort ([preferredPort]) picks the one the
	 * admin configured by hand; without it the newest inbound wins, as everywhere else here.
	 */
	private fun selectThreeXuiInbound(
		session: ThreeXuiSession,
		protocol: String,
		preferredPort: Long?,
		objectMapper: ObjectMapper,
	): ObjectNode {
		val matching = listThreeXuiInbounds(session, objectMapper)
			.filter { it.path("protocol").asText().equals(protocol, ignoreCase = true) }
		if (matching.isEmpty()) {
			error("3x-ui inbound for protocol '$protocol' not found")
		}

		val byPort = preferredPort
			?.takeIf { it > 0 }
			?.let { port -> matching.filter { it.path("port").asLong() == port } }
			?.takeIf { it.isNotEmpty() }

		return (byPort ?: matching).last()
	}

	/**
	 * Re-reads one inbound. WireGuard and AmneziaWG keep their per-client tunnel fields
	 * (keys, addresses) in the inbound's own settings JSON instead of the shared clients
	 * table, so the generated values have to be read back from here.
	 */
	private fun getThreeXuiInboundById(
		session: ThreeXuiSession,
		inboundId: Long,
		objectMapper: ObjectMapper,
	): ObjectNode {
		val request = Request.Builder()
			.url("${session.baseUrl}/panel/api/inbounds/get/$inboundId")
			.header("Accept", "application/json")
			.header("Cookie", session.cookie)
			.get()
			.build()

		val root = readJsonResponse(request, objectMapper, "get inbound")
		return root.path("obj") as? ObjectNode
			?: error("3x-ui get inbound $inboundId returned invalid obj")
	}

	/**
	 * The client record of [email], or null when the panel has none. In 3x-ui v3 a client is
	 * one global identity attached to many inbounds, so this is also how an existing client
	 * is found before creating a duplicate.
	 */
	private fun findThreeXuiClientRecord(
		session: ThreeXuiSession,
		email: String,
		objectMapper: ObjectMapper,
	): JsonNode? {
		val request = Request.Builder()
			.url("${session.baseUrl}/panel/api/clients/list")
			.header("Accept", "application/json")
			.header("Cookie", session.cookie)
			.get()
			.build()

		val root = readJsonResponse(request, objectMapper, "list clients")
		return root.path("obj")
			.firstOrNull { it.path("email").asText() == email }
	}

	/**
	 * Creates [client] on [inboundId], or attaches the identity that already carries this
	 * email. The second case is not an edge case: the local transaction may have failed after
	 * the panel call, and the same user may already hold this protocol on another inbound.
	 */
	private fun upsertThreeXuiClient(
		session: ThreeXuiSession,
		inboundId: Long,
		email: String,
		client: ObjectNode,
		objectMapper: ObjectMapper,
	) {
		val existing = findThreeXuiClientRecord(session, email, objectMapper)
		if (existing == null) {
			addThreeXuiClient(session, inboundId, client, objectMapper)
			return
		}

		val payload = objectMapper.createObjectNode().apply {
			putArray("inboundIds").add(inboundId)
		}
		val body = objectMapper.writeValueAsString(payload)
			.toRequestBody("application/json".toMediaType())
		val request = Request.Builder()
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.header("Cookie", session.cookie)
			.header("X-CSRF-Token", session.csrfToken)
			.url("${session.baseUrl}/panel/api/clients/${URLEncoder.encode(email, UTF_8.name())}/attach")
			.post(body)
			.build()

		readJsonResponse(request, objectMapper, "attach client")
	}

	/**
	 * The settings JSON of one inbound. WireGuard and AmneziaWG keep the per-client tunnel
	 * fields (keys, addresses) only here, and it is also where a value the panel generated
	 * while attaching an existing identity shows up first.
	 */
	private fun getThreeXuiInboundSettings(
		session: ThreeXuiSession,
		inboundId: Long,
		objectMapper: ObjectMapper,
	): ObjectNode {
		val inbound = getThreeXuiInboundById(session, inboundId, objectMapper)
		return objectField(inbound.get("settings"), objectMapper)
			?: error("3x-ui inbound $inboundId has no settings")
	}

	private fun findThreeXuiSettingsClient(settings: ObjectNode, email: String): JsonNode? {
		return settings.path("clients")
			.takeIf { it.isArray }
			?.firstOrNull { it.path("email").asText() == email }
	}

	/**
	 * Hysteria2 obfuscation of an inbound as (type, password), or null when it is off.
	 * The panel stores it as a "salamander" UDP mask under streamSettings.finalmask.
	 */
	private fun hysteriaObfs(stream: ObjectNode): Pair<String, String>? {
		val finalmask = stream.path("finalmask")
		val masks = finalmask.path("udp")
		if (!masks.isArray) return null

		return masks
			.firstOrNull { it.path("type").asText() == "salamander" }
			?.path("settings")
			?.path("password")
			?.asText()
			?.takeIf { it.isNotBlank() }
			?.let { "salamander" to it }
	}

	/**
	 * The UDP port-hopping range of a Hysteria2 inbound ("2000-3000,5000"), or null when
	 * hopping is off. xray-core 26.9.9 moved it from finalmask.quicParams.udpHop into a
	 * "udphop" mask, so both places are read.
	 */
	private fun hysteriaHopPorts(stream: ObjectNode): String? {
		val finalmask = stream.path("finalmask")
		val masks = finalmask.path("udp")
		if (masks.isArray) {
			masks
				.firstOrNull { it.path("type").asText() == "udphop" }
				?.path("settings")
				?.path("remotePorts")
				?.asText()
				?.trim()
				?.takeIf { it.isNotEmpty() }
				?.let { return it }
		}

		return finalmask.path("quicParams").path("udpHop").path("ports")
			.asText("")
			.trim()
			.takeIf { it.isNotEmpty() }
	}

	private fun objectField(node: JsonNode?, objectMapper: ObjectMapper): ObjectNode? {
		if (node == null || node.isNull) return null
		if (node.isObject) return node as ObjectNode
		if (node.isTextual && node.asText().isNotBlank()) {
			return objectMapper.readTree(node.asText()) as? ObjectNode
		}
		return null
	}

	private fun addThreeXuiClient(
		session: ThreeXuiSession,
		inboundId: Long,
		client: ObjectNode,
		objectMapper: ObjectMapper,
	) {
		val payload = objectMapper.createObjectNode().apply {
			set<JsonNode>("client", client)
			putArray("inboundIds").add(inboundId)
		}

		val body = objectMapper.writeValueAsString(payload)
			.toRequestBody("application/json".toMediaType())
		val request = Request.Builder()
			.url("${session.baseUrl}/panel/api/clients/add")
			.header("Content-Type", "application/json")
			.header("Cookie", session.cookie)
			.header("X-CSRF-Token", session.csrfToken)
			.post(body)
			.build()

		readJsonResponse(request, objectMapper, "add client")
	}

	private fun randomThreeXuiSubId(): String {
		return UUID.randomUUID().toString().replace("-", "").take(16)
	}

	@Transactional
	fun createUserProxy(host: String, proxy: Proxy, user: User): ConfigData {
		val objectMapper = ObjectMapper()
		when (proxy.type!!) {
			VLESS -> {
				val protocol = "vless"
				val session = loginThreeXui(host, proxy, objectMapper)
				val inbound = getThreeXuiInbound(session, protocol, objectMapper)
				val inboundId = inbound.path("id").asLong()
				val port = inbound.path("port").asLong()
				if (inboundId <= 0L) error("3x-ui VLESS inbound has invalid id")
				if (port <= 0L) error("3x-ui VLESS inbound has invalid port")

				val stream = objectField(inbound.get("streamSettings"), objectMapper)
					?: objectMapper.createObjectNode()
				val tlsSettings = objectField(stream.get("tlsSettings"), objectMapper)
				val xhttpSettings = objectField(stream.get("xhttpSettings"), objectMapper)

				val sni = tlsSettings?.path("serverName")?.asText()?.takeIf { it.isNotBlank() }
				val alpn = tlsSettings?.path("alpn")
					?.takeIf { it.isArray }
					?.map { it.asText() }
					?.filter { it.isNotBlank() }
					?.takeIf { it.isNotEmpty() }
					?: listOf("h2")

				val uuid = UUID.randomUUID().toString()
				val client = objectMapper.createObjectNode().apply {
					put("email", "${user.name}-vless")
					put("id", uuid)
					put("subId", randomThreeXuiSubId())
					put("limitIp", 0)
					put("totalGB", 0)
					put("expiryTime", 0)
					put("tgId", 0)
					put("comment", "")
					put("enable", true)
				}
				addThreeXuiClient(session, inboundId, client, objectMapper)

				val server = if (proxy.useSubDomain == true) {
					"${proxy.subdomain}.${host}"
				} else {
					host
				}

				return ConfigDataVless(
					type = protocol,
					uuid = uuid,
					server = server,
					serverPort = port,
					tls = TlsSettings(
						enabled = true,
						serverName = sni ?: host,
						alpn = alpn,
						utls = UtlsSettings(
							enabled = true,
							fingerprint = "chrome"
						),
					),
					transport = TransportSettings(
						type = "xhttp",
						host = xhttpSettings?.path("host")?.asText()?.takeIf { it.isNotBlank() }
							?: (sni ?: host),
						path = xhttpSettings?.path("path")?.asText()?.takeIf { it.isNotBlank() } ?: "/",
						mode = xhttpSettings?.path("mode")?.asText()?.takeIf { it.isNotBlank() } ?: "auto",
					),
				).apply {
					this.proxy = proxy
				}
			}

			TROJAN -> {
				val protocol = "trojan"
				val session = loginThreeXui(host, proxy, objectMapper)
				val inbound = getThreeXuiInbound(session, protocol, objectMapper)
				val inboundId = inbound.path("id").asLong()
				val port = inbound.path("port").asLong()
				if (inboundId <= 0L) error("3x-ui Trojan inbound has invalid id")
				if (port <= 0L) error("3x-ui Trojan inbound has invalid port")

				val stream = objectField(inbound.get("streamSettings"), objectMapper)
					?: objectMapper.createObjectNode()
				val tlsSettings = objectField(stream.get("tlsSettings"), objectMapper)
				val alpn = tlsSettings?.path("alpn")
					?.takeIf { it.isArray }
					?.map { it.asText() }
					?.filter { it.isNotBlank() }
					?.takeIf { it.isNotEmpty() }
					?: listOf("h2")

				val password = UUID.randomUUID().toString()
				val client = objectMapper.createObjectNode().apply {
					put("email", "${user.name}-trojan")
					put("password", password)
					put("subId", randomThreeXuiSubId())
					put("limitIp", 0)
					put("totalGB", 0)
					put("expiryTime", 0)
					put("tgId", 0)
					put("comment", "")
					put("enable", true)
				}
				addThreeXuiClient(session, inboundId, client, objectMapper)

				val sniHost = tlsSettings?.path("serverName")?.asText()?.takeIf { it.isNotBlank() }
					?: if (proxy.useSubDomain == true) {
						"${proxy.subdomain}.${host}"
					} else {
						host
					}

				return ConfigDataTrojan(
					trojanServer = host,
					serverPort = port,
					password = password,
					sni = sniHost,
					fp = "chrome",
					alpn = alpn,
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

			/*
			 * Hysteria2 is provisioned through 3x-ui itself: the panel runs it as a
			 * "hysteria" inbound with streamSettings.hysteriaSettings.version = 2, so the
			 * standalone Hysteria HTTP API is no longer involved.
			 */
			HYSTERIA2 -> {
				val protocol = "hysteria"
				val session = loginThreeXui(host, proxy, objectMapper)
				val inbound = selectThreeXuiInbound(session, protocol, proxy.proxyPort, objectMapper)
				val inboundId = inbound.path("id").asLong()
				val port = inbound.path("port").asLong()
				if (inboundId <= 0L) error("3x-ui Hysteria inbound has invalid id")
				if (port !in 1L..65535L) error("3x-ui Hysteria inbound has invalid port")

				val stream = objectField(inbound.get("streamSettings"), objectMapper)
					?: objectMapper.createObjectNode()
				val hysteriaSettings = objectField(stream.get("hysteriaSettings"), objectMapper)
				val version = hysteriaSettings?.path("version")?.asInt(2) ?: 2
				if (version == 1) {
					error("3x-ui inbound $inboundId runs Hysteria v1, and the client config speaks hysteria2 only")
				}

				val tlsSettings = objectField(stream.get("tlsSettings"), objectMapper)
				val sni = tlsSettings?.path("serverName")?.asText()?.takeIf { it.isNotBlank() }
				val alpn = tlsSettings?.path("alpn")
					?.takeIf { it.isArray }
					?.map { it.asText() }
					?.filter { it.isNotBlank() }
					?.takeIf { it.isNotEmpty() }
					?: listOf("h3")
				val obfs = hysteriaObfs(stream)

				val email = "${user.name}-hysteria2"
				val client = objectMapper.createObjectNode().apply {
					put("email", email)
					/* The panel generates one when this is blank; passing it keeps the created
					   credential in hand instead of depending on reading it back. */
					put("auth", UUID.randomUUID().toString().replace("-", ""))
					put("subId", randomThreeXuiSubId())
					put("limitIp", 0)
					put("totalGB", 0)
					put("expiryTime", 0)
					put("tgId", 0)
					put("comment", "")
					put("reset", 0)
					put("enable", true)
				}
				upsertThreeXuiClient(session, inboundId, email, client, objectMapper)

				/*
				 * Read the auth back: an attached client keeps the one it already had, and an
				 * identity created for another protocol only gets one while being attached —
				 * that one reaches the inbound's settings before the clients table.
				 */
				val auth = findThreeXuiClientRecord(session, email, objectMapper)
					?.path("auth")
					?.asText()
					?.takeIf { it.isNotBlank() }
					?: findThreeXuiSettingsClient(getThreeXuiInboundSettings(session, inboundId, objectMapper), email)
						?.path("auth")
						?.asText()
						?.takeIf { it.isNotBlank() }
					?: error("3x-ui returned no Hysteria auth for client $email")

				val hysteriaHost = if (proxy.useSubDomain == true) {
					"${proxy.subdomain}.${host}"
				} else {
					host
				}

				return ConfigDataHysteria(
					password = auth,
					server = hysteriaHost,
					serverPort = port,
					sni = sni ?: hysteriaHost,
					alpnList = alpn.joinToString(","),
					insecure = true,
					obfsType = obfs?.first,
					obfsPassword = obfs?.second,
					portHopping = hysteriaHopPorts(stream),
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

			WIREGUARD -> {
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
				return WireguardConfigParser.parseConfig(config, host).apply {
					this.proxy = proxy
				}
			}

			/*
			 * AmneziaWG is provisioned through 3x-ui as well ("amneziawg" inbound, native
			 * since 3x-ui v3). The keypair and the tunnel address are left to the panel: it
			 * owns the inbound's subnet and knows which addresses are already taken.
			 */
			AMNEZIA_WG -> {
				val protocol = "amneziawg"
				val session = loginThreeXui(host, proxy, objectMapper)
				val inbound = selectThreeXuiInbound(session, protocol, proxy.proxyPort, objectMapper)
				val inboundId = inbound.path("id").asLong()
				val port = inbound.path("port").asLong()
				if (inboundId <= 0L) error("3x-ui AmneziaWG inbound has invalid id")
				if (port !in 1L..65535L) error("3x-ui AmneziaWG inbound has invalid port")

				val email = "${user.name}-amneziawg"
				val client = objectMapper.createObjectNode().apply {
					put("email", email)
					put("subId", randomThreeXuiSubId())
					put("limitIp", 0)
					put("totalGB", 0)
					put("expiryTime", 0)
					put("tgId", 0)
					put("comment", "")
					put("reset", 0)
					put("enable", true)
				}
				upsertThreeXuiClient(session, inboundId, email, client, objectMapper)

				/* The tunnel fields live in the inbound's settings JSON, not in the clients
				   table: one identity attached to two tunnels has two different addresses. */
				val settings = getThreeXuiInboundSettings(session, inboundId, objectMapper)
				val serverSettings = objectField(settings.get("server"), objectMapper)
					?: error("3x-ui AmneziaWG inbound $inboundId has no server block")
				val clientSettings = findThreeXuiSettingsClient(settings, email)
					?: error("3x-ui did not return the AmneziaWG client $email")

				/* A tunnel endpoint is dialled over UDP, so the panel's subdomain is only
				   usable when it resolves to the server itself. */
				val endpoint = when {
					proxy.useSubDomain == true -> "${proxy.subdomain}.${host}"
					else -> proxy.serverIp ?: host
				}
				val remark = listOfNotNull(
					inbound.path("remark").asText("").takeIf { it.isNotBlank() },
					user.name,
				).joinToString(" - ")

				return ConfigDataAmneziaWireguard(
					server = endpoint,
					serverPort = port,
					localAddress = AmneziaWgConfigBuilder.allowedIps(clientSettings),
					privateKey = clientSettings.path("privateKey").asText().takeIf { it.isNotBlank() },
					peerPublicKey = serverSettings.path("publicKey").asText().takeIf { it.isNotBlank() },
					preSharedKey = clientSettings.path("preSharedKey").asText().takeIf { it.isNotBlank() },
					mtu = AmneziaWgConfigBuilder.effectiveMtu(
						serverSettings.path("mtu").asInt(0),
						serverSettings.path("s4").asInt(0),
					),
					dns = AmneziaWgConfigBuilder.dnsServers(serverSettings)
						.joinToString(",")
						.takeIf { it.isNotBlank() },
					clientConfig = AmneziaWgConfigBuilder.build(
						server = serverSettings,
						client = clientSettings,
						endpoint = endpoint,
						port = port,
						remark = remark,
					),
				).apply {
					this.proxy = proxy
				}
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