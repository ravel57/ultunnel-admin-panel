package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.ravel.ultunneladminpanel.model.Admin

interface AdminRepository : JpaRepository<Admin, Long> {
	fun findByLogin(login: String): Admin?
}