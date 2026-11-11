package ru.ravel.ultunneladminpanel.component

import okhttp3.Response

/**
 * The response body, or "" when it cannot be read.
 *
 * 3x-ui aborts a request its CSRF filter rejects with `403` and `Content-Length: 0`, while the
 * gzip filter in front of it has already announced `Content-Encoding: gzip`. OkHttp decodes
 * that transparently, and decoding an empty stream throws EOFException out of
 * [okhttp3.ResponseBody.string] — burying the status code that explains the refusal under a
 * stack trace about a gzip header. The status is worth more than the body here.
 */
fun Response.readBodyOrEmpty(): String {
	return runCatching { body?.string().orEmpty() }.getOrDefault("")
}
