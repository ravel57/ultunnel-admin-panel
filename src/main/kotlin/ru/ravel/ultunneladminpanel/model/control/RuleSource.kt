package ru.ravel.ultunneladminpanel.model.control

/** Where the mode a domain ends up with came from. */
enum class RuleSource {
	/** The user's own rule. It always wins over the pool. */
	OWN,

	/** A rule from the shared pool, i.e. one other users agreed on. */
	SHARED,

	/** Nothing matched, so the user's default mode applies. */
	DEFAULT,
}
