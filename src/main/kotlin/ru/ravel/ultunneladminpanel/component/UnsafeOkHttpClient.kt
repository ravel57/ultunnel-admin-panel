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
		/*
		 * Keyed by the exact host, not by the registrable domain: two panels under one
		 * domain (xui.a.example.com and xui.b.example.com) issue session cookies of the
		 * same name, and a shared bucket hands one panel's session to the other. That is
		 * not a stale-cookie nuisance — OkHttp's BridgeInterceptor sets the Cookie header
		 * with header(), so whatever this jar returns REPLACES a Cookie header the caller
		 * set itself, and the call goes out unauthenticated.
		 */
		.cookieJar(object : CookieJar {
			@Synchronized
			override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
				cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
					removeAll { stored ->
						cookies.any { received -> received.name == stored.name }
					}
					addAll(cookies)
				}
			}

			@Synchronized
			override fun loadForRequest(url: HttpUrl): List<Cookie> {
				val stored = cookieStore[url.host] ?: return emptyList()
				val now = System.currentTimeMillis()
				stored.removeAll { it.expiresAt < now }
				return stored.filter { it.matches(url) }
			}
		})
		.build()
}

fun createUnsafeOkHttpClient(): OkHttpClient = unsafeOkHttpClient