package ru.ravel.ultunneladminpanel.service.control

import org.junit.jupiter.api.Test
import ru.ravel.ultunneladminpanel.model.control.DomainMatchType
import ru.ravel.ultunneladminpanel.model.control.SplitRoutingRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainNormalizerTest {

	@Test
	fun `collapses the shapes a client may report the same host in`() {
		val expected = "example.com"
		listOf(
			"example.com",
			"EXAMPLE.COM",
			"  example.com  ",
			"example.com.",
			"example.com:443",
			"https://example.com/path?q=1",
			"http://user:pass@example.com/",
		).forEach { raw ->
			assertEquals(expected, DomainNormalizer.normalize(raw), "normalize($raw)")
		}
	}

	@Test
	fun `turns a unicode name into punycode`() {
		assertEquals("xn--80aswg.xn--p1ai", DomainNormalizer.normalize("сайт.рф"))
	}

	@Test
	fun `rejects what is not a usable domain name`() {
		listOf(null, "", "   ", "localhost", "192.168.1.10", "10.0.0.1:53", "[2001:db8::1]", "-bad-.com", "a..b")
			.forEach { raw -> assertNull(DomainNormalizer.normalize(raw), "normalize($raw)") }
	}

	@Test
	fun `keeps a keyword pattern as free text`() {
		assertEquals("googlevideo", DomainNormalizer.normalizePattern(" GoogleVideo ", keyword = true))
		// The same value is not a domain, so it is refused for the other match types.
		assertNull(DomainNormalizer.normalizePattern("googlevideo", keyword = false))
	}
}

class SplitRoutingRuleMatchingTest {

	private fun rule(pattern: String, type: DomainMatchType) =
		SplitRoutingRule(pattern = pattern, matchType = type)

	@Test
	fun `exact host matches only itself`() {
		val exact = rule("example.com", DomainMatchType.DOMAIN)
		assertTrue(exact.matches("example.com"))
		assertFalse(exact.matches("a.example.com"))
	}

	@Test
	fun `suffix matches the host and everything under it but not a lookalike`() {
		val suffix = rule("example.com", DomainMatchType.SUFFIX)
		assertTrue(suffix.matches("example.com"))
		assertTrue(suffix.matches("a.b.example.com"))
		assertFalse(suffix.matches("notexample.com"))
	}

	@Test
	fun `keyword matches anywhere in the host`() {
		assertTrue(rule("googlevideo", DomainMatchType.KEYWORD).matches("r1---sn-x.googlevideo.com"))
	}

	@Test
	fun `the tighter rule wins`() {
		val exact = rule("a.example.com", DomainMatchType.DOMAIN)
		val longSuffix = rule("sub.example.com", DomainMatchType.SUFFIX)
		val shortSuffix = rule("example.com", DomainMatchType.SUFFIX)
		val keyword = rule("example", DomainMatchType.KEYWORD)

		assertTrue(exact.specificity() > longSuffix.specificity())
		assertTrue(longSuffix.specificity() > shortSuffix.specificity())
		assertTrue(shortSuffix.specificity() > keyword.specificity())
	}
}
