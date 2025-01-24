package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.ravel.ultunneladminpanel.model.User

interface UserRepository : JpaRepository<User, Long>