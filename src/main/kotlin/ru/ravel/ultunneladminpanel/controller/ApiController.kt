package ru.ravel.ultunneladminpanel.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.ravel.ultunneladminpanel.service.ThreeXUiService

@RestController
@RequestMapping("/api/v1")
class ApiController(
	val threeXUiService: ThreeXUiService
) {

	@GetMapping("")
	fun getConfigs(): ResponseEntity<Any> {
		return ResponseEntity.ok().body(null)
	}
}