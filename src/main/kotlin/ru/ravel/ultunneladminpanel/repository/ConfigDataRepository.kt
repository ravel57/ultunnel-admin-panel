package ru.ravel.ultunneladminpanel.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.ravel.ultunneladminpanel.model.config.ConfigData


@Repository
interface ConfigDataRepository : JpaRepository<ConfigData, Long>