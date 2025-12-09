package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.fasterxml.jackson.module.kotlin.KotlinModule
import ru.ravel.ultunneladminpanel.service.SerializationContext

object ConfigTemplate {

	fun serializeConfig(configData: ConfigData, ignoreUrl: Boolean): String {
		val mapper = ObjectMapper().registerModule(KotlinModule())
		val filter = if (ignoreUrl) {
			SimpleBeanPropertyFilter.serializeAllExcept("url")
		} else {
			SimpleBeanPropertyFilter.serializeAll()
		}
		val filters = SimpleFilterProvider().addFilter("configFilter", filter)
		return mapper.writer(filters).writeValueAsString(configData)
	}

	fun getConfig(configData: ConfigData, platform: String = ""): String {
		val serializedConfigData = serializeConfig(configData, ignoreUrl = true)
		SerializationContext.setControllerCall(true)
		SerializationContext.clear()
		val config = when (platform) {
			"android", "" -> {
				"""
				|{
				|  "log": {
				|    "level": "debug"
				|  },
				|  "dns": {
				|    "servers": [
				|      {
				|        "type": "udp",
				|        "tag": "cloudflare",
				|        "server": "1.1.1.1"
				|      },
				|      {
				|        "type": "udp",
				|        "tag": "google",
				|        "server": "8.8.8.8"
				|      }
				|    ],
				|    "final": "cloudflare",
				|    "strategy": "ipv4_only"
				|  },
				|  "inbounds": [
				|    {
				|      "type": "tun",
				|      "tag": "tun-in",
				|      "interface_name": "tun0",
				|      "auto_route": true,
				|      "strict_route": true,
				|      "stack": "system",
				|      "address": [
				|        "198.18.0.1/30"
				|      ],
				|      "mtu": 1500,
				|      "endpoint_independent_nat": true
				|    }
				|  ],
				|  "outbounds": [
				|    ${serializedConfigData},
				|    {
				|      "type": "direct",
				|      "tag": "direct"
				|    },
				|    {
				|      "type": "block",
				|      "tag": "block"
				|    }
				|  ],
				|  "route": {
				|    "auto_detect_interface": true,
				|    "override_android_vpn": true,
				|    "default_domain_resolver": "cloudflare",
				|    "rules": [
				|      {
				|        "inbound": [
				|          "tun-in"
				|        ],
				|        "protocol": [
				|			  "dns"
				|       ],
				|        "action": "hijack-dns"
				|      },
				|      {
				|        "inbound": [
				|          "tun-in"
				|        ],
				|        "outbound": "proxy"
				|      }
				|    ],
				|    "final": "proxy"
				|  }
				|}
				""".trimMargin()
			}

			"desktop" -> {
				"""
				|{
				|  "log": {
				|    "level": "debug"
				|  },
				|  "dns": {
				|    "servers": [
				|      {
				|        "type": "udp",
				|        "tag": "cloudflare",
				|        "server": "1.1.1.1"
				|      },
				|      {
				|        "type": "udp",
				|        "tag": "google",
				|        "server": "8.8.8.8"
				|      }
				|    ],
				|    "final": "cloudflare",
				|    "strategy": "ipv4_only"
				|  },
				|  "inbounds": [
				|    {
				|      "type": "tun",
				|      "tag": "tun-in",
				|      "interface_name": "utun99",
				|      "auto_route": true,
				|      "strict_route": false,
				|      "stack": "system",
				|      "address": [
				|        "198.18.0.1/30"
				|      ],
				|      "mtu": 1500,
				|      "endpoint_independent_nat": true
				|    }
				|  ],
				|  "outbounds": [
				|    ${serializedConfigData},
				|    {
				|      "type": "direct",
				|      "tag": "direct"
				|    },
				|    {
				|      "type": "block",
				|      "tag": "block"
				|    }
				|  ],
				|  "route": {
				|    "auto_detect_interface": true,
				|    "default_domain_resolver": "cloudflare",
				|    "rules": [
				|      {
				|        "protocol": [
				|          "dns"
				|        ],
				|        "action": "hijack-dns"
				|      },
				|      {
				|        "domain_suffix": [
				|          "localhost",
				|          "msftconnecttest.com",
				|          "msftncsi.com"
				|        ],
				|        "outbound": "direct"
				|      },
				|      {
				|        "inbound": [
				|          "tun-in"
				|        ],
				|        "outbound": "proxy"
				|      }
				|    ],
				|    "final": "proxy"
				|  }
				|}
				""".trimMargin()
			}

			else -> {
				throw IllegalArgumentException("Unsupported platform: $platform")
			}
		}
		return config
	}
}