package ru.ravel.ultunneladminpanel.dto.control

import com.fasterxml.jackson.annotation.JsonInclude
import ru.ravel.ultunneladminpanel.model.control.DomainMatchType
import ru.ravel.ultunneladminpanel.model.control.RoutingMode
import ru.ravel.ultunneladminpanel.model.control.RuleSource
import java.time.Instant

/* ---------------------------------------------------------------- reporting */

/**
 * What the client application pushes. Both shapes are accepted, so a thin client
 * can send nothing but names:
 *
 *   {"deviceId": "laptop", "domains": ["ya.ru", "github.com"]}
 *   {"deviceId": "laptop", "entries": [{"domain": "ya.ru", "address": "5.255.255.242", "hits": 3}]}
 */
data class VisitReportRequest(
	val deviceId: String? = null,
	/*
	 * Nullable rather than defaulted-to-empty on purpose. Every field of this class
	 * has a default, so Kotlin also emits a no-arg constructor; Jackson picks that
	 * one and fills the fields by reflection, which bypasses the Kotlin value
	 * instantiator and with it the defaults. A client that spells an absent list out
	 * as `"entries": null` would then write a null into a non-null property and the
	 * first .map() on it would throw. The service reads these through orEmpty().
	 */
	val domains: List<String>? = null,
	val entries: List<VisitEntryRequest>? = null,
)

data class VisitEntryRequest(
	val domain: String? = null,
	val address: String? = null,
	val hits: Long? = null,
	val at: Instant? = null,
)

data class VisitReportResponse(
	val accepted: Int,
	val rejected: Int,
	val stored: Boolean,
	val rulesVersion: Long,
)

/* ------------------------------------------------------------------ reading */

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VisitedDomainView(
	val domain: String,
	val addresses: List<String>,
	val hits: Long,
	val firstSeen: Instant,
	val lastSeen: Instant,
	val deviceId: String?,
	/** What actually happens to this domain right now, pool included. */
	val mode: RoutingMode,
	/** Where [mode] came from: the user's own rule, the shared pool or the default. */
	val ruleSource: RuleSource,
	/** True when the user has a rule of their own for this domain. */
	val hasRule: Boolean,
	val ruleId: Long?,
	val rulePattern: String?,
	val ruleMatchType: DomainMatchType?,
)

data class VisitedDomainPage(
	val total: Int,
	val offset: Int,
	val limit: Int,
	val items: List<VisitedDomainView>,
)

/* -------------------------------------------------------------------- rules */

data class RuleView(
	val id: Long,
	val pattern: String,
	val matchType: DomainMatchType,
	val mode: RoutingMode,
	val enabled: Boolean,
	val comment: String?,
	val createdAt: Instant,
	val updatedAt: Instant,
)

data class RulesResponse(
	val rulesVersion: Long,
	val defaultMode: RoutingMode,
	val rules: List<RuleView>,
)

/**
 * Upsert. [mode] null removes the rule for those patterns, which puts the domains
 * back under the default mode — that is what the "по умолчанию" position of the
 * per-domain switch sends.
 */
data class UpsertRulesRequest(
	/* Nullable for the reason spelled out on VisitReportRequest. */
	val patterns: List<String>? = null,
	val mode: RoutingMode? = null,
	val matchType: DomainMatchType? = null,
	val enabled: Boolean? = null,
	val comment: String? = null,
)

data class DeleteRulesRequest(
	val ids: List<Long>? = null,
	val patterns: List<String>? = null,
)

data class RulesChangeResponse(
	val created: Int,
	val updated: Int,
	val deleted: Int,
	val rulesVersion: Long,
)

/* ----------------------------------------------------------------- settings */

data class ControlSettingsView(
	val defaultMode: RoutingMode,
	val collectEnabled: Boolean,
	val rulesVersion: Long,
	val rulesUpdatedAt: Instant,
	val lastReportAt: Instant?,
)

data class UpdateControlSettingsRequest(
	val defaultMode: RoutingMode? = null,
	val collectEnabled: Boolean? = null,
)

/* --------------------------------------------------------------- me / stats */

data class ControlMeResponse(
	val userName: String?,
	val isEnabled: Boolean,
	val settings: ControlSettingsView,
	val stats: ControlStatsView,
)

data class ControlStatsView(
	val domains: Long,
	val rules: Long,
	val proxyRules: Long,
	val directRules: Long,
	val blockRules: Long,
	val disabledRules: Long,
	/** Rules the pool contributes on top of this user's own; 0 when opted out. */
	val sharedRules: Long,
	val domainsLast24h: Long,
	val topDomains: List<TopDomainView>,
	val lastReportAt: Instant?,
	val rulesVersion: Long,
)

data class TopDomainView(
	val domain: String,
	val hits: Long,
	val mode: RoutingMode,
)

/* ------------------------------------------------------- client-facing rules */

/**
 * A ready-to-merge fragment of a sing-box `route` section. The client application
 * splices [rules] in front of its own catch-all rule and keeps everything else of
 * its configuration as it is.
 */
data class SingBoxRoutingResponse(
	val rulesVersion: Long,
	val onlyMyDomains: Boolean,
	val final: String,
	val rules: List<Map<String, Any>>,
)
