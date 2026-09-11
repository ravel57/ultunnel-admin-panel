package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import com.fasterxml.jackson.module.kotlin.KotlinModule
import ru.ravel.ultunneladminpanel.service.SerializationContext

object ConfigTemplate {

	private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

	fun serializeConfig(configData: ConfigData, ignoreUrl: Boolean, platform: String): String {
		val excludes = mutableSetOf<String>()
		if (ignoreUrl) {
			excludes += "url"
		}
		if (platform == "android" || platform.isBlank()) {
			excludes += "domain_resolver"
		}
		val filter = if (excludes.isEmpty()) {
			SimpleBeanPropertyFilter.serializeAll()
		} else {
			SimpleBeanPropertyFilter.serializeAllExcept(excludes)
		}
		val filters = SimpleFilterProvider().addFilter("configFilter", filter)
		return mapper.writer(filters).writeValueAsString(configData)
	}

	fun getConfig(configData: ConfigData, platform: String = ""): String {
		val routeExcludeAddress = if (configData.proxy?.serverIp != null) {
			"""
				|      "route_exclude_address": [
				|        "${configData.proxy?.serverIp}/32"
				|      ],
			"""
		} else {
			""
		}

		val serializedConfigData = serializeConfig(configData, ignoreUrl = true, platform)
		SerializationContext.setControllerCall(true)
		SerializationContext.clear()
		val config = when (platform) {
			"android" -> {
				"""
				|{
				|  "log": {
				|    "level": "debug"
				|  },
				|  "dns": {
				|    "strategy": "ipv4_only",
				|    "final": "remote-dns",
				|    "servers": [
				|      {
				|        "type": "udp",
				|        "tag": "bootstrap",
				|        "server": "1.1.1.1"
				|      },
				|      {
				|        "tag": "remote-dns",
				|        "type": "https",
				|        "server": "1.1.1.1",
				|        "server_port": 443,
				|        "path": "/dns-query",
				|        "tls": {
				|          "enabled": true,
				|          "server_name": "cloudflare-dns.com"
				|        },
				|        "detour": "proxy"
				|      }
				|    ]
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
				|      "mtu": 1360,
				|      "endpoint_independent_nat": true,
				|      "sniff": true,
				|      "sniff_override_destination": true
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
				|    "default_domain_resolver": "bootstrap",
				|    "rules": [
				|      {
				|        "inbound": ["tun-in"],
				|        "action": "sniff"
				|      },
				|      {
				|        "protocol": ["dns"],
				|        "action": "hijack-dns"
				|      },
				|      {
				|        "inbound": ["tun-in"],
				|        "outbound": "proxy"
				|      }
				|    ],
				|    "final": "proxy"
				|  }
				|}
				""".trimMargin()
			}

			"ios" -> {
				"""
				|{
				|  "log": {
				|    "level": "debug"
				|  },
				|  "dns": {
				|    "strategy": "ipv4_only",
				|    "final": "remote-dns",
				|    "servers": [
				|      {
				|        "type": "udp",
				|        "tag": "bootstrap",
				|        "server": "1.1.1.1"
				|      },
				|      {
				|        "tag": "remote-dns",
				|        "type": "https",
				|        "server": "1.1.1.1",
				|        "server_port": 443,
				|        "path": "/dns-query",
				|        "tls": {
				|          "enabled": true,
				|          "server_name": "cloudflare-dns.com"
				|        },
				|        "detour": "proxy"
				|      }
				|    ]
				|  },
				|  "inbounds": [
				|    {
				|      "type": "tun",
				|      "tag": "tun-in",
				|      "interface_name": "tun0",
				|      "auto_route": true,
				|      "strict_route": true,
				|      "stack": "gvisor",
				|      "address": [
				|        "198.18.0.1/30"
				|      ],
				|      "mtu": 1360,
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
				|    "default_domain_resolver": "bootstrap",
				|    "rules": [
				|      {
				|        "inbound": ["tun-in"],
				|        "action": "sniff"
				|      },
				|      {
				|        "protocol": ["dns"],
				|        "action": "hijack-dns"
				|      },
				|      {
				|        "inbound": ["tun-in"],
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
				|	"strategy": "ipv4_only",
				|	"final": "remote-dns",
				|	"servers": [
				|	  {
				|		"type": "udp",
				|		"tag": "bootstrap",
				|		"server": "1.1.1.1"
				|	  },
				|	  {
				|		"detour": "proxy",
				|		"path": "/dns-query",
				|		"server": "1.1.1.1",
				|		"server_port": 443,
				|		"tag": "remote-dns",
				|		"tls": {
				|		  "enabled": true,
				|		  "server_name": "cloudflare-dns.com"
				|		},
				|		"type": "https"
				|	  }
				|	]
				|  },
				|  "inbounds": [
				|	{
				|	  "type": "tun",
				|	  "tag": "tun-in",
				|	  "interface_name": "utun99",
				|	  "address": [
				|	    "198.18.0.1/30"
				|	  ],
				|	  "auto_route": true,
				|	  "strict_route": true,
				|	  ${routeExcludeAddress}
				|	  "stack": "system",
				|	  "mtu": 1360,
				|	  "endpoint_independent_nat": true
				|	}
				|  ],
				|  "outbounds": [
				|	${serializedConfigData},
				|	{
				|	  "type": "direct",
				|	  "tag": "direct"
				|	},
				|	{
				|	  "type": "block",
				|	  "tag": "block"
				|	}
				|  ],
				|  "route": {
				|	"auto_detect_interface": false,
				|	"default_domain_resolver": "bootstrap",
				|	"rules": [
				|	  {
				|		"inbound": ["tun-in"],
				|		"action": "sniff"
				|	  },
				|	  {
				|		"protocol": ["dns"],
				|		"action": "hijack-dns"
				|	  },
				|	  {
				|		"inbound": ["tun-in"],
				|		"outbound": "proxy"
				|	  }
				|	],
				|	"final": "proxy"
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