package ru.ravel.ultunneladminpanel.repository.control

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.ravel.ultunneladminpanel.model.control.VisitedDomain
import java.time.Instant

interface VisitedDomainRepository : JpaRepository<VisitedDomain, Long> {

	fun findByUserIdAndDomainIn(userId: Long, domains: Collection<String>): List<VisitedDomain>

	fun countByUserId(userId: Long): Long

	fun countByUserIdAndLastSeenAfter(userId: Long, after: Instant): Long

	/**
	 * [pattern] is a ready LIKE pattern ("%" for everything) rather than a nullable
	 * term: a `:param is null` branch makes Hibernate guess the parameter type, and
	 * PostgreSQL then rejects the query.
	 */
	@Query(
		"""
		select v from VisitedDomain v
		where v.user.id = :userId and lower(v.domain) like :pattern
		order by v.lastSeen desc, v.id desc
		""",
	)
	fun search(@Param("userId") userId: Long, @Param("pattern") pattern: String): List<VisitedDomain>

	@Modifying
	@Query("delete from VisitedDomain v where v.user.id = :userId")
	fun deleteAllByUserId(@Param("userId") userId: Long): Int

	@Modifying
	@Query("delete from VisitedDomain v where v.user.id = :userId and v.domain in :domains")
	fun deleteByUserIdAndDomainIn(
		@Param("userId") userId: Long,
		@Param("domains") domains: Collection<String>,
	): Int

	@Modifying
	@Query("delete from VisitedDomain v where v.lastSeen < :before")
	fun deleteOlderThan(@Param("before") before: Instant): Int

	@Modifying
	@Query("delete from VisitedDomain v where v.id in :ids")
	fun deleteByIdIn(@Param("ids") ids: Collection<Long>): Int

	/** Oldest first, used to trim a user back to the per-user cap. */
	@Query("select v.id from VisitedDomain v where v.user.id = :userId order by v.lastSeen asc, v.id asc")
	fun idsOldestFirst(@Param("userId") userId: Long, pageable: Pageable): List<Long>
}
