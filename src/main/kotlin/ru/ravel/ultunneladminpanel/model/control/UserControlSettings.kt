package ru.ravel.ultunneladminpanel.model.control

import jakarta.persistence.*
import ru.ravel.ultunneladminpanel.model.User
import java.time.Instant

/**
 * Per-user state of the control endpoint. Kept apart from [User] so the admin API,
 * which serializes users straight to the admin UI, is not touched by it.
 */
@Entity
@Table(
	name = "user_control_settings",
	uniqueConstraints = [
		UniqueConstraint(name = "uk_user_control_settings_user", columnNames = ["user_id"]),
	],
)
class UserControlSettings(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	var user: User? = null,

	/** What happens to a domain no rule covers. */
	@Enumerated(EnumType.STRING)
	@Column(name = "default_mode", nullable = false, length = 16)
	var defaultMode: RoutingMode = RoutingMode.PROXY,

	/** When off, reported domains are counted and dropped instead of stored. */
	@Column(name = "collect_enabled", nullable = false)
	var collectEnabled: Boolean = true,

	/**
	 * Bumped on every change to the rules or the default mode. The client application
	 * polls this number and only re-downloads the ruleset when it moved.
	 */
	@Column(name = "rules_version", nullable = false)
	var rulesVersion: Long = 0,

	@Column(name = "rules_updated_at", nullable = false)
	var rulesUpdatedAt: Instant = Instant.now(),

	@Column(name = "last_report_at")
	var lastReportAt: Instant? = null,
) {

	fun touchRules() {
		rulesVersion += 1
		rulesUpdatedAt = Instant.now()
	}
}
