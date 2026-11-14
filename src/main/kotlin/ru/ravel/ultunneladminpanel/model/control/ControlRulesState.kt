package ru.ravel.ultunneladminpanel.model.control

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * One row for the whole installation, holding the version of the shared rule pool.
 *
 * A user who did not opt out of the pool routes by everyone's rules, so their
 * client has to notice a change made by somebody else. Their own
 * [UserControlSettings.rulesVersion] cannot show that, hence this counter: it is
 * bumped on every rule change by anybody.
 */
@Entity
@Table(name = "control_rules_state")
class ControlRulesState(

	@Id
	@Column(name = "id")
	var id: Long = SINGLETON_ID,

	@Column(name = "rules_version", nullable = false)
	var rulesVersion: Long = 0,

	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant = Instant.now(),
) {

	companion object {
		const val SINGLETON_ID = 1L
	}
}
