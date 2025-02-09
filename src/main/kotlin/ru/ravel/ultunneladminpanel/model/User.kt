package ru.ravel.ultunneladminpanel.model

import jakarta.persistence.*
import ru.ravel.ultunneladminpanel.model.config.ConfigData
import java.time.LocalDate


@Entity
@Table(name = "users_t")
data class User(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	@Column(unique = true)
	var name: String? = null,

	var secretKey: String? = null,

	@OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
	var proxiesConfigs: MutableList<ConfigData> = mutableListOf(),

	var isEnabled: Boolean? = null,

	var isForFree: Boolean? = null,

	var createdDate: LocalDate? = null,

	var nextPaymentDate: LocalDate? = null,
)