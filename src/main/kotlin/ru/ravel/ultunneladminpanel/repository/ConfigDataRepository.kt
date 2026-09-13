package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.ravel.ultunneladminpanel.model.config.ConfigData


@Repository
interface ConfigDataRepository : JpaRepository<ConfigData, Long> {
	fun findAllByProxy_IdIn(proxyIds: Collection<Long>): List<ConfigData>

	fun findAllByProxy_IdAndType(proxyId: Long, type: String): List<ConfigData>

	@Modifying
	@Query("delete from ConfigData c where c.proxy.id in :proxyIds")
	fun deleteAllByProxyIds(@Param("proxyIds") proxyIds: Collection<Long>): Int

	@Modifying
	@Query("delete from ConfigData c where c.proxy.id = :proxyId and c.type = :configType")
	fun deleteAllByProxyIdAndType(
		@Param("proxyId") proxyId: Long,
		@Param("configType") configType: String,
	): Int

	@Modifying
	@Query(
		value = "update config_data set proxy_id = null where proxy_id = :proxyId",
		nativeQuery = true,
	)
	fun detachAllFromProxy(@Param("proxyId") proxyId: Long): Int
}