package ru.ravel.ultunneladminpanel.service

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.component.createUnsafeOkHttpClient
import ru.ravel.ultunneladminpanel.repository.ProxyRepository

@Service
class ThreeXUiService (
	val proxyRepository: ProxyRepository
) {
	fun threeXUiLogin(url: String)/*: Boolean*/ {
//		val proxy = proxyRepository.findProxyByUrl(url)
//		val mediaType = "text/plain".toMediaType()
//		val body = "username=${proxy?.login}&password=${proxy?.password}".toRequestBody(mediaType)
//		val request = Request.Builder()
//			.url("https://vpn.ravel57.ru:2053/login")
//			.post(body)
//			.build()
//		val response = createUnsafeOkHttpClient().newCall(request).execute()
//		return response.isSuccessful
	}
}