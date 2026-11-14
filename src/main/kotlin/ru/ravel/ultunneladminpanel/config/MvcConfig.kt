package ru.ravel.ultunneladminpanel.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class MvcConfig(
	private val controlAuthInterceptor: ControlAuthInterceptor,
) : WebMvcConfigurer {

	override fun addViewControllers(registry: ViewControllerRegistry) {
		registry.addViewController("/").setViewName("index")
		/* The control UI is the same single-page bundle; it picks its own layout from
		   the host name and the path. Both spellings are mapped so a reload of
		   control.ultunnel.ru/control does not 404. */
		registry.addViewController("/control").setViewName("index")
		registry.addViewController("/control/").setViewName("index")
	}

	override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
		registry.addResourceHandler("/static/**")
			.addResourceLocations("classpath:/static/")
	}

	override fun addInterceptors(registry: InterceptorRegistry) {
		registry.addInterceptor(controlAuthInterceptor)
			.addPathPatterns("/control/api/v1/**")
	}
}
