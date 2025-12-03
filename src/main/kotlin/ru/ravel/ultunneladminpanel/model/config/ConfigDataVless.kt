package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.net.URLEncoder
import java.util.Base64


@Entity
data class ConfigDataVless(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "vless",

	var tag: String? = "proxy",

	override var server: String?,

	@JsonProperty("server_port")
	var serverPort: Long,

	var uuid: String,

	@JdbcTypeCode(SqlTypes.JSON)
	var tls: TlsSettings? = null,

	@JdbcTypeCode(SqlTypes.JSON)
	var transport: TransportSettings? = null

) : ConfigData(id = id, type = type, server = server) {

	override fun fillFields(): ConfigDataVless {
		this.server = super.server
		this.type = super.type
		this.tag = "proxy"
//		val encodedEch = URLEncoder.encode(tls?.ech?.config?.get(0) ?: "", "UTF-8")
		this.url = buildString {
			append("${type}://${uuid}@${server}:${serverPort}")
			append("?type=grpc")
			append("&encryption=none")
			append("&serviceName=${transport?.serviceName ?: "GunService"}")
			append("&security=tls")
			append("&fp=chrome")
			append("&alpn=${tls?.alpn?.joinToString(",") ?: ""}")
			append("&sni=${tls?.serverName ?: server}")
//			append("&ech=$encodedEch")
		}
		return this
	}

}