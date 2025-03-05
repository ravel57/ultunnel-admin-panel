package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.megoru.impl.WgEasyAPI
import org.megoru.io.UnsuccessfulHttpException
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.component.createUnsafeOkHttpClient
import ru.ravel.ultunneladminpanel.model.Proxy
import ru.ravel.ultunneladminpanel.model.ProxyServer
import ru.ravel.ultunneladminpanel.model.ProxyType.*
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.config.ConfigData
import ru.ravel.ultunneladminpanel.model.config.ConfigDataHysteria
import ru.ravel.ultunneladminpanel.model.config.ConfigDataSsh
import ru.ravel.ultunneladminpanel.model.config.ConfigDataVless
import ru.ravel.ultunneladminpanel.model.xui.Root
import ru.ravel.ultunneladminpanel.model.xui.ThreeXuiType
import ru.ravel.ultunneladminpanel.repository.ProxyRepository
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import ru.ravel.ultunneladminpanel.repository.UserRepository
import java.io.File
import java.util.*


@Service
class ProxyServerService(
	private val sshService: SshService,
	private val proxyServerRepository: ProxyServerRepository,
	private val proxyRepository: ProxyRepository,
	private val userRepository: UserRepository,
	private val hysteriaService: HysteriaService,
) {

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
				val port = readValue.obj?.last { it.protocol == protocol }?.port
				val id = readValue.obj?.last { it.protocol == protocol }?.id
				val uuid = UUID.randomUUID().toString()
				json = """{
					"id": ${id},
					"settings": "{\"clients\":[{\"id\":\"${uuid}\",\"alterId\":0,\"email\":\"${user.name}\",\"limitIp\":0,\"totalGB\":0,\"expiryTime\":0,\"enable\":true,\"tgId\":\"\",\"subId\":\"\"}]}"
				}"""
				body = json.toRequestBody("application/json".toMediaType())
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("${url}/panel/api/inbounds/addClient")
					.header("Cookie", cooke.toString())
					.post(body)
					.build()
				createUnsafeOkHttpClient().newCall(request).execute()
				return ConfigDataVless(
					type = protocol,
					uuid = uuid,
					server = host,
					serverPort = port!!,
				)
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
				)
			}

			SSH -> {
				val password = sshService.addNewUser(proxy, host, user)
				return ConfigDataSsh(
					server = host,
					serverPort = proxy.proxyPort!!,
					password = password,
					user = user.name!!,
				)
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
		}
	}


	fun getProxyServer(secretKey: String): List<ConfigData> {
		return userRepository.findBySecretKey(secretKey)
			?.proxiesConfigs
			?.map {
				it.fillFields()
			}
			?: emptyList()
	}

}