package ru.ravel.ultunneladminpanel.model.control

import jakarta.persistence.*
import ru.ravel.ultunneladminpanel.model.User
import java.time.Instant

/**
 * One domain the client application reported for one user, collapsed to a single
 * row: every further report of the same name only bumps [hits], [lastSeen] and the
 * observed address list, so a chatty client cannot grow the table without bound.
 */
@Entity
@Table(
	name = "visited_domain",
	uniqueConstraints = [
		UniqueConstraint(name = "uk_visited_domain_user_domain", columnNames = ["user_id", "domain"]),
	],
	indexes = [
		Index(name = "idx_visited_domain_user_last_seen", columnList = "user_id, last_seen"),
		Index(name = "idx_visited_domain_domain", columnList = "domain"),
	],
)
class VisitedDomain(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	var user: User? = null,

	@Column(name = "domain", nullable = false, length = 255)
	var domain: String = "",

	@Column(name = "first_seen", nullable = false)
	var firstSeen: Instant = Instant.now(),

	@Column(name = "last_seen", nullable = false)
	var lastSeen: Instant = Instant.now(),

	@Column(name = "hits", nullable = false)
	var hits: Long = 0,

	/**
	 * Addresses the name resolved to, newest first, comma separated and capped at
	 * [MAX_ADDRESSES]. A plain column rather than a child table: the UI only shows
	 * them as chips, and nothing ever queries by address.
	 */
	@Column(name = "addresses", length = 1024)
	var addresses: String? = null,

	@Column(name = "device_id", length = 128)
	var deviceId: String? = null,
) {

	fun addressList(): List<String> = addresses
		?.split(',')
		?.map { it.trim() }
		?.filter { it.isNotEmpty() }
		.orEmpty()

	fun rememberAddress(address: String?) {
		val value = address?.trim()?.takeIf { it.isNotEmpty() } ?: return
		if (value.length > MAX_ADDRESS_LENGTH) {
			return
		}
		val merged = (listOf(value) + addressList().filter { it != value }).take(MAX_ADDRESSES)
		addresses = merged.joinToString(",")
	}

	companion object {
		const val MAX_ADDRESSES = 12
		const val MAX_ADDRESS_LENGTH = 64
	}
}
