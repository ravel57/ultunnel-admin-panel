package ru.ravel.ultunneladminpanel.model

import jakarta.persistence.*

@Entity
data class Server(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	var name: String? = null,

	var host: String? = null,

	@OneToMany(
		cascade = [CascadeType.ALL],
		orphanRemoval = true,
		fetch = FetchType.EAGER,
	)
	@JoinTable(
		name = "proxies",
		joinColumns = [JoinColumn(name = "server_id")],
		inverseJoinColumns = [JoinColumn(name = "proxy_id")],
	)
	var proxies: MutableList<Proxy>? = null,
)