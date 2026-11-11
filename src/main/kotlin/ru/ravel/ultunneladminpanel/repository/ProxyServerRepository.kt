package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.ravel.ultunneladminpanel.model.ProxyServer

interface ProxyServerRepository : JpaRepository<ProxyServer, Long> {
    @Query(value = "select id from proxy_server where id = :id for update", nativeQuery = true)
    fun lockServer(@Param("id") id: Long): Long?

	fun findByHost(host: String): ProxyServer?
}