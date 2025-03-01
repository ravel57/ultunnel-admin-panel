package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.*


@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "config_type", discriminatorType = DiscriminatorType.STRING)
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

) {
	abstract fun fillFields(): ConfigData
}