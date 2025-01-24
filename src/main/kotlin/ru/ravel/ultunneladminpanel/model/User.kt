package ru.ravel.ultunneladminpanel.model

import jakarta.persistence.*


@Entity
@Table(name = "users_t")
data class User(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	var name: String? = null,

	var secretKey: String? = null,

	@OneToMany(
		cascade = [(CascadeType.ALL)],
		fetch = FetchType.EAGER,
	)
	var proxies: MutableList<UsersProxy> = mutableListOf(),
)