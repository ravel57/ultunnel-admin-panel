package ru.ravel.ultunneladminpanel.model.control

/**
 * How a rule pattern is matched against a domain. The names follow sing-box
 * ("domain", "domain_suffix", "domain_keyword") so a rule can be handed to the
 * client application without translation.
 */
enum class DomainMatchType {
	/** Exact host: "example.com" matches only "example.com". */
	DOMAIN,

	/** The host itself and everything under it: "example.com" also matches "a.example.com". */
	SUFFIX,

	/** Substring anywhere in the host: "googlevideo" matches "r1---sn.googlevideo.com". */
	KEYWORD,
	;

	fun matches(pattern: String, domain: String): Boolean = when (this) {
		DOMAIN -> domain == pattern
		SUFFIX -> domain == pattern || domain.endsWith(".$pattern")
		KEYWORD -> domain.contains(pattern)
	}

	/**
	 * How tightly a pattern of this kind describes a domain. An exact host always
	 * beats a suffix, a longer suffix beats a shorter one, and a keyword is the last
	 * resort — the same order sing-box itself uses when several rules could match.
	 */
	fun specificity(pattern: String): Int = when (this) {
		DOMAIN -> 1_000_000
		SUFFIX -> 1_000 + pattern.length
		KEYWORD -> pattern.length
	}

	/** The field name this match type takes in a sing-box route rule. */
	fun singBoxField(): String = when (this) {
		DOMAIN -> "domain"
		SUFFIX -> "domain_suffix"
		KEYWORD -> "domain_keyword"
	}

	companion object {
		fun parse(value: String?): DomainMatchType? = value
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.uppercase()
			?.let { name ->
				when (name) {
					"DOMAIN_SUFFIX" -> SUFFIX
					"DOMAIN_KEYWORD" -> KEYWORD
					else -> entries.firstOrNull { it.name == name }
				}
			}
	}
}
