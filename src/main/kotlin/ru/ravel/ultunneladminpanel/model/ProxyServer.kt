package ru.ravel.ultunneladminpanel.model

import jakarta.persistence.*

@Entity
data class ProxyServer(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	var id: Long? = null,

	var name: String? = null,

	@Column(unique = true)
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
    var login: String? = null,
    var password: String? = null,
    var useSubDomain: Boolean? = null,
    var subdomain: String? = null,
    var port: Long? = null,
    var serverIp: String? = null,
    var certificateFile: String? = null,
    var privateKeyFile: String? = null,

)