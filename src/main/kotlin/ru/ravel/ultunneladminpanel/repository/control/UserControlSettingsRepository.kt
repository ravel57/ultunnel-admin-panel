package ru.ravel.ultunneladminpanel.repository.control

import org.springframework.data.jpa.repository.JpaRepository
import ru.ravel.ultunneladminpanel.model.control.UserControlSettings

interface UserControlSettingsRepository : JpaRepository<UserControlSettings, Long> {

	fun findByUserId(userId: Long): UserControlSettings?
}
