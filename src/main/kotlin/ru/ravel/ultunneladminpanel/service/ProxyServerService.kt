package ru.ravel.ultunneladminpanel.service

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
import ru.ravel.ultunneladminpanel.repository.ProxyRepository
import ru.ravel.ultunneladminpanel.repository.ProxyServerRepository
import java.io.File
import java.util.*


@Service
class ProxyServerService(
	val proxyServerRepository: ProxyServerRepository,
	val proxyRepository: ProxyRepository,
) {

	fun addNewServer(proxyServer: ProxyServer): ProxyServer {
		proxyServer.proxies?.forEach {
			proxyRepository.save(it)
		}
		proxyServerRepository.save(proxyServer)
		return proxyServer
	}


	fun addProxyToServer(url: String, proxy: Proxy): Proxy {
		val proxyServer = proxyServerRepository.findByHost(url).apply {
			proxyRepository.save(proxy)
		}
		proxyServer?.proxies?.add(proxy)
		proxyServer?.let { proxyServerRepository.save(it) }
		return proxy
	}


	fun createUserProxy(url: String, proxy: Proxy, user: User): String {
		when (proxy.type!!) {
			THREEX_UI -> {
				var json = """
					|{
                    |    "username": "${proxy.login}",
                    |    "password": "${proxy.password}"
					|}""".trimMargin()
				var body = json.toRequestBody("application/json".toMediaType())
				var request = Request.Builder()
					.header("Content-Type", "application/json")
					.url("https://${url}:${proxy.port}/login")
					.post(body)
					.build()
				val response = createUnsafeOkHttpClient().newCall(request).execute()
				val cooke = response.headers["Set-Cookie"]
				val uuid = UUID.randomUUID().toString()
				val portFIXME = 26026
				val idFIXME = 10
				json = """{
					"id": ${idFIXME},
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
				return "vless://${uuid}@${url}:${portFIXME}?type=tcp&security=none"
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

}