package ru.ravel.ultunneladminpanel.repository.control

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.ravel.ultunneladminpanel.model.control.ControlRulesState
import java.time.Instant

interface ControlRulesStateRepository : JpaRepository<ControlRulesState, Long> {

	/**
	 * Increments in the database rather than read-modify-write in Kotlin: two users
	 * changing a rule at the same moment would otherwise lose one bump, and a client
	 * polling the version would never learn about that change.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(
		"""
		update ControlRulesState s
		set s.rulesVersion = s.rulesVersion + 1, s.updatedAt = :now
		where s.id = :id
		""",
	)
	fun bump(@Param("id") id: Long, @Param("now") now: Instant): Int
}
