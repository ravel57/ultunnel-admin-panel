package ru.ravel.ultunneladminpanel.service.control

import java.net.IDN

/**
 * Brings whatever the client application reports to one canonical host name, so
 * "HTTPS://Example.COM:443/path", "example.com." and "example.com" are one row.
 */
object DomainNormalizer {

	private const val MAX_LENGTH = 253
	private const val MAX_LABEL_LENGTH = 63
	private val LABEL = Regex("^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?$")
	private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

	/** The canonical host, or null when the value is not a usable domain name. */
	fun normalize(raw: String?): String? {
		var value = raw?.trim()?.lowercase() ?: return null
		if (value.isEmpty()) {
			return null
		}

		value = value.substringAfter("://")
		value = value.substringAfter('@')
		value = value.substringBefore('/')
		value = value.substringBefore('?')
		value = value.substringBefore('#')

		// IPv6 literals arrive bracketed; a bare colon is a port.
		if (value.startsWith("[")) {
			return null
		}
		value = value.substringBefore(':')
		value = value.trimEnd('.')

		if (value.isEmpty() || value.length > MAX_LENGTH) {
			return null
		}
		// The endpoint collects names, not addresses: an address carries no routing
		// decision a domain rule could be hung on.
		if (IPV4.matches(value)) {
			return null
		}

		val ascii = try {
			IDN.toASCII(value, IDN.ALLOW_UNASSIGNED)
		} catch (ignored: IllegalArgumentException) {
			return null
		}

		val labels = ascii.split('.')
		if (labels.size < 2) {
			return null
		}
		val valid = labels.all { it.isNotEmpty() && it.length <= MAX_LABEL_LENGTH && LABEL.matches(it) }
		return if (valid) ascii else null
	}

	/**
	 * A rule pattern. Same shape as a domain for exact and suffix matching; a keyword
	 * is free text, so it only gets trimmed, lowercased and length-checked.
	 */
	fun normalizePattern(raw: String?, keyword: Boolean): String? {
		if (!keyword) {
			return normalize(raw)
		}
		val value = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
		return value.takeIf { it.length <= MAX_LENGTH }
	}
}
