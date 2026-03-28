package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import ru.ravel.ultunneladminpanel.model.Proxy

@Entity
data class ConfigDataNaive(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	override var id: Long? = null,

	override var type: String? = "naive",

	var tag: String? = "proxy",

	override var server: String?,

	@JsonProperty("server_port")
	var serverPort: Long,

	var username: String,

	var password: String,

	@JdbcTypeCode(SqlTypes.JSON)
	var tls: TlsSettings? = null,

) : ConfigData(id = id, type = type, server = server) {

	override fun fillFields(): ConfigDataNaive {
		this.server = proxy?.serverIp// super.server
		this.type = super.type
		this.tag = "proxy"
		this.url = buildString {
			append("${type}://${username}:${password}@${server}:${serverPort}")
			append("?security=tls")
			append("&sni=${tls?.serverName ?: server}")
		}
		return this
	}

}