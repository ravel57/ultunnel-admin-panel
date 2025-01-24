package ru.ravel.ultunneladminpanel.model

import jakarta.persistence.*

@Entity
data class UsersProxy(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	@Column(length = 1024)
	var connectionData: String? = null,
)
