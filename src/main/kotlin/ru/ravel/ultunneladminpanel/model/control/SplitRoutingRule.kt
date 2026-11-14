package ru.ravel.ultunneladminpanel.model.control

import jakarta.persistence.*
import ru.ravel.ultunneladminpanel.model.User
import java.time.Instant

/**
 * A user's decision about one domain pattern. Everything the user did not decide
 * about falls back to [UserControlSettings.defaultMode].
 */
@Entity
@Table(
	name = "split_routing_rule",
	uniqueConstraints = [
		UniqueConstraint(
			name = "uk_split_routing_rule_user_pattern",
			columnNames = ["user_id", "pattern", "match_type"],
		),
	],
	indexes = [
		Index(name = "idx_split_routing_rule_user", columnList = "user_id"),
	],
)
class SplitRoutingRule(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	var user: User? = null,

	@Column(name = "pattern", nullable = false, length = 255)
	var pattern: String = "",

	@Enumerated(EnumType.STRING)
	@Column(name = "match_type", nullable = false, length = 16)
	var matchType: DomainMatchType = DomainMatchType.SUFFIX,

	@Enumerated(EnumType.STRING)
	@Column(name = "mode", nullable = false, length = 16)
	var mode: RoutingMode = RoutingMode.DIRECT,

	/** A disabled rule is kept but ignored, so a user can park a rule without losing it. */
	@Column(name = "enabled", nullable = false)
	var enabled: Boolean = true,

	@Column(name = "comment", length = 512)
	var comment: String? = null,

	@Column(name = "created_at", nullable = false)
	var createdAt: Instant = Instant.now(),

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
) {

	fun matches(domain: String): Boolean = matchType.matches(pattern, domain)

	fun specificity(): Int = matchType.specificity(pattern)
}
