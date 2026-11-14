package ru.ravel.ultunneladminpanel.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {

	@Bean
	fun objectMapper(): ObjectMapper {
		val objectMapper = ObjectMapper()
			.registerModule(KotlinModule())
			.registerModule(JavaTimeModule())
			/*
			 * This bean replaces the one Spring Boot would have built, and a bare
			 * ObjectMapper still writes dates as numbers: a LocalDate leaves as
			 * [2026, 11, 14] and an Instant as 1794658083.031565000, neither of which
			 * `new Date(value)` in the browser can read. ISO-8601 for both.
			 */
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

		val filters = SimpleFilterProvider().addFilter(
			"configFilter",
			SimpleBeanPropertyFilter.serializeAll()
		)

		objectMapper.setFilterProvider(filters)
		return objectMapper
	}
}
