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

	fun findAllByProxy_IdInAndType(proxyIds: Collection<Long>, type: String): List<ConfigData>

	@Modifying
	@Query("delete from ConfigData c where c.id in :configIds")
	fun deleteAllByConfigIds(@Param("configIds") configIds: Collection<Long>): Int

	@Modifying
	@Query(
		value = "update config_data set proxy_id = null where proxy_id = :proxyId",
		nativeQuery = true,
	)
	fun detachAllFromProxy(@Param("proxyId") proxyId: Long): Int

	/*
	 * @ElementCollection / peer rows are not touched by a JPQL bulk delete, so they
	 * keep their FK to config_data_wireguard and make the delete fail. Purge them
	 * explicitly before deleting the configs themselves.
	 */

	@Modifying
	@Query(
		value = """
			delete from config_data_wireguard_peer_allowed_ips
			where config_data_wireguard_peer_id in (
				select id from config_data_wireguard_peer where config_id in (:configIds)
			)
		""",
		nativeQuery = true,
	)
	fun deleteWireguardPeerAllowedIps(@Param("configIds") configIds: Collection<Long>): Int

	@Modifying
	@Query(
		value = """
			delete from config_data_wireguard_peer_reserved
			where config_data_wireguard_peer_id in (
				select id from config_data_wireguard_peer where config_id in (:configIds)
			)
		""",
		nativeQuery = true,
	)
	fun deleteWireguardPeerReserved(@Param("configIds") configIds: Collection<Long>): Int

	@Modifying
	@Query(
		value = "delete from config_data_wireguard_peer where config_id in (:configIds)",
		nativeQuery = true,
	)
	fun deleteWireguardPeers(@Param("configIds") configIds: Collection<Long>): Int

	@Modifying
	@Query(
		value = "delete from config_data_wireguard_local_address where config_data_wireguard_id in (:configIds)",
		nativeQuery = true,
	)
	fun deleteWireguardLocalAddresses(@Param("configIds") configIds: Collection<Long>): Int

	@Modifying
	@Query(
		value = "delete from config_data_wireguard_reserved where config_data_wireguard_id in (:configIds)",
		nativeQuery = true,
	)
	fun deleteWireguardReserved(@Param("configIds") configIds: Collection<Long>): Int

	@Modifying
	@Query(
		value = """
			delete from config_data_amnezia_wireguard_local_address
			where config_data_amnezia_wireguard_id in (:configIds)
		""",
		nativeQuery = true,
	)
	fun deleteAmneziaLocalAddresses(@Param("configIds") configIds: Collection<Long>): Int

	@Modifying
	@Query(
		value = """
			delete from config_data_amnezia_wireguard_reserved
			where config_data_amnezia_wireguard_id in (:configIds)
		""",
		nativeQuery = true,
	)
	fun deleteAmneziaReserved(@Param("configIds") configIds: Collection<Long>): Int
}
