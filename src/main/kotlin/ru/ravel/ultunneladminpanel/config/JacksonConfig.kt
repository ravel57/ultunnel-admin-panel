package ru.ravel.ultunneladminpanel.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
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

		val filters = SimpleFilterProvider().addFilter(
			"configFilter",
			SimpleBeanPropertyFilter.serializeAll()
		)

		objectMapper.setFilterProvider(filters)
		return objectMapper
	}
}
