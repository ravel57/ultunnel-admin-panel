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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.component.createUnsafeOkHttpClient
import ru.ravel.ultunneladminpanel.component.readBodyOrEmpty
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

	private val logger: Logger = LoggerFactory.getLogger(ProxyServerService::class.java)

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

		fun csrfToken(): String = client.newCall(csrfRequest).execute().use { response ->
			val responseBody = response.readBodyOrEmpty()
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

		/* /login is behind the CSRF filter too, so it needs the anonymous token. */
		val loginCsrfToken = csrfToken()

		val loginJson = objectMapper.createObjectNode()
			.put("username", proxy.login)
			.put("password", proxy.password)
		val loginBody = objectMapper.writeValueAsBytes(loginJson)
			.toRequestBody("application/json".toMediaType())
		val loginRequest = Request.Builder()
			.header("Content-Type", "application/json")
			.header("Cookie", cookieHeader(cookies))
			.header("X-CSRF-Token", loginCsrfToken)
			.url("$baseUrl/login")
			.post(loginBody)
			.build()

		client.newCall(loginRequest).execute().use { response ->
			val responseBody = response.readBodyOrEmpty()
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

		/* The login wipes the session, CSRF token included: mint one that belongs to it.
		   Minted before the cookie header is built, since this call refreshes the session. */
		val sessionCsrfToken = csrfToken()

		return ThreeXuiV3Session(
			cookie = cookieHeader(cookies),
			csrfToken = sessionCsrfToken,
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

    @Transactional
    fun addNewServer(proxyServer: ProxyServer): ProxyServer {
        require(proxyServer.id == null && proxyServer.proxies.isNullOrEmpty()) { "Add protocols after creating the server" }
        validateServerConnection(proxyServer)
        proxyServer.proxies = mutableListOf()
        return proxyServerRepository.save(proxyServer)
    }

    @Transactional
    fun editServer(request: ProxyServer): ProxyServer {
        val id = requireNotNull(request.id)
        proxyServerRepository.lockServer(id) ?: error("Server not found")
        val server = proxyServerRepository.findById(id).orElseThrow()
        server.name = request.name
        server.host = request.host
        server.login = request.login
        if (!request.password.isNullOrBlank()) server.password = request.password
        server.useSubDomain = request.useSubDomain
        server.subdomain = request.subdomain
        server.port = request.port
        server.serverIp = request.serverIp
        server.certificateFile = request.certificateFile
        server.privateKeyFile = request.privateKeyFile
        validateServerConnection(server)
        return proxyServerRepository.save(server)
    }

    private fun validateServerConnection(server: ProxyServer) {
        server.name = server.name?.trim(); server.host = server.host?.trim()
        server.login = server.login?.trim(); server.subdomain = server.subdomain?.trim()
        server.serverIp = server.serverIp?.trim()?.takeIf { it.isNotEmpty() }
        server.certificateFile = server.certificateFile?.trim()?.takeIf { it.isNotEmpty() }
        server.privateKeyFile = server.privateKeyFile?.trim()?.takeIf { it.isNotEmpty() }
        require((server.certificateFile == null) == (server.privateKeyFile == null)) {
            "Укажите оба пути: к сертификату и закрытому ключу"
        }
        require(listOfNotNull(server.certificateFile, server.privateKeyFile).all { it.startsWith("/") }) {
            "Пути к сертификату и ключу должны быть абсолютными путями на сервере"
        }
        require(!server.name.isNullOrBlank() && !server.host.isNullOrBlank()) { "Server name and host are required" }
        require(!server.host!!.contains("://") && !server.host!!.contains('/')) { "Host must not contain a scheme or path" }
        require(!server.login.isNullOrBlank() && !server.password.isNullOrBlank()) { "Panel login and password are required" }
        if (server.useSubDomain == true) require(!server.subdomain.isNullOrBlank()) { "Panel subdomain is required" }
        else require(server.port != null && server.port!! in 1L..65535L) { "Panel port must be between 1 and 65535" }
    }

    private fun connectionProxy(server: ProxyServer, proxy: Proxy): Proxy = proxy.copy(
        login = server.login, password = server.password, useSubDomain = server.useSubDomain,
        subdomain = server.subdomain, port = server.port, serverIp = server.serverIp)

    @Transactional
    fun migrateServerConnections() {
        proxyServerRepository.findAll().forEach { server ->
            if (server.login.isNullOrBlank()) {
                val candidates = server.proxies.orEmpty().filter { it.type != SSH && !it.login.isNullOrBlank() }
                val source = candidates.firstOrNull { it.type !in setOf(NAIVE, WIREGUARD) } ?: candidates.firstOrNull()
                if (source != null) {
                    server.login = source.login; server.password = source.password
                    server.useSubDomain = source.useSubDomain; server.subdomain = source.subdomain
                    server.port = source.port; server.serverIp = source.serverIp
                    proxyServerRepository.save(server)
                }
            }
        }
    }

    @Transactional
    fun addProxyToServer(proxyServerId: Long, request: Proxy): Proxy {
        proxyServerRepository.lockServer(proxyServerId) ?: error("Server not found")
        val server = proxyServerRepository.findById(proxyServerId).orElseThrow()
        val type = requireNotNull(request.type) { "Protocol type is required" }
        if (server.proxies.orEmpty().any { it.type == type || it.type?.configType == type.configType }) {
            throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "This protocol already exists on this server")
        }
        val proxy = if (type == SSH) {
            require(!request.login.isNullOrBlank() && !request.password.isNullOrBlank()) { "SSH credentials are required" }
            require(request.port != null && request.port!! in 1L..65535L) { "SSH port is invalid" }
            Proxy(type = SSH, login = request.login, password = request.password,
                port = request.port, proxyPort = request.proxyPort ?: request.port, serverIp = request.serverIp)
        } else {
            validateServerConnection(server)
            val mapper = ObjectMapper()
            val session = loginThreeXui(server.host!!, connectionProxy(server, Proxy(type = type)), mapper)
            val protocol = when (type) { HYSTERIA2 -> "hysteria"; AMNEZIA_WG -> "amneziawg"; else -> type.configType }
            val body = mapper.createObjectNode().put("protocol", protocol)
                .put("serverName", java.net.URI(session.baseUrl).host)
                .put("certificateFile", server.certificateFile)
                .put("privateKeyFile", server.privateKeyFile)
            val create = Request.Builder().url("${session.baseUrl}/panel/api/inbounds/provision")
                .header("Cookie", session.cookie).header("X-CSRF-Token", session.csrfToken)
                .post(mapper.writeValueAsBytes(body).toRequestBody("application/json".toMediaType())).build()
            val inbound = readJsonResponse(session.client, create, mapper, "provision inbound").path("obj")
            require(inbound.path("enable").asBoolean(true)) { "Managed inbound is disabled in the panel" }
            val inboundId = inbound.path("id").asLong()
            val port = inbound.path("port").asLong()
            require(inboundId > 0 && port in 1L..65535L) { "Panel returned an invalid inbound" }
            Proxy(type = type, inboundId = inboundId, proxyPort = port)
        }
        proxyRepository.save(proxy)
        if (server.proxies == null) server.proxies = mutableListOf()
        server.proxies!!.add(proxy)
        proxyServerRepository.save(server)
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


	/**
	 * [client] is the one the login ran on, and it owns that login's cookies. Every later
	 * call of the session has to go through it: OkHttp replaces a Cookie header the caller
	 * set with whatever the client's own jar returns, so a call made on another client can
	 * leave with another panel's session and come back 404.
	 */
	private data class ThreeXuiSession(
		val baseUrl: String,
		val cookie: String,
		val csrfToken: String,
		val client: OkHttpClient,
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
		client: OkHttpClient,
		request: Request,
		objectMapper: ObjectMapper,
		operation: String,
	): JsonNode {
		// The panel deliberately masks unauthenticated non-XHR API calls as empty 404s.
		val apiRequest = request.newBuilder()
			.header("X-Requested-With", "XMLHttpRequest")
			.build()
		return client.newCall(apiRequest).execute().use { response ->
			val responseBody = response.readBodyOrEmpty()
			if (!response.isSuccessful) {
				val detail = responseBody.ifBlank { "empty body" }
				val endpoint = "${response.request.method} ${response.request.url}"
				if (response.code == 401) {
					error("3x-ui $operation failed: HTTP 401 at $endpoint. " +
						"The API did not accept the logged-in session. Check session cookies and reverse-proxy routing to the same panel instance.")
				}
				if (response.code == 404) {
					error("3x-ui $operation failed: HTTP 404 at $endpoint: $detail. " +
						"Check the panel base path, reverse-proxy routing and installed panel version.")
				}
				if (response.code == 403) {
					error(
						"3x-ui $operation failed: HTTP 403: $detail. " +
							"The panel rejects an unsafe request whose CSRF token does not belong to the logged-in session."
					)
				}
				error("3x-ui $operation failed: HTTP ${response.code}: $detail")
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
		private val storedCookies = mutableListOf<Cookie>()

		@Synchronized
		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			cookies.forEach { newCookie ->
				storedCookies.removeAll {
					it.name == newCookie.name &&
						it.domain == newCookie.domain &&
						it.path == newCookie.path
				}
				storedCookies += newCookie
			}
		}

		@Synchronized
		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			val now = System.currentTimeMillis()
			storedCookies.removeAll { it.expiresAt < now }
			return storedCookies.filter { it.matches(url) }
		}

		@Synchronized
		fun cookieHeader(): String = storedCookies
			.filter { it.expiresAt >= System.currentTimeMillis() }
			.joinToString("; ") { "${it.name}=${it.value}" }
	}

	private fun fetchThreeXuiCsrfToken(
		client: OkHttpClient,
		baseUrl: String,
		objectMapper: ObjectMapper,
	): String {
		val request = Request.Builder()
			.url("$baseUrl/csrf-token")
			.get()
			.build()

		return client.newCall(request).execute().use { response ->
			val responseBody = response.readBodyOrEmpty()
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
	}

	private fun loginThreeXui(host: String, proxy: Proxy, objectMapper: ObjectMapper): ThreeXuiSession {
		val baseUrl = threeXuiBaseUrl(host, proxy)
		// The address is built out of the proxy row, so a protocol pointed at the wrong
		// subdomain or port is otherwise invisible: the call just fails somewhere else.
		logger.info("3x-ui: logging in to {} as {} (proxy {} {})", baseUrl, proxy.login, proxy.id, proxy.type)
		val cookieJar = ThreeXuiCookieJar()
		val client = createUnsafeOkHttpClient()
			.newBuilder()
			.cookieJar(cookieJar)
			.build()

		// Keep one OkHttpClient for the whole browser-session flow. This is important:
		// Set-Cookie can be returned by an intermediate redirect, not only the final response.
		// /login is itself behind the CSRF filter, so it needs this anonymous token.
		val loginCsrfToken = fetchThreeXuiCsrfToken(client, baseUrl, objectMapper)

		val loginJson = objectMapper.createObjectNode().apply {
			put("username", proxy.login)
			put("password", proxy.password)
		}
		val loginBody = objectMapper.writeValueAsString(loginJson)
			.toRequestBody("application/json".toMediaType())
		val loginRequest = Request.Builder()
			.url("$baseUrl/login")
			.header("Content-Type", "application/json")
			.header("X-CSRF-Token", loginCsrfToken)
			.post(loginBody)
			.build()

		client.newCall(loginRequest).execute().use { response ->
			val responseBody = response.readBodyOrEmpty()
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

		/*
		 * The panel wipes the session on login — the CSRF token above all, so that a token
		 * minted before the credentials went in cannot outlive the privilege change. The
		 * pre-login token is therefore already dead here, and every POST carrying it is
		 * answered with an empty 403. Mint a new one on the logged-in session.
		 */
		val csrfToken = fetchThreeXuiCsrfToken(client, baseUrl, objectMapper)

		return ThreeXuiSession(
			baseUrl = baseUrl,
			cookie = cookieJar.cookieHeader().ifBlank { authenticatedCookie },
			csrfToken = csrfToken,
			client = client,
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

		val root = readJsonResponse(session.client, request, objectMapper, "list inbounds")
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

		val root = readJsonResponse(session.client, request, objectMapper, "list inbounds")
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

		return (byPort ?: matching).last().also {
			logger.info(
				"3x-ui: {} inbound {} (port {}, remark '{}') of {} on {}",
				protocol,
				it.path("id").asLong(),
				it.path("port").asLong(),
				it.path("remark").asText(""),
				matching.size,
				session.baseUrl,
			)
		}
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

		val root = readJsonResponse(session.client, request, objectMapper, "get inbound")
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

		val root = readJsonResponse(session.client, request, objectMapper, "list clients")
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
			logger.info("3x-ui: created client {} on inbound {} of {}", email, inboundId, session.baseUrl)
			return
		}
		logger.info("3x-ui: client {} already exists, attaching it to inbound {} of {}", email, inboundId, session.baseUrl)

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

		readJsonResponse(session.client, request, objectMapper, "attach client")
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
    private fun hysteriaGeckoRange(stream: ObjectNode): Pair<Int, Int>? {
        val mask = stream.path("finalmask").path("udp")
            .firstOrNull { it.path("type").asText() == "salamander" } ?: return null
        val raw = mask.path("settings").path("packetSize").asText("").trim()
        if (raw.isEmpty()) return null
        val parts = raw.split("-")
        val minimum = parts.firstOrNull()?.trim()?.toIntOrNull()
        val maximum = if (parts.size == 1) minimum else parts.getOrNull(1)?.trim()?.toIntOrNull()
        require(parts.size <= 2 && minimum != null && maximum != null &&
            minimum in 1..65535 && maximum in minimum..65535) { "Invalid Gecko packetSize: $raw" }
        return minimum to maximum
    }

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

		readJsonResponse(session.client, request, objectMapper, "add client")
	}

	private fun randomThreeXuiSubId(): String {
		return UUID.randomUUID().toString().replace("-", "").take(16)
	}

    @Transactional
    fun createUserProxy(host: String, proxy: Proxy, user: User): ConfigData {
        if (proxy.type == SSH) {
            return ConfigDataSsh(server = host, serverPort = proxy.proxyPort ?: proxy.port!!,
                password = sshService.addNewUser(proxy, host, user), user = user.name!!).apply { this.proxy = proxy }
        }
        val server = proxyServerRepository.findByHost(host) ?: error("Server not found")
        validateServerConnection(server)
        val connection = connectionProxy(server, proxy)
        val mapper = ObjectMapper()
        if (proxy.type == AMNEZIA_WG) return createAmneziaClient(host, connection, user, mapper).apply { this.proxy = proxy }
        val protocol = when (proxy.type) { HYSTERIA2 -> "hysteria"; TROJAN2 -> "trojan"; else -> proxy.type!!.configType }
        val session = loginThreeXui(host, connection, mapper)
        val inbound = if (proxy.inboundId != null) getThreeXuiInboundById(session, proxy.inboundId!!, mapper)
            else selectThreeXuiInbound(session, protocol, proxy.proxyPort, mapper)
        require(inbound.path("protocol").asText() == protocol) { "Configured inbound protocol has changed" }
        require(inbound.path("enable").asBoolean(true)) { "Inbound is disabled" }
        val inboundId = inbound.path("id").asLong()
        val port = inbound.path("port").asInt()
        require(inboundId > 0 && port in 1..65535) { "Invalid inbound" }
        val email = "${user.name}-${proxy.type!!.configType}"
        val candidate = mapper.createObjectNode().apply {
            put("email", email); put("id", UUID.randomUUID().toString())
            put("password", UUID.randomUUID().toString()); put("auth", UUID.randomUUID().toString())
            put("subId", randomThreeXuiSubId()); put("enable", true)
            put("limitIp", 0); put("totalGB", 0); put("expiryTime", 0); put("tgId", 0)
            put("reset", 0); put("comment", "")
        }
        upsertThreeXuiClient(session, inboundId, email, candidate, mapper)
        val settings = getThreeXuiInboundSettings(session, inboundId, mapper)
        val identity = findThreeXuiClientRecord(session, email, mapper)
        return buildStoredProxyConfig(server, proxy, user, inbound, settings, identity)
    }

    private fun clientEmail(user: User, proxy: Proxy): String =
        "${user.name}-${if (proxy.type == AMNEZIA_WG) "amneziawg" else proxy.type!!.configType}"

    private fun buildStoredProxyConfig(server: ProxyServer, proxy: Proxy, user: User,
                                      inbound: JsonNode, settings: ObjectNode, identity: JsonNode?): ConfigData {
        val mapper = ObjectMapper()
        if (proxy.type == AMNEZIA_WG) return buildAmneziaConfig(server.host!!, connectionProxy(server, proxy),
            user, inbound, settings, mapper).apply { this.proxy = proxy }
        val email = clientEmail(user, proxy)
        val saved = findThreeXuiSettingsClient(settings, email) ?: error("Panel returned no attached client $email")
        fun credential(key: String): String? = saved.path(key).asText("").takeIf { it.isNotBlank() }
            ?: identity?.path(key)?.asText("")?.takeIf { it.isNotBlank() }
        val endpoint = server.serverIp?.takeIf { it.isNotBlank() } ?: server.host!!
        val protocol = when (proxy.type) { HYSTERIA2 -> "hysteria2"; TROJAN2 -> "trojan"; else -> proxy.type!!.configType }
        val port = inbound.path("port").asInt()
        val stream = objectField(inbound.get("streamSettings"), mapper) ?: mapper.createObjectNode()
        val result = if (proxy.type == WIREGUARD) {
            NativeProxyConfigBuilder.wireguard(endpoint, port, settings, saved, mapper)
        } else {
            NativeProxyConfigBuilder.build(protocol, endpoint, port, email,
                credential(if (protocol == "hysteria2") "auth" else "password") ?: "",
                credential("id") ?: credential("uuid"), settings, stream, mapper)
        }
        return result.apply { this.proxy = proxy }
    }

    // Only batch requests go to the panel; local rendering never creates or attaches clients.
    fun createUserProxiesBatch(serverId: Long, proxyId: Long, users: List<User>): Map<Long, Result<ConfigData>> {
        if (users.isEmpty()) return emptyMap()
        val server = proxyServerRepository.findById(serverId).orElseThrow()
        val proxy = server.proxies.orEmpty().firstOrNull { it.id == proxyId } ?: error("Protocol no longer exists")
        require(proxy.type != SSH) { "SSH does not support panel batches" }
        validateServerConnection(server)
        val mapper = ObjectMapper()
        val session = loginThreeXui(server.host!!, connectionProxy(server, proxy), mapper)
        val protocol = when (proxy.type) { HYSTERIA2 -> "hysteria"; AMNEZIA_WG -> "amneziawg"; TROJAN2 -> "trojan"; else -> proxy.type!!.configType }
        val inbound = proxy.inboundId?.let { getThreeXuiInboundById(session, it, mapper) }
            ?: selectThreeXuiInbound(session, protocol, proxy.proxyPort, mapper)
        val inboundId = inbound.path("id").asLong()
        require(inboundId > 0 && inbound.path("enable").asBoolean(true) && inbound.path("protocol").asText() == protocol) {
            "Configured inbound is disabled or its protocol has changed"
        }
        val batchClient = session.client.newBuilder().callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        fun post(path: String, body: JsonNode): JsonNode {
            val request = Request.Builder().url("${session.baseUrl}/panel/api/clients/$path")
                .header("Cookie", session.cookie).header("X-CSRF-Token", session.csrfToken)
                .post(mapper.writeValueAsBytes(body).toRequestBody("application/json".toMediaType())).build()
            return readJsonResponse(batchClient, request, mapper, path).path("obj")
        }
        fun listClients(): Map<String, JsonNode> {
            val request = Request.Builder().url("${session.baseUrl}/panel/api/clients/list")
                .header("Cookie", session.cookie).get().build()
            val records = readJsonResponse(batchClient, request, mapper, "list clients").path("obj")
            require(records.isArray) { "Panel returned an invalid client list" }
            return records.associateBy { it.path("email").asText() }
        }
        val failures = mutableMapOf<String, String>()
        var identities = listClients()
        users.filter { clientEmail(it, proxy) !in identities }.chunked(200).forEach { chunk ->
            val payload = mapper.createArrayNode()
            chunk.forEach { user ->
                val client = mapper.createObjectNode().apply {
                    put("email", clientEmail(user, proxy)); put("id", UUID.randomUUID().toString())
                    put("password", UUID.randomUUID().toString()); put("auth", UUID.randomUUID().toString())
                    put("subId", randomThreeXuiSubId()); put("enable", true)
                    put("limitIp", 0); put("totalGB", 0); put("expiryTime", 0); put("tgId", 0)
                    put("reset", 0); put("comment", "")
                }
                payload.add(mapper.createObjectNode().apply {
                    set<ObjectNode>("client", client)
                    putArray("inboundIds").add(inboundId)
                })
            }
            try {
                val result = post("bulkCreate", payload)
                result.path("skipped").forEach { report -> failures[report.path("email").asText()] = report.path("reason").asText() }
            } catch (error: Exception) {
                chunk.forEach { failures[clientEmail(it, proxy)] = error.message ?: "Batch creation failed" }
            }
        }
        // A timed-out request may still have committed; read canonical credentials before attaching.
        identities = listClients()
        val afterCreate = getThreeXuiInboundSettings(session.copy(client = batchClient), inboundId, mapper)
        val alreadyAttached = afterCreate.path("clients").map { it.path("email").asText() }.toSet()
        users.filter { clientEmail(it, proxy) in identities && clientEmail(it, proxy) !in alreadyAttached }
            .chunked(200).forEach { chunk ->
            val payload = mapper.createObjectNode().apply {
                val emails = putArray("emails")
                chunk.forEach { emails.add(clientEmail(it, proxy)) }
                putArray("inboundIds").add(inboundId)
            }
            try {
                val result = post("bulkAttach", payload)
                val errors = result.path("errors").map { it.asText() }.joinToString("; ")
                if (errors.isNotBlank()) chunk.forEach { failures[clientEmail(it, proxy)] = errors }
            } catch (error: Exception) {
                chunk.forEach { failures[clientEmail(it, proxy)] = error.message ?: "Batch attachment failed" }
            }
        }
        val finalInbound = getThreeXuiInboundById(session.copy(client = batchClient), inboundId, mapper)
        val settings = objectField(finalInbound.get("settings"), mapper) ?: error("Panel returned no inbound settings")
        val attached = settings.path("clients").map { it.path("email").asText() }.toSet()
        return users.associate { user ->
            val email = clientEmail(user, proxy)
            user.id!! to runCatching {
                check(email in attached) { failures[email] ?: "Panel did not attach client $email" }
                buildStoredProxyConfig(server, proxy, user, finalInbound, settings, identities[email])
            }
        }
    }

    // Fully remove the selected client identities, including traffic and every inbound membership.
    fun deleteUserProxiesBatch(serverId: Long, type: ru.ravel.ultunneladminpanel.model.ProxyType,
                               users: List<User>): Map<Long, Result<Boolean>> {
        if (users.isEmpty()) return emptyMap()
        require(type != SSH) { "SSH is managed independently" }
        val server = proxyServerRepository.findById(serverId).orElseThrow()
        val proxies = server.proxies.orEmpty().filter { it.type?.configType == type.configType }
        require(proxies.isNotEmpty()) { "Protocol no longer exists on this server" }
        validateServerConnection(server)
        val mapper = ObjectMapper()
        val login = loginThreeXui(server.host!!, connectionProxy(server, proxies.first()), mapper)
        val session = login.copy(client = login.client.newBuilder()
            .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build())
        val protocol = when (type) { HYSTERIA2 -> "hysteria"; AMNEZIA_WG -> "amneziawg"; TROJAN2 -> "trojan"; else -> type.configType }
        val inbounds = listThreeXuiInbounds(session, mapper)
        val targets = linkedSetOf<Long>()
        proxies.forEach { proxy ->
            val selected = if (proxy.inboundId != null) inbounds.filter { it.path("id").asLong() == proxy.inboundId }
                else if (proxy.proxyPort != null) inbounds.filter { it.path("protocol").asText() == protocol && it.path("port").asLong() == proxy.proxyPort }
                else inbounds.filter { it.path("protocol").asText() == protocol }.takeLast(1)
            require(selected.all { it.path("protocol").asText() == protocol }) { "Configured inbound protocol has changed" }
            selected.forEach { targets.add(it.path("id").asLong()) }
        }
        fun records(): Map<String, JsonNode> {
            val request = Request.Builder().url("${session.baseUrl}/panel/api/clients/list")
                .header("Cookie", session.cookie).get().build()
            val result = readJsonResponse(session.client, request, mapper, "list clients").path("obj")
            require(result.isArray) { "Panel returned an invalid client list" }
            return result.associateBy { it.path("email").asText() }
        }
        val before = records()
        val views = before.values.toList() + inbounds.filter { it.path("id").asLong() in targets }.flatMap { inbound ->
            objectField(inbound.get("settings"), mapper)?.path("clients")?.toList().orEmpty()
        }
        val serverProxyIds = server.proxies.orEmpty().mapNotNull { it.id }.toSet()
        // Stored credentials also find legacy clients whose admin username has since changed.
        val emailsByUser = users.associate { user ->
            val names = linkedSetOf(clientEmail(user, Proxy(type = type)))
            user.proxiesConfigs.filter { it.proxy?.id in serverProxyIds && it.type == type.configType }.forEach { config ->
                val outbound = mapper.readTree(ConfigTemplate.serializeConfig(config, true, "desktop"))
                val uuid = outbound.path("uuid").asText("")
                val privateKey = outbound.path("private_key").asText("")
                val password = if (config is ConfigDataNativeProxy && type == SHADOWTLS) {
                    mapper.readTree(config.auxiliaryOutboundsJson).firstOrNull { it.path("type").asText() == "shadowtls" }
                        ?.path("password")?.asText("").orEmpty()
                } else outbound.path("password").asText("")
                val matches = views.filter { record ->
                    when {
                        uuid.isNotBlank() -> record.path("id").asText() == uuid || record.path("uuid").asText() == uuid
                        privateKey.isNotBlank() -> record.path("privateKey").asText() == privateKey
                        password.isNotBlank() -> record.path("password").asText() == password || record.path("auth").asText() == password
                        else -> false
                    }
                }.map { it.path("email").asText() }.filter { it.isNotBlank() }.distinct()
                val canonical = clientEmail(user, Proxy(type = type))
                if (canonical in matches) names.add(canonical)
                else {
                    check(matches.size <= 1) { "Cannot unambiguously identify the panel client for ${user.name}" }
                    names.addAll(matches)
                }
            }
            user.id!! to names.toSet()
        }
        val emails = emailsByUser.values.flatten().distinct()
        val toDelete = emails.filter { it in before }
        val changed = toDelete.toSet()
        val failures = mutableMapOf<String, String>()
        fun deleteBatch(batch: List<String>) {
            val body = mapper.createObjectNode().apply {
                val array = putArray("emails"); batch.forEach { array.add(it) }
                put("keepTraffic", false)
            }
            try {
                val request = Request.Builder().url("${session.baseUrl}/panel/api/clients/bulkDel")
                    .header("Cookie", session.cookie).header("X-CSRF-Token", session.csrfToken)
                    .post(mapper.writeValueAsBytes(body).toRequestBody("application/json".toMediaType())).build()
                val result = readJsonResponse(session.client, request, mapper, "bulkDel").path("obj")
                result.path("skipped").filter { it.isObject }.forEach { report ->
                    failures[report.path("email").asText()] = report.path("reason").asText()
                }
                val detail = result.path("errors").map { it.asText() }.joinToString("; ")
                if (detail.isNotBlank()) batch.forEach { failures[it] = detail }
            } catch (error: Exception) {
                batch.forEach { failures[it] = error.message ?: "Batch deletion failed" }
            }
        }
        toDelete.chunked(200).forEach(::deleteBatch)
        // Reconcile the actual state, including requests that committed after an HTTP timeout.
        val after = records()
        return users.associate { user ->
            val names = emailsByUser.getValue(user.id!!)
            user.id!! to runCatching {
                names.forEach { email ->
                    check(after[email] == null) {
                        failures[email] ?: "Client $email is still present on the panel"
                    }
                }
                names.any { it in changed }
            }
        }
    }

    @Transactional
    fun deleteConfirmedConfigIds(proxyIds: Set<Long>, configType: String, snapshotIds: Set<Long>): Int {
        if (snapshotIds.isEmpty()) return 0
        val ids = configDataRepository.findAllByProxy_IdInAndType(proxyIds, configType)
            .mapNotNull { it.id }.filter { it in snapshotIds }.toSet()
        if (ids.isEmpty()) return 0
        unlinkConfigsFromUsers(ids)
        deleteConfigChildRows(ids)
        return configDataRepository.deleteAllByConfigIds(ids)
    }

    private fun createAmneziaClient(host: String, proxy: Proxy, user: User, objectMapper: ObjectMapper): ConfigData {

				val protocol = "amneziawg"
				val session = loginThreeXui(host, proxy, objectMapper)
				val inbound = proxy.inboundId?.let { getThreeXuiInboundById(session, it, objectMapper) }
                    ?: selectThreeXuiInbound(session, protocol, proxy.proxyPort, objectMapper)
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
        return buildAmneziaConfig(host, proxy, user, inbound, settings, objectMapper)
    }

    private fun buildAmneziaConfig(host: String, proxy: Proxy, user: User, inbound: JsonNode,
                                  settings: ObjectNode, objectMapper: ObjectMapper): ConfigData {
        val inboundId = inbound.path("id").asLong()
        val port = inbound.path("port").asLong()
        val email = clientEmail(user, proxy)

				val serverSettings = objectField(settings.get("server"), objectMapper)
					?: error("3x-ui AmneziaWG inbound $inboundId has no server block")
				val clientSettings = findThreeXuiSettingsClient(settings, email)
					?: error("3x-ui did not return the AmneziaWG client $email")

				/* A tunnel endpoint is dialled over UDP, so the panel's subdomain is only
				   usable when it resolves to the server itself. */
                val endpoint = proxy.serverIp?.takeIf { it.isNotBlank() } ?: host
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
					/*
					 * These mirror AmneziaWgConfigBuilder.build() field for field: the client
					 * only completes the handshake when it repeats the server's obfuscation
					 * parameters exactly, so nothing here is left to a sing-box-side default.
					 */
					jc = serverSettings.path("jc").asInt(0),
					jmin = serverSettings.path("jmin").asInt(0),
					jmax = serverSettings.path("jmax").asInt(0),
					s1 = serverSettings.path("s1").asInt(0),
					s2 = serverSettings.path("s2").asInt(0),
					s3 = serverSettings.path("s3").asInt(0).takeIf { it > 0 },
					s4 = serverSettings.path("s4").asInt(0).takeIf { it > 0 },
					h1 = serverSettings.path("h1").asText("").takeIf { it.isNotBlank() },
					h2 = serverSettings.path("h2").asText("").takeIf { it.isNotBlank() },
					h3 = serverSettings.path("h3").asText("").takeIf { it.isNotBlank() },
					h4 = serverSettings.path("h4").asText("").takeIf { it.isNotBlank() },
					i1 = serverSettings.path("i1").asText("").takeIf { it.isNotBlank() },
					i2 = serverSettings.path("i2").asText("").takeIf { it.isNotBlank() },
					i3 = serverSettings.path("i3").asText("").takeIf { it.isNotBlank() },
					i4 = serverSettings.path("i4").asText("").takeIf { it.isNotBlank() },
					i5 = serverSettings.path("i5").asText("").takeIf { it.isNotBlank() },
					headerProtectionKey = serverSettings.path("headerProtectionKey").asText("").takeIf { it.isNotBlank() },
					contentPaddingAddition = serverSettings.path("contentPaddingAddition").asText("").takeIf { it.isNotBlank() },
					rekeyAfterTime = serverSettings.path("rekeyAfterTime").asText("").takeIf { it.isNotBlank() },
					rekeyTimeout = serverSettings.path("rekeyTimeout").asText("").takeIf { it.isNotBlank() },
					rejectAfterTime = serverSettings.path("rejectAfterTime").asText("").takeIf { it.isNotBlank() },
					keepaliveTimeout = serverSettings.path("keepaliveTimeout").asText("").takeIf { it.isNotBlank() },
					maxHandshakeAttempts = serverSettings.path("maxHandshakeAttempts").asText("").takeIf { it.isNotBlank() },
					randomTrailers = serverSettings.path("randomTrailers").asBoolean(false).takeIf { it },
					disableCookies = serverSettings.path("disableCookies").asBoolean(false).takeIf { it },
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
    fun getProxyServer(secretKey: String): List<ConfigData> {
        val configs = userRepository.findBySecretKey(secretKey)?.proxiesConfigs ?: return emptyList()
        val tunnels = configs.filter { it is ConfigDataAmneziaWireguard || it is ConfigDataWireguard }
        if (tunnels.isNotEmpty()) {
            val endpoints = proxyServerRepository.findAll().flatMap { server ->
                val address = server.serverIp?.takeIf { it.isNotBlank() } ?: server.host
                server.proxies.orEmpty().mapNotNull { proxy -> proxy.id?.let { it to address } }
            }.toMap()
            tunnels.forEach { config ->
                val address = endpoints[config.proxy?.id]?.takeIf { it.isNotBlank() }
                    ?: config.server?.takeIf { it.isNotBlank() }
                    ?: config.proxy?.serverIp?.takeIf { it.isNotBlank() }
                    ?: error("WireGuard/AmneziaWG server address is missing")
                config.server = address
            }
        }
        return configs.map { it.fillFields() }
    }
}
