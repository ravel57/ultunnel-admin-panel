package ru.ravel.ultunneladminpanel.model

import jakarta.persistence.*


@Entity
data class Proxy (
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	var port: String? = null,

	var login: String? = null,

	var password: String? = null,

	@Enumerated(EnumType.STRING)
	var type: ProxyType? = null,
)