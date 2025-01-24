package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.ravel.ultunneladminpanel.model.ProxyServer

interface ProxyServerRepository : JpaRepository<ProxyServer, Long> {
	fun findByHost(host: String): ProxyServer?
}