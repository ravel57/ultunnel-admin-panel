package ru.ravel.ultunneladminpanel.component

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

fun createUnsafeOkHttpClient(): OkHttpClient {
	try {
		val trustAllCerts = arrayOf<TrustManager>(
			object : X509TrustManager {
				override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
				override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
				override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
			}
		)

		val sslContext = SSLContext.getInstance("SSL")
		sslContext.init(null, trustAllCerts, SecureRandom())
		val sslSocketFactory = sslContext.socketFactory
		return OkHttpClient.Builder()
			.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
			.hostnameVerifier { _, _ -> true }
			.build()
	} catch (e: Exception) {
		throw RuntimeException(e)
	}
}