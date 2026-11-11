package ru.ravel.ultunneladminpanel.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RequestErrorHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException::class)
    fun status(error: org.springframework.web.server.ResponseStatusException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(error.statusCode).body(mapOf("message" to (error.reason ?: "Request failed")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(error: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("message" to (error.message ?: "Invalid request")))

    @ExceptionHandler(IllegalStateException::class)
    fun panelFailure(error: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(502).body(mapOf("message" to (error.message ?: "Panel request failed")))
}
