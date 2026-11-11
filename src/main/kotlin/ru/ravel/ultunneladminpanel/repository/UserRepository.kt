package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.ravel.ultunneladminpanel.model.User

interface UserRepository : JpaRepository<User, Long> {
    @Query(value = "select id from users_t where id = :id for update", nativeQuery = true)
    fun lockUser(@Param("id") id: Long): Long?

    fun findBySecretKey(secretKey: String): User?
}