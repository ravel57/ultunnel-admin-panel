package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.ravel.ultunneladminpanel.model.UsersProxy

interface UserProxyRepository : JpaRepository<UsersProxy, Long>