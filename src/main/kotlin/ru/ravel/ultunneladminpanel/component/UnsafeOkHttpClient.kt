package ru.ravel.ultunneladminpanel.component

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private val unsafeOkHttpClient: OkHttpClient by lazy {
	val trustManager = object : X509TrustManager {
		override fun checkClientTrusted(
			chain: Array<out X509Certificate>?,
			authType: String?
		) = Unit

		override fun checkServerTrusted(
			chain: Array<out X509Certificate>?,
			authType: String?
		) = Unit

		override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
	}

	val sslContext = SSLContext.getInstance("TLS")
	sslContext.init(
		null,
		arrayOf<TrustManager>(trustManager),
		SecureRandom()
	)

	val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

	OkHttpClient.Builder()
		.sslSocketFactory(sslContext.socketFactory, trustManager)
		.hostnameVerifier { _, _ -> true }
		.connectTimeout(30, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.writeTimeout(30, TimeUnit.SECONDS)
		.callTimeout(90, TimeUnit.SECONDS)
		.followRedirects(true)
		.followSslRedirects(true)
		.cookieJar(object : CookieJar {
			override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
				val domain = url.topPrivateDomain() ?: url.host
				cookieStore.getOrPut(domain) { mutableListOf() }.apply {
					removeAll { stored ->
						cookies.any { received -> received.name == stored.name }
					}
					addAll(cookies)
				}
			}

			override fun loadForRequest(url: HttpUrl): List<Cookie> {
				val domain = url.topPrivateDomain() ?: url.host
				return cookieStore[domain].orEmpty()
			}
		})
		.build()
}

fun createUnsafeOkHttpClient(): OkHttpClient = unsafeOkHttpClient