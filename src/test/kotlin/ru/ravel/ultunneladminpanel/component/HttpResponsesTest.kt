package ru.ravel.ultunneladminpanel.component

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSource
import okio.buffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 3x-ui answers a request its CSRF filter rejects with `403`, `Content-Length: 0` and
 * `Content-Encoding: gzip` at the same time. Decoding that empty gzip stream throws, and the
 * throw used to escape instead of the 403 that explains what the panel actually refused.
 */
class HttpResponsesTest {

	private fun response(code: Int, body: okhttp3.ResponseBody): Response {
		return Response.Builder()
			.request(Request.Builder().url("https://panel.example/panel/api/clients/add").build())
			.protocol(Protocol.HTTP_1_1)
			.code(code)
			.message("Forbidden")
			.body(body)
			.build()
	}

	@Test
	fun `an empty body announced as gzip reads as empty instead of throwing`() {
		val undecodable = GzipSource(Buffer()).buffer().asResponseBody("application/json".toMediaType())

		assertEquals("", response(403, undecodable).readBodyOrEmpty())
	}

	@Test
	fun `a readable body is returned unchanged`() {
		val body = """{"success":false,"msg":"nope"}""".toResponseBody("application/json".toMediaType())

		assertEquals("""{"success":false,"msg":"nope"}""", response(200, body).readBodyOrEmpty())
	}

}
