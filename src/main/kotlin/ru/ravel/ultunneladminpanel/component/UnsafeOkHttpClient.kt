package ru.ravel.ultunneladminpanel.component

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import okhttp3.Cookie
import okhttp3.HttpUrl

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
			.followRedirects(true)
			.followSslRedirects(true)
			.cookieJar(object : CookieJar {
				private val cookieStore = mutableMapOf<String, MutableList<Cookie>>() // Сохраняем по домену

				override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
					val domain = url.topPrivateDomain() ?: url.host
					cookieStore.getOrPut(domain) { mutableListOf() }.apply {
						removeAll { oldCookie -> cookies.any { it.name == oldCookie.name } }
						addAll(cookies)
					}
				}

				override fun loadForRequest(url: HttpUrl): List<Cookie> {
					val domain = url.topPrivateDomain() ?: url.host
					val cookies = cookieStore[domain] ?: emptyList()
					return cookies
				}
			})
			.build()

	} catch (e: Exception) {
		throw RuntimeException(e)
	}
}