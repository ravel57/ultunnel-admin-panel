package ru.ravel.ultunneladminpanel.dto.control

import org.junit.jupiter.api.Test
import ru.ravel.ultunneladminpanel.config.JacksonConfig
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A client is free to spell an absent field out as null, and some do. Every field
 * of these request classes has a default, which makes Kotlin emit a no-arg
 * constructor as well; Jackson picks that one and fills the fields by reflection,
 * so the defaults never run. A non-null `List` property would then hold a null and
 * the first call on it would throw — reaching the client as a 500, or, before
 * /error was opened up, as a redirect to the login form.
 */
class ControlRequestDeserializationTest {

	private val mapper = JacksonConfig().objectMapper()

	@Test
	fun `a visit report survives explicit nulls`() {
		val request = mapper.readValue(
			"""{"deviceId": null, "domains": null, "entries": null}""",
			VisitReportRequest::class.java,
		)
		assertNull(request.deviceId)
		assertTrue(request.domains.orEmpty().isEmpty())
		assertTrue(request.entries.orEmpty().isEmpty())
	}

	@Test
	fun `a visit report keeps what was actually sent`() {
		val request = mapper.readValue(
			"""{"deviceId": "iphone", "domains": ["ya.ru"], "entries": null}""",
			VisitReportRequest::class.java,
		)
		assertEquals("iphone", request.deviceId)
		assertEquals(listOf("ya.ru"), request.domains)
		assertTrue(request.entries.orEmpty().isEmpty())
	}

	@Test
	fun `an empty body is a usable request`() {
		val request = mapper.readValue("{}", VisitReportRequest::class.java)
		assertTrue(request.domains.orEmpty().isEmpty())
		assertTrue(request.entries.orEmpty().isEmpty())
	}

	@Test
	fun `a rule upsert survives explicit nulls`() {
		val request = mapper.readValue(
			"""{"patterns": ["ya.ru"], "mode": "DIRECT", "matchType": null, "enabled": null, "comment": null}""",
			UpsertRulesRequest::class.java,
		)
		assertEquals(listOf("ya.ru"), request.patterns)
		assertNull(request.matchType)
		assertNull(request.enabled)
	}

	@Test
	fun `a rule deletion survives explicit nulls`() {
		val request = mapper.readValue("""{"ids": null, "patterns": null}""", DeleteRulesRequest::class.java)
		assertTrue(request.ids.orEmpty().isEmpty())
		assertTrue(request.patterns.orEmpty().isEmpty())
	}
}
