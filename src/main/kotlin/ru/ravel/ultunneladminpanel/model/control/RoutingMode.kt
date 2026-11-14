package ru.ravel.ultunneladminpanel.model.control

/**
 * What the client application must do with the traffic of a domain.
 *
 * PROXY  — send it through the tunnel.
 * DIRECT — let it leave the device straight to the internet (the "split" in split routing).
 * BLOCK  — drop it.
 */
enum class RoutingMode {
	PROXY,
	DIRECT,
	BLOCK,
	;

	companion object {
		fun parse(value: String?): RoutingMode? = value
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.uppercase()
			?.let { name -> entries.firstOrNull { it.name == name } }
	}
}
