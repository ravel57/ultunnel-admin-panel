package ru.ravel.ultunneladminpanel.component

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import ru.ravel.ultunneladminpanel.service.ProxyServerService

@Component
class ServerConnectionMigration(private val service: ProxyServerService) : ApplicationRunner {
    override fun run(args: ApplicationArguments) { service.migrateServerConnections() }
}
