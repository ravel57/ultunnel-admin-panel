package ru.ravel.ultunneladminpanel.controller.control

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ravel.ultunneladminpanel.dto.control.*
import ru.ravel.ultunneladminpanel.model.User
import ru.ravel.ultunneladminpanel.model.control.RoutingMode
import ru.ravel.ultunneladminpanel.service.control.ControlAuthService
import ru.ravel.ultunneladminpanel.service.control.SplitRoutingService

/**
 * The control.ultunnel.ru API.
 *
 * Every call authenticates with the user's secret key (header `X-Secret-Key`, or
 * `Authorization: Bearer <key>`); ControlAuthInterceptor has already resolved it
 * into the [User] that arrives as a request attribute.
 *
 * The client application needs three of these: POST /visits to report what was
 * visited, GET /routing/version to notice a change, GET /routing/singbox to pick
 * the new ruleset up. The rest serves the web UI.
 *
 * Every endpoint takes `onlyMine`. Routing is pooled by default — a client gets
 * the rules every subscriber enabled, not only its own — and `onlyMine=true`
 * narrows it to this user's rules. The flag is a setting of the client
 * application, so it travels on the request and is never stored here.
 *
 * Note what is NOT pooled: GET /visits returns this user's own collected domains
 * and nothing else, whatever `onlyMine` says. One subscriber must not be able to
 * find out what another one browses.
 */
@RestController
@RequestMapping("/control/api/v1")
@CrossOrigin
class ControlApiController(
	private val splitRoutingService: SplitRoutingService,
) {

	@GetMapping("/me")
	fun me(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<ControlMeResponse> = ResponseEntity.ok(splitRoutingService.me(user, onlyMine))

	@GetMapping("/stats")
	fun stats(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<ControlStatsView> = ResponseEntity.ok(splitRoutingService.stats(user, onlyMine))

	/* ----------------------------------------------------------- collection */

	/** What the client application pushes: the domain names it has seen. */
	@PostMapping("/visits")
	fun report(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestBody request: VisitReportRequest,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<VisitReportResponse> = ResponseEntity.ok(splitRoutingService.report(user, request, onlyMine))

	@GetMapping("/visits")
	fun visits(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(required = false) find: String?,
		@RequestParam(required = false) mode: String?,
		@RequestParam(required = false) ruled: Boolean?,
		@RequestParam(defaultValue = "200") limit: Int,
		@RequestParam(defaultValue = "0") offset: Int,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<VisitedDomainPage> {
		val modeFilter = mode?.takeIf { it.isNotBlank() && !it.equals("all", ignoreCase = true) }?.let {
			RoutingMode.parse(it) ?: throw IllegalArgumentException("Unknown routing mode: $it")
		}
		return ResponseEntity.ok(splitRoutingService.domains(user, find, modeFilter, ruled, limit, offset, onlyMine))
	}

	@DeleteMapping("/visits")
	fun forget(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestBody(required = false) request: ForgetDomainsRequest?,
	): ResponseEntity<Map<String, Int>> {
		val removed = if (request == null || request.all == true || request.domains.isNullOrEmpty()) {
			splitRoutingService.forgetAllDomains(user)
		} else {
			splitRoutingService.forgetDomains(user, request.domains)
		}
		return ResponseEntity.ok(mapOf("deleted" to removed))
	}

	/* ---------------------------------------------------------------- rules */

	@GetMapping("/rules")
	fun rules(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<RulesResponse> = ResponseEntity.ok(splitRoutingService.rules(user, onlyMine))

	@PostMapping("/rules")
	fun upsertRules(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestBody request: UpsertRulesRequest,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<RulesChangeResponse> =
		ResponseEntity.ok(splitRoutingService.upsertRules(user, request, onlyMine))

	@DeleteMapping("/rules")
	fun deleteRules(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestBody request: DeleteRulesRequest,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<RulesChangeResponse> =
		ResponseEntity.ok(splitRoutingService.deleteRules(user, request, onlyMine))

	/* ------------------------------------------------------------- settings */

	@GetMapping("/settings")
	fun settings(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<ControlSettingsView> = ResponseEntity.ok(splitRoutingService.settingsView(user, onlyMine))

	@PostMapping("/settings")
	fun updateSettings(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestBody request: UpdateControlSettingsRequest,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<ControlSettingsView> =
		ResponseEntity.ok(splitRoutingService.updateSettings(user, request, onlyMine))

	/* ------------------------------------------------- what the client applies */

	/** Cheap poll: the client re-downloads the ruleset only when this number moved. */
	@GetMapping("/routing/version")
	fun routingVersion(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(splitRoutingService.routingVersion(user, onlyMine))

	/** The ruleset as a sing-box `route` fragment. */
	@GetMapping("/routing/singbox")
	fun singBoxRouting(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<SingBoxRoutingResponse> =
		ResponseEntity.ok(splitRoutingService.singBoxRouting(user, onlyMine))

	/** The same ruleset as plain text, for a client that does not speak sing-box. */
	@GetMapping("/routing/plain", produces = [MediaType.TEXT_PLAIN_VALUE])
	fun plainRouting(
		@RequestAttribute(ControlAuthService.USER_ATTRIBUTE) user: User,
		@RequestParam(defaultValue = "false") onlyMine: Boolean,
	): ResponseEntity<String> = ResponseEntity.ok(splitRoutingService.plainRuleList(user, onlyMine))
}

data class ForgetDomainsRequest(
	/* Nullable for the reason spelled out on VisitReportRequest. */
	val domains: List<String>? = null,
	val all: Boolean? = null,
)
