package ru.ravel.ultunneladminpanel.service

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.springframework.stereotype.Service
import ru.ravel.ultunneladminpanel.model.Proxy
import ru.ravel.ultunneladminpanel.model.User
import java.util.*


@Service
class SshService {

	fun addNewUser(proxy: Proxy, host: String, user: User): String {
		try {
			val jsch = JSch()
			val session: Session = jsch.getSession(proxy.login, host, proxy.port!!.toInt())
			session.setPassword(proxy.password)
			val config = Properties()
			config["StrictHostKeyChecking"] = "no"
			session.setConfig(config)
			session.connect()
			val channel = session.openChannel("exec")
			val password = UserService.generateSecretKey()
			val command = "useradd ${user.name} --no-create-home --no-user-group --shell /usr/sbin/nologin --password \"\$(openssl passwd -6 ${password})\""
			(channel as ChannelExec).setCommand(command)
			val inputStream = channel.inputStream
			channel.connect()
			inputStream.bufferedReader().use { it.readText() }
			channel.disconnect()
			session.disconnect()
			return password
		} catch (e: Exception) {
			throw RuntimeException("Error while trying to add user", e)
		}
	}

}