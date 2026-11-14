package ru.ravel.ultunneladminpanel.service.control

import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.dto.control.*
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.control.*
import ru.ravel.ultunneladminpanel.repository.control.ControlRulesStateRepository
import ru.ravel.ultunneladminpanel.repository.control.SplitRoutingRuleRepository
import ru.ravel.ultunneladminpanel.repository.control.UserControlSettingsRepository
import ru.ravel.ultunneladminpanel.repository.control.VisitedDomainRepository
import java.time.Duration
import java.time.Instant

/**
 * Everything behind control.ultunnel.ru: it takes the domains a client application
 * reports, keeps the user's routing decisions, and hands those decisions back in a
 * shape the client can apply.
 *
 * Two things are deliberately scoped differently:
 *
 * * **Collected domains are private.** Everything that reads [VisitedDomain] is
 *   filtered by user id — one subscriber must never learn what another one browses.
 * * **Rules are pooled.** The routing a client applies is built from everyone's
 *   enabled rules, so a fresh user inherits a list other people already curated.
 *   A client that does not want that passes `onlyMine = true`. That flag belongs
 *   to the client application and is never stored here — every endpoint takes it
 *   as a query parameter and defaults to the pooled behaviour.
 */
@Service
class SplitRoutingService(
	private val visitedDomainRepository: VisitedDomainRepository,
	private val ruleRepository: SplitRoutingRuleRepository,
	private val settingsRepository: UserControlSettingsRepository,
	private val rulesStateRepository: ControlRulesStateRepository,
	@Value("\${app.control.max-domains-per-report:2000}") private val maxDomainsPerReport: Int,
	@Value("\${app.control.max-domains-per-user:20000}") private val maxDomainsPerUser: Int,
	@Value("\${app.control.retention-days:30}") private val retentionDays: Long,
	@Value("\${app.control.max-rules-per-user:5000}") private val maxRulesPerUser: Int,
) {

	private val logger: Logger = LoggerFactory.getLogger(SplitRoutingService::class.java)

	/* ------------------------------------------------------------- settings */

	@Transactional
	fun settingsOf(user: User): UserControlSettings {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		return settingsRepository.findByUserId(userId)
			?: settingsRepository.save(UserControlSettings(user = user))
	}

	@Transactional
	fun settingsView(user: User, onlyMine: Boolean): ControlSettingsView = settingsOf(user).toView(onlyMine)

	@Transactional
	fun updateSettings(user: User, request: UpdateControlSettingsRequest, onlyMine: Boolean): ControlSettingsView {
		val settings = settingsOf(user)
		var routingChanged = false
		request.defaultMode?.let {
			routingChanged = routingChanged || it != settings.defaultMode
			settings.defaultMode = it
		}
		request.collectEnabled?.let { settings.collectEnabled = it }
		if (routingChanged) {
			settings.touchRules()
		}
		return settingsRepository.save(settings).toView(onlyMine)
	}

	/* ------------------------------------------------------------ reporting */

	@Transactional
	fun report(user: User, request: VisitReportRequest, onlyMine: Boolean): VisitReportResponse {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)

		val raw = request.entries.orEmpty().map { it.domain to it } +
			request.domains.orEmpty().map { it to VisitEntryRequest(domain = it) }
		require(raw.size <= maxDomainsPerReport) {
			"Too many domains in one report: ${raw.size}, the limit is $maxDomainsPerReport"
		}

		// Collapse the batch first: a client that reports the same name forty times in
		// one call must cost one row update, not forty.
		val collapsed = LinkedHashMap<String, MutableList<VisitEntryRequest>>()
		var rejected = 0
		raw.forEach { (candidate, entry) ->
			val domain = DomainNormalizer.normalize(candidate)
			if (domain == null) {
				rejected += 1
			} else {
				collapsed.getOrPut(domain) { mutableListOf() }.add(entry)
			}
		}

		settings.lastReportAt = Instant.now()
		settingsRepository.save(settings)

		if (!settings.collectEnabled || collapsed.isEmpty()) {
			return VisitReportResponse(
				accepted = collapsed.size,
				rejected = rejected,
				stored = false,
				rulesVersion = effectiveVersion(settings, onlyMine),
			)
		}

		val now = Instant.now()
		val deviceId = request.deviceId?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
		val existing = visitedDomainRepository
			.findByUserIdAndDomainIn(userId, collapsed.keys)
			.associateBy { it.domain }

		val toSave = collapsed.map { (domain, entries) ->
			val hits = entries.sumOf { it.hits?.coerceAtLeast(0) ?: 1L }.coerceAtLeast(1L)
			val seenAt = entries.mapNotNull { it.at }.maxOrNull() ?: now
			val row = existing[domain] ?: VisitedDomain(
				user = user,
				domain = domain,
				firstSeen = seenAt,
				lastSeen = seenAt,
				hits = 0,
			)
			row.hits += hits
			if (seenAt.isAfter(row.lastSeen)) {
				row.lastSeen = seenAt
			}
			if (seenAt.isBefore(row.firstSeen)) {
				row.firstSeen = seenAt
			}
			deviceId?.let { row.deviceId = it }
			entries.forEach { row.rememberAddress(it.address) }
			row
		}
		visitedDomainRepository.saveAll(toSave)
		trimToCap(userId)

		return VisitReportResponse(
			accepted = collapsed.size,
			rejected = rejected,
			stored = true,
			rulesVersion = effectiveVersion(settings, onlyMine),
		)
	}

	/** Keeps the per-user history bounded by dropping the least recently seen names. */
	private fun trimToCap(userId: Long) {
		val total = visitedDomainRepository.countByUserId(userId)
		val excess = (total - maxDomainsPerUser).toInt()
		if (excess <= 0) {
			return
		}
		val ids = visitedDomainRepository.idsOldestFirst(userId, PageRequest.of(0, excess))
		if (ids.isNotEmpty()) {
			visitedDomainRepository.deleteByIdIn(ids)
		}
	}

	/* -------------------------------------------------------------- reading */

	/**
	 * The user's **own** collected domains, never anybody else's. The mode shown is
	 * the one that will actually be applied, pool included, so the page does not
	 * promise something the client will not do.
	 */
	@Transactional
	fun domains(
		user: User,
		find: String?,
		modeFilter: RoutingMode?,
		onlyRuled: Boolean?,
		limit: Int,
		offset: Int,
		onlyMine: Boolean,
	): VisitedDomainPage {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)
		val routing = resolveRouting(userId, onlyMine)

		val term = find?.trim()?.lowercase().orEmpty()
		val pattern = if (term.isEmpty()) {
			"%"
		} else {
			"%${term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
		}

		val matched = visitedDomainRepository.search(userId, pattern)
			.map { it to routing.bestFor(it.domain) }
			.filter { (_, resolved) ->
				val mode = resolved?.mode ?: settings.defaultMode
				(modeFilter == null || mode == modeFilter) &&
					(onlyRuled == null || onlyRuled == (resolved?.ownRule != null))
			}

		val safeLimit = limit.coerceIn(1, 1000)
		val safeOffset = offset.coerceAtLeast(0)
		val page = matched.drop(safeOffset).take(safeLimit).map { (visited, resolved) ->
			val own = resolved?.ownRule
			VisitedDomainView(
				domain = visited.domain,
				addresses = visited.addressList(),
				hits = visited.hits,
				firstSeen = visited.firstSeen,
				lastSeen = visited.lastSeen,
				deviceId = visited.deviceId,
				mode = resolved?.mode ?: settings.defaultMode,
				ruleSource = resolved?.source ?: RuleSource.DEFAULT,
				hasRule = own != null,
				ruleId = own?.id,
				rulePattern = own?.pattern,
				ruleMatchType = own?.matchType,
			)
		}
		return VisitedDomainPage(total = matched.size, offset = safeOffset, limit = safeLimit, items = page)
	}

	@Transactional
	fun forgetDomains(user: User, domains: List<String>): Int {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val normalized = domains.mapNotNull { DomainNormalizer.normalize(it) }.distinct()
		if (normalized.isEmpty()) {
			return 0
		}
		return visitedDomainRepository.deleteByUserIdAndDomainIn(userId, normalized)
	}

	@Transactional
	fun forgetAllDomains(user: User): Int {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		return visitedDomainRepository.deleteAllByUserId(userId)
	}

	/* ---------------------------------------------------------------- rules */

	/** Only the user's own rules: these are the ones they can edit. */
	@Transactional
	fun rules(user: User, onlyMine: Boolean): RulesResponse {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)
		return RulesResponse(
			rulesVersion = effectiveVersion(settings, onlyMine),
			defaultMode = settings.defaultMode,
			rules = ruleRepository.findAllByUserIdOrderByPatternAsc(userId).map { it.toView() },
		)
	}

	/**
	 * Upsert, or — when [UpsertRulesRequest.mode] is null — drop the rules for those
	 * patterns so the domains fall back to the pool, and then to the default mode.
	 */
	@Transactional
	fun upsertRules(user: User, request: UpsertRulesRequest, onlyMine: Boolean): RulesChangeResponse {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)
		val matchType = request.matchType ?: DomainMatchType.SUFFIX
		val keyword = matchType == DomainMatchType.KEYWORD
		val patterns = request.patterns.orEmpty()
			.mapNotNull { DomainNormalizer.normalizePattern(it, keyword) }
			.distinct()
		require(patterns.isNotEmpty()) { "No usable pattern in the request" }

		if (request.mode == null) {
			val removed = removeRules(userId, patterns)
			if (removed > 0) {
				touchRules(settings)
			}
			return RulesChangeResponse(0, 0, removed, effectiveVersion(settings, onlyMine))
		}

		val existing = ruleRepository.findAllByUserIdAndPatternIn(userId, patterns)
			.filter { it.matchType == matchType }
			.associateBy { it.pattern }

		val newPatterns = patterns.count { it !in existing }
		require(ruleRepository.countByUserId(userId) + newPatterns <= maxRulesPerUser) {
			"Rule limit reached: a user may keep at most $maxRulesPerUser rules"
		}

		var created = 0
		var updated = 0
		val now = Instant.now()
		val saved = patterns.map { pattern ->
			val rule = existing[pattern]
			if (rule == null) {
				created += 1
				SplitRoutingRule(
					user = user,
					pattern = pattern,
					matchType = matchType,
					mode = request.mode,
					enabled = request.enabled ?: true,
					comment = request.comment?.take(512),
					createdAt = now,
					updatedAt = now,
				)
			} else {
				updated += 1
				rule.mode = request.mode
				rule.enabled = request.enabled ?: true
				request.comment?.let { rule.comment = it.take(512) }
				rule.updatedAt = now
				rule
			}
		}
		ruleRepository.saveAll(saved)
		touchRules(settings)
		return RulesChangeResponse(created, updated, 0, effectiveVersion(settings, onlyMine))
	}

	@Transactional
	fun deleteRules(user: User, request: DeleteRulesRequest, onlyMine: Boolean): RulesChangeResponse {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)

		val requestedIds = request.ids.orEmpty()
		val requestedPatterns = request.patterns.orEmpty()
		val byId = if (requestedIds.isEmpty()) {
			emptyList()
		} else {
			ruleRepository.findAllByUserIdAndIdIn(userId, requestedIds)
		}
		val byPattern = if (requestedPatterns.isEmpty()) {
			emptyList()
		} else {
			val patterns = requestedPatterns.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
			val normalized = (patterns + patterns.mapNotNull { DomainNormalizer.normalize(it) }).distinct()
			if (normalized.isEmpty()) emptyList() else ruleRepository.findAllByUserIdAndPatternIn(userId, normalized)
		}

		val doomed = (byId + byPattern).distinctBy { it.id }
		if (doomed.isEmpty()) {
			return RulesChangeResponse(0, 0, 0, effectiveVersion(settings, onlyMine))
		}
		ruleRepository.deleteAll(doomed)
		touchRules(settings)
		return RulesChangeResponse(0, 0, doomed.size, effectiveVersion(settings, onlyMine))
	}

	private fun removeRules(userId: Long, patterns: List<String>): Int {
		val doomed = ruleRepository.findAllByUserIdAndPatternIn(userId, patterns)
		if (doomed.isEmpty()) {
			return 0
		}
		ruleRepository.deleteAll(doomed)
		return doomed.size
	}

	/* -------------------------------------------------- client-facing output */

	/**
	 * The ruleset as a sing-box `route` fragment.
	 *
	 * Own rules are emitted before the pooled ones. sing-box takes the first rule
	 * that matches, so that ordering is what makes a user's own decision beat the
	 * pool — the same precedence [ResolvedRouting.bestFor] applies on the server.
	 */
	@Transactional
	fun singBoxRouting(user: User, onlyMine: Boolean): SingBoxRoutingResponse {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)
		val routing = resolveRouting(userId, onlyMine)

		val fragment = listOf(routing.own, routing.shared).flatMap { group ->
			DomainMatchType.entries.flatMap { matchType ->
				group.filter { it.matchType == matchType }
					.groupBy { it.mode }
					.toSortedMap()
					.map { (mode, rules) ->
						mapOf<String, Any>(
							matchType.singBoxField() to rules.map { it.pattern }.sorted(),
							"outbound" to mode.toOutbound(),
						)
					}
			}
		}

		return SingBoxRoutingResponse(
			rulesVersion = effectiveVersion(settings, onlyMine),
			onlyMyDomains = onlyMine,
			final = settings.defaultMode.toOutbound(),
			rules = fragment,
		)
	}

	/** One pattern per line, "mode matchType pattern", for clients that do not speak sing-box. */
	@Transactional
	fun plainRuleList(user: User, onlyMine: Boolean): String {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)
		val routing = resolveRouting(userId, onlyMine)

		val header = listOf(
			"# ultunnel split routing",
			"# version: ${effectiveVersion(settings, onlyMine)}",
			"# default: ${settings.defaultMode}",
			"# onlyMyDomains: $onlyMine",
		)
		val body = (routing.own + routing.shared)
			.sortedWith(compareBy({ it.source }, { it.matchType }, { it.pattern }))
			.map { "${it.mode} ${it.matchType} ${it.pattern}" }
		return (header + body).joinToString("\n", postfix = "\n")
	}

	@Transactional
	fun routingVersion(user: User, onlyMine: Boolean): Map<String, Any> {
		val settings = settingsOf(user)
		return mapOf(
			"rulesVersion" to effectiveVersion(settings, onlyMine),
			"rulesUpdatedAt" to maxOf(settings.rulesUpdatedAt, poolUpdatedAt(onlyMine)).toString(),
			"defaultMode" to settings.defaultMode.name,
			"onlyMyDomains" to onlyMine,
		)
	}

	/* ---------------------------------------------------------------- stats */

	@Transactional
	fun stats(user: User, onlyMine: Boolean): ControlStatsView {
		val userId = requireNotNull(user.id) { "User is not persisted" }
		val settings = settingsOf(user)
		val allRules = ruleRepository.findAllByUserIdOrderByPatternAsc(userId)
		val routing = resolveRouting(userId, onlyMine)

		val top = visitedDomainRepository.search(userId, "%")
			.sortedByDescending { it.hits }
			.take(10)
			.map {
				TopDomainView(
					domain = it.domain,
					hits = it.hits,
					mode = routing.bestFor(it.domain)?.mode ?: settings.defaultMode,
				)
			}

		val active = allRules.filter { it.enabled }
		return ControlStatsView(
			domains = visitedDomainRepository.countByUserId(userId),
			rules = allRules.size.toLong(),
			proxyRules = active.count { it.mode == RoutingMode.PROXY }.toLong(),
			directRules = active.count { it.mode == RoutingMode.DIRECT }.toLong(),
			blockRules = active.count { it.mode == RoutingMode.BLOCK }.toLong(),
			disabledRules = allRules.count { !it.enabled }.toLong(),
			sharedRules = routing.shared.size.toLong(),
			domainsLast24h = visitedDomainRepository
				.countByUserIdAndLastSeenAfter(userId, Instant.now().minus(Duration.ofDays(1))),
			topDomains = top,
			lastReportAt = settings.lastReportAt,
			rulesVersion = effectiveVersion(settings, onlyMine),
		)
	}

	@Transactional
	fun me(user: User, onlyMine: Boolean): ControlMeResponse = ControlMeResponse(
		userName = user.name,
		isEnabled = user.isEnabled ?: false,
		settings = settingsOf(user).toView(onlyMine),
		stats = stats(user, onlyMine),
	)

	/* ------------------------------------------------------------ retention */

	/** Drops history nobody looked at for [retentionDays]; rules are never touched. */
	@Scheduled(cron = "\${app.control.cleanup-cron:0 30 4 * * *}")
	@Transactional
	fun dropStaleHistory() {
		if (retentionDays <= 0) {
			return
		}
		val removed = visitedDomainRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(retentionDays)))
		if (removed > 0) {
			logger.info("dropped {} visited domains older than {} days", removed, retentionDays)
		}
	}

	/* -------------------------------------------------------------- routing */

	/** One rule as it will be applied, with a note of where it came from. */
	data class ResolvedRule(
		val pattern: String,
		val matchType: DomainMatchType,
		val mode: RoutingMode,
		val source: RuleSource,
		/** The user's own rule behind this entry, or null for a pooled one. */
		val ownRule: SplitRoutingRule?,
	) {
		fun matches(domain: String): Boolean = matchType.matches(pattern, domain)
		fun specificity(): Int = matchType.specificity(pattern)
	}

	class ResolvedRouting(
		val own: List<ResolvedRule>,
		val shared: List<ResolvedRule>,
	) {
		/**
		 * The rule that decides this domain. A user's own rule wins outright — if any
		 * of them matches, the pool is not consulted at all, so a stranger's rule can
		 * never override a decision the user made themselves.
		 */
		fun bestFor(domain: String): ResolvedRule? =
			own.filter { it.matches(domain) }.maxByOrNull { it.specificity() }
				?: shared.filter { it.matches(domain) }.maxByOrNull { it.specificity() }
	}

	/**
	 * Builds the ruleset for one user: their own rules, plus — unless they opted
	 * out — what everyone else agreed on.
	 *
	 * Two users can disagree about the same pattern, so the pool takes the mode the
	 * most people chose, and the most recently changed rule breaks a tie. Majority
	 * rather than "last write wins" on purpose: otherwise any single subscriber
	 * could flip a domain for everybody at any moment.
	 */
	@Transactional
	fun resolveRouting(userId: Long, onlyMine: Boolean): ResolvedRouting {
		val own = ruleRepository.findAllByUserIdOrderByPatternAsc(userId)
			.filter { it.enabled }
			.map { ResolvedRule(it.pattern, it.matchType, it.mode, RuleSource.OWN, it) }

		if (onlyMine) {
			return ResolvedRouting(own, emptyList())
		}

		val ownKeys = own.map { it.pattern to it.matchType }.toSet()
		val shared = ruleRepository.findAllByEnabledTrue()
			.filter { it.user?.id != userId }
			.groupBy { it.pattern to it.matchType }
			.filterKeys { it !in ownKeys }
			.mapNotNull { (key, rules) ->
				val mode = rules
					.groupBy { it.mode }
					.maxWithOrNull(
						compareBy(
							{ (_, group) -> group.size },
							{ (_, group) -> group.maxOf { it.updatedAt } },
						),
					)
					?.key
					?: return@mapNotNull null
				ResolvedRule(key.first, key.second, mode, RuleSource.SHARED, null)
			}
			.sortedWith(compareBy({ it.matchType }, { it.pattern }))

		return ResolvedRouting(own, shared)
	}

	/* ---------------------------------------------------------------- utils */

	/** Bumps the user's own version and, since rules are pooled, the shared one too. */
	private fun touchRules(settings: UserControlSettings) {
		settings.touchRules()
		settingsRepository.save(settings)
		bumpPool()
	}

	private fun bumpPool() {
		if (rulesStateRepository.bump(ControlRulesState.SINGLETON_ID, Instant.now()) == 0) {
			// First change on a fresh installation: the singleton row is not there yet.
			rulesStateRepository.save(ControlRulesState(rulesVersion = 1, updatedAt = Instant.now()))
		}
	}

	private fun poolState(): ControlRulesState? =
		rulesStateRepository.findById(ControlRulesState.SINGLETON_ID).orElse(null)

	private fun poolUpdatedAt(onlyMine: Boolean): Instant =
		if (onlyMine) Instant.EPOCH else poolState()?.updatedAt ?: Instant.EPOCH

	/**
	 * A number that changes whenever anything this user's routing depends on changes.
	 * Opted out, that is only their own rules; pooled, it is theirs plus everyone's,
	 * so a change made by somebody else still moves it. Opaque: compare it for
	 * inequality, do not read anything into the value.
	 */
	private fun effectiveVersion(settings: UserControlSettings, onlyMine: Boolean): Long =
		if (onlyMine) settings.rulesVersion else settings.rulesVersion + (poolState()?.rulesVersion ?: 0)

	private fun UserControlSettings.toView(onlyMine: Boolean) = ControlSettingsView(
		defaultMode = defaultMode,
		collectEnabled = collectEnabled,
		rulesVersion = effectiveVersion(this, onlyMine),
		rulesUpdatedAt = rulesUpdatedAt,
		lastReportAt = lastReportAt,
	)

	private fun SplitRoutingRule.toView() = RuleView(
		id = requireNotNull(id),
		pattern = pattern,
		matchType = matchType,
		mode = mode,
		enabled = enabled,
		comment = comment,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)

	companion object {
		/**
		 * The outbound tags the generated sing-box configurations already define
		 * (see ConfigTemplate): "proxy" for the tunnel, plus a "direct" and a "block"
		 * outbound.
		 */
		fun RoutingMode.toOutbound(): String = when (this) {
			RoutingMode.PROXY -> "proxy"
			RoutingMode.DIRECT -> "direct"
			RoutingMode.BLOCK -> "block"
		}
	}
}
