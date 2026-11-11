package ru.ravel.ultunneladminpanel.component

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared client is used against several panels at once. Its jar used to bucket cookies by
 * registrable domain, which handed one panel's session to another panel of the same domain —
 * and because OkHttp sets the Cookie header with header(), that silently replaced the session
 * the caller had put there itself, so the call went out unauthenticated and came back 404.
 */
class UnsafeOkHttpClientTest {

	/* The client is a process-wide singleton, so every test needs hosts of its own. */
	private fun panel(host: String) = "https://$host.ultunnel.ru/".toHttpUrl()

	@Test
	fun `a session cookie never leaves the host that issued it`() {
		val issuer = panel("xui.issuer-test")
		val other = panel("xui.other-test")
		val jar = createUnsafeOkHttpClient().cookieJar
		jar.saveFromResponse(issuer, listOf(Cookie.parse(issuer, "3x-ui=session-of-issuer; Path=/")!!))

		assertEquals(
			listOf("session-of-issuer"),
			jar.loadForRequest("https://xui.issuer-test.ultunnel.ru/panel/api/inbounds/list".toHttpUrl())
				.map { it.value },
		)
		assertTrue(
			jar.loadForRequest("https://xui.other-test.ultunnel.ru/panel/api/inbounds/list".toHttpUrl())
				.none { it.value == "session-of-issuer" },
			"the other panel must not receive the first panel's session",
		)
	}

	@Test
	fun `logging in to a second panel does not drop the first one's session`() {
		val first = panel("xui.first-test")
		val second = panel("xui.second-test")
		val jar = createUnsafeOkHttpClient().cookieJar
		jar.saveFromResponse(first, listOf(Cookie.parse(first, "3x-ui=session-of-first; Path=/")!!))
		jar.saveFromResponse(second, listOf(Cookie.parse(second, "3x-ui=session-of-second; Path=/")!!))

		assertEquals(listOf("session-of-first"), jar.loadForRequest(first).map { it.value })
		assertEquals(listOf("session-of-second"), jar.loadForRequest(second).map { it.value })
	}

}
