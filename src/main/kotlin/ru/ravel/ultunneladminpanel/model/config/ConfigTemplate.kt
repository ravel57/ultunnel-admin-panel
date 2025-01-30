package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

class ConfigTemplate {
	private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

	fun getConfig(configData: ConfigData): String = """
	  |{
	  |"log": {
	  |  "level": "panic"
	  |},
	  |"dns": {
	  |  "servers": [
	  |    {
	  |      "tag": "dns-remote",
	  |      "address": "https://dns.google/dns-query",
	  |      "address_resolver": "dns-direct",
	  |      "strategy": "ipv4_only"
	  |    },
	  |    {
	  |      "tag": "dns-direct",
	  |      "address": "local",
	  |      "address_resolver": "dns-local",
	  |      "strategy": "ipv4_only",
	  |      "detour": "direct"
	  |    },
	  |    {
	  |      "tag": "dns-local",
	  |      "address": "local",
	  |      "detour": "direct"
	  |    },
	  |    {
	  |      "tag": "dns-block",
	  |      "address": "rcode://success"
	  |    }
	  |  ],
	  |  "rules": [
	  |    {
	  |      "domain": [
	  |        "${configData.server}",
	  |        "dns.google"
	  |      ],
	  |      "server": "dns-direct"
	  |    }
	  |  ],
	  |  "independent_cache": true
	  |},
	  |"inbounds": [
	  |  {
	  |    "type": "direct",
	  |    "tag": "dns-in",
	  |    "listen": "127.0.0.1",
	  |    "listen_port": 6450,
	  |    "override_address": "8.8.8.8",
	  |    "override_port": 53
	  |  },
	  |  {
	  |    "type": "tun",
	  |    "tag": "tun-in",
	  |    "interface_name": "tun0",
	  |    "mtu": 9000,
	  |    "auto_route": true,
	  |    "endpoint_independent_nat": true,
	  |    "stack": "mixed",
	  |    "sniff": true,
	  |    "inet4_address": "172.19.0.1/28"
	  |  },
	  |  {
	  |    "type": "mixed",
	  |    "tag": "mixed-in",
	  |    "listen": "127.0.0.1",
	  |    "listen_port": 2080,
	  |    "sniff": true
	  |  }
	  |],
	  |"outbounds": [
	  |  ${mapper.writeValueAsString(configData)},
	  |  {
	  |    "type": "direct",
	  |    "tag": "direct"
	  |  },
	  |  {
	  |    "type": "direct",
	  |    "tag": "bypass"
	  |  },
	  |  {
	  |    "type": "block",
	  |    "tag": "block"
	  |  },
	  |  {
	  |    "type": "dns",
	  |    "tag": "dns-out"
	  |  }
	  |],
	  |"route": {
	  |  "rules": [
	  |    {
	  |      "port": 53,
	  |      "outbound": "dns-out"
	  |    },
	  |    {
	  |      "inbound": "dns-in",
	  |      "outbound": "dns-out"
	  |    },
	  |    {
	  |      "source_ip_cidr": [
	  |        "224.0.0.0/3",
	  |        "ff00::/8"
	  |      ],
	  |      "ip_cidr": [
	  |        "224.0.0.0/3",
	  |        "ff00::/8"
	  |      ],
	  |      "outbound": "block"
	  |    }
	  |  ],
	  |  "auto_detect_interface": true
	  |}
	  |}
	""".trimMargin()
}