package ru.ravel.ultunneladminpanel.service

import ru.ravel.ultunneladminpanel.model.config.ConfigDataWireguard
import ru.ravel.ultunneladminpanel.model.config.ConfigDataWireguardPeer

object WireguardConfigParser {

    fun parseConfig(filePath: String, host: String): ConfigDataWireguard {
        val lines = filePath.lines()
        val peers = mutableListOf<ConfigDataWireguardPeer>()
        val server: String = host
        var serverPort: Long? = null
        var interfaceName: String? = null
        var localAddress: List<String>? = null
        var privateKey: String? = null
        var peerPublicKey: String? = null
        var preSharedKey: String? = null
        var reserved: List<Int>? = null
        var workers: Int? = null
        var mtu: Int? = null
        var network: String? = null
        var gso: Boolean? = null

        var peerServer: String? = null
        var peerServerPort: Int? = null
        var peerPublic: String? = null
        var peerPreShared: String? = null
        var peerAllowedIps: List<String>? = null
        var peerReserved: List<Int>? = null

        var insidePeerSection = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            when {
                trimmed.startsWith("[Interface]") -> insidePeerSection = false
                trimmed.startsWith("[Peer]") -> {
                    insidePeerSection = true
                    if (peerPublic != null) {
                        peers.add(
                            ConfigDataWireguardPeer(
                                server = peerServer,
                                serverPort = peerServerPort,
                                publicKey = peerPublic,
                                preSharedKey = peerPreShared,
                                allowedIps = peerAllowedIps,
                                reserved = peerReserved
                            )
                        )
                    }
                    peerServer = null
                    peerServerPort = null
                    peerPublic = null
                    peerPreShared = null
                    peerAllowedIps = null
                    peerReserved = null
                }

                insidePeerSection -> {
                    val (key, value) = trimmed.split("=", limit = 2).map { it.trim() }
                    when (key.lowercase()) {
                        "endpoint" -> {
                            val parts = value.split(":")
                            peerServer = parts[0]
                            peerServerPort = parts[1].toIntOrNull()
                        }

                        "publickey" -> peerPublic = value
                        "presharedkey" -> peerPreShared = value
                        "allowedips" -> peerAllowedIps = value.split(",").map { it.trim() }
                        "reserved" -> peerReserved = value.split(",").mapNotNull { it.toIntOrNull() }
                    }
                }

                else -> {
                    val (key, value) = trimmed.split("=", limit = 2).map { it.trim() }
                    when (key.lowercase()) {
                        "address" -> localAddress = value.split(",").map { it.trim() }
                        "privatekey" -> privateKey = value
                        "listenport" -> serverPort = value.toLongOrNull()
                        "mtu" -> mtu = value.toIntOrNull()
                        "peers" -> peerPublicKey = value
                        "presharedkey" -> preSharedKey = value
                        "reserved" -> reserved = value.split(",").mapNotNull { it.toIntOrNull() }
                        "workers" -> workers = value.toIntOrNull()
                        "network" -> network = value
                        "gso" -> gso = value.toBooleanStrictOrNull()
                    }
                }
            }
        }

        if (peerPublic != null) {
            peers.add(
                ConfigDataWireguardPeer(
                    server = peerServer,
                    serverPort = peerServerPort,
                    publicKey = peerPublic,
                    preSharedKey = peerPreShared,
                    allowedIps = peerAllowedIps,
                    reserved = peerReserved
                )
            )
        }

        return ConfigDataWireguard(
            server = server,
            serverPort = serverPort,
            interfaceName = interfaceName,
            localAddress = localAddress,
            privateKey = privateKey,
            peerPublicKey = peerPublicKey,
            preSharedKey = preSharedKey,
            reserved = reserved,
            workers = workers,
            mtu = mtu,
            network = network,
            gso = gso,
            peers = peers
        )
    }

}
