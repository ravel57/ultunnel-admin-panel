package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonFilter
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import ru.ravel.ultunneladminpanel.model.Proxy


@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "config_type", discriminatorType = DiscriminatorType.STRING)
@JsonFilter("configFilter")
abstract class ConfigData(

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	var id: Long? = null,

	@Column(nullable = false)
	var type: String? = null,

	@Column(nullable = false)
	var server: String? = null,

	@Column(nullable = false)
	@JsonIgnore
	var serverName: String? = null,

	var url: String? = null,

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "proxy_id")
	@JsonIgnore
	var proxy: Proxy? = null,

) {
	abstract fun fillFields(): ConfigData
}