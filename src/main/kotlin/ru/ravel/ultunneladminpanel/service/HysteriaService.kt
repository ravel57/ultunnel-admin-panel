package ru.ravel.ultunneladminpanel.service

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.component.createUnsafeOkHttpClient
import ru.ravel.ultunneladminpanel.dto.HysteriaUser
import ru.ravel.ultunneladminpanel.dto.Username
import ru.ravel.ultunneladminpanel.model.Proxy
import ru.ravel.ultunneladminpanel.model.User
import java.util.*


@Service
class HysteriaService {

	fun addNewUser(proxy: Proxy, host: String, user: User): HysteriaUser {
		val objectMapper = ObjectMapper()
		val password = Base64.getEncoder().encodeToString(proxy.password?.toByteArray())
		var body = "".toRequestBody()
		val url = if (proxy.useSubDomain!!) {
			"https://${proxy.subdomain}.${host}"
		} else {
			"https://${host}:${proxy.port}"
		}
		var request = Request.Builder()
			.url("${url}/auth/login?password=${password}")
			.header("Connection", "keep-alive")
			.post(body)
			.build()
		val client = createUnsafeOkHttpClient()
		client.newCall(request).execute()
		body = objectMapper.writeValueAsString(Username(user.name!!))
			.toRequestBody("application/json".toMediaType())
		request = Request.Builder()
			.header("Content-Type", "application/json")
			.url("${url}/api/v1/user")
			.header("Connection", "keep-alive")
			.post(body)
			.build()
		val response = client.newCall(request).execute()
		val string = response.body?.string()
		val hysteriaUser = objectMapper.readValue(string, HysteriaUser::class.java)

		return hysteriaUser
	}

}