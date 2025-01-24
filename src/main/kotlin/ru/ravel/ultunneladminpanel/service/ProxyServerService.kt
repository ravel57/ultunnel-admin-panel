package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.ObjectMapper
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
import ru.ravel.ultunneladminpanel.model.UsersProxy
import ru.ravel.ultunneladminpanel.model.xui.Root
import ru.ravel.ultunneladminpanel.repository.ProxyRepository
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import ru.ravel.ultunneladminpanel.repository.UserRepository
import java.io.File
import java.util.*


@Service
class ProxyServerService(
	val proxyServerRepository: ProxyServerRepository,
	val proxyRepository: ProxyRepository,
	val userRepository: UserRepository,
) {

	fun addNewServer(proxyServer: ProxyServer): ProxyServer {
		proxyServer.proxies?.forEach {
			proxyRepository.save(it)
		}
		proxyServerRepository.save(proxyServer)
		return proxyServer
	}


	fun addProxyToServer(proxyServerId: Long, proxy: Proxy): Proxy {
		val proxyServer = proxyServerRepository.findById(proxyServerId).orElseThrow().apply {
			proxyRepository.save(proxy)
		}
		proxyServer?.proxies?.add(proxy)
		proxyServer?.let { proxyServerRepository.save(it) }
		return proxy
	}


	fun createUserProxy(url: String, proxy: Proxy, user: User): String {
		when (proxy.type!!) {
			THREEX_UI -> {
				var json = "{\"username\":\"${proxy.login}\",\"password\":\"${proxy.password}\"}"
				var body = json.toRequestBody("application/json".toMediaType())
				var request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("https://${url}:${proxy.port}/login")
					.post(body)
					.build()
				var response = createUnsafeOkHttpClient().newCall(request).execute()
				val cooke = response.headers["Set-Cookie"]
				val uuid = UUID.randomUUID().toString()
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("https://${url}:${proxy.port}/panel/api/inbounds/list")
					.header("Cookie", cooke.toString())
					.get()
					.build()
				response = createUnsafeOkHttpClient().newCall(request).execute()
				val readValue = ObjectMapper().readValue(response.body?.string(), Root::class.java)
				val port = readValue.obj?.last { it.protocol == "vless" }?.port
				var id = readValue.obj?.last { it.protocol == "vless" }?.id
				json = """{
					"id": ${id},
					"settings": "{\"clients\":[{\"id\":\"$uuid\",\"alterId\":0,\"email\":\"${user.name}\",\"limitIp\":0,\"totalGB\":0,\"expiryTime\":0,\"enable\":true,\"tgId\":\"\",\"subId\":\"\"}]}"
				}"""
				body = json.toRequestBody("application/json".toMediaType())
				request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("https://${url}:${proxy.port}/panel/api/inbounds/addClient")
					.header("Cookie", cooke.toString())
					.post(body)
					.build()
				createUnsafeOkHttpClient().newCall(request).execute()
				return "vless://${uuid}@${url}:${port}?type=tcp&security=none"
			}

			HYSTERIA -> {
				return "false"
			}

			SSH -> {
				return "false"
			}

			WIREGUARD, AMNEZIA_WIREGUARD -> {
				val api = WgEasyAPI.Builder()
					.password(proxy.password)
					.host("https://${url}:${proxy.port}")
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
				return config
			}
		}
	}

	fun getProxyServer(secretKey: String): List<UsersProxy> {
		return userRepository.findBySecretKey(secretKey)?.proxies ?: emptyList()
	}

}