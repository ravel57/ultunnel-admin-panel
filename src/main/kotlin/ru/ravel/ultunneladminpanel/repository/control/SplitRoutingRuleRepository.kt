package ru.ravel.ultunneladminpanel.repository.control

import org.springframework.data.jpa.repository.JpaRepository
import ru.ravel.ultunneladminpanel.model.control.DomainMatchType
import ru.ravel.ultunneladminpanel.model.control.SplitRoutingRule

interface SplitRoutingRuleRepository : JpaRepository<SplitRoutingRule, Long> {

	fun findAllByUserIdOrderByPatternAsc(userId: Long): List<SplitRoutingRule>

	fun findByUserIdAndPatternAndMatchType(
		userId: Long,
		pattern: String,
		matchType: DomainMatchType,
	): SplitRoutingRule?

	fun findAllByUserIdAndIdIn(userId: Long, ids: Collection<Long>): List<SplitRoutingRule>

	fun findAllByUserIdAndPatternIn(userId: Long, patterns: Collection<String>): List<SplitRoutingRule>

	fun countByUserId(userId: Long): Long

	/** Everyone's live rules: the raw material of the shared pool. */
	fun findAllByEnabledTrue(): List<SplitRoutingRule>
}
