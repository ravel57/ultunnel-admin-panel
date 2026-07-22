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

	fun login(proxy: Proxy, host: String): Session {
		val jsch = JSch()
		val session: Session = jsch.getSession(proxy.login, host, proxy.port!!.toInt())
		session.setPassword(proxy.password)
		val config = Properties()
		config["StrictHostKeyChecking"] = "no"
		session.setConfig(config)
		session.connect()
		return session
	}


	fun addNewUser(proxy: Proxy, host: String, user: User): String {
		try {
			val session = login(proxy, host)
			val channel = session.openChannel("exec") as ChannelExec
			val password = UserService.generateSecretKey()
			val command = """
				export PATH="${'$'}PATH:/sbin:/usr/sbin:/usr/local/sbin";
				useradd '${user.name}' --no-create-home --no-user-group --shell /usr/sbin/nologin --password "${'$'}(openssl passwd -6 '$password')"
			""".trimIndent()
			channel.setCommand(command)
			val stdout = channel.inputStream
			val stderr = channel.errStream
			channel.connect()
			val output = stdout.bufferedReader().readText()
			val error = stderr.bufferedReader().readText()
			while (!channel.isClosed) {
				Thread.sleep(100)
			}
			val exitCode = channel.exitStatus
			channel.disconnect()
			session.disconnect()
			if (exitCode != 0) {
				throw RuntimeException("useradd failed with exit code $exitCode\nSTDOUT:\n$output\nSTDERR:\n$error\nCOMMAND:\n$command")
			}
			return password
		} catch (e: Exception) {
			throw RuntimeException("Error while trying to add user", e)
		}
	}

}