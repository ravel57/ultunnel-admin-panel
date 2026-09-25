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
			val useradd = "useradd '${user.name}' --no-create-home --no-user-group " +
				"--shell /usr/sbin/nologin --password \"\$(openssl passwd -6 '$password')\""
			// useradd needs root. Run it directly when the SSH account is root, and
			// escalate with sudo otherwise: -S takes the password from stdin (written
			// below) so it never shows up in the remote process list, -p '' drops the
			// prompt from stderr.
			val command = """
				export PATH="${'$'}PATH:/sbin:/usr/sbin:/usr/local/sbin";
				if [ "${'$'}(id -u)" -eq 0 ]; then
					$useradd
				else
					sudo -S -p '' $useradd
				fi
			""".trimIndent()
			channel.setCommand(command)
			val stdin = channel.outputStream
			val stdout = channel.inputStream
			val stderr = channel.errStream
			channel.connect()
			// Feed the sudo password. Ignored when the command runs as root, and the
			// remote side may close the stream first, which is not an error here.
			runCatching {
				stdin.write("${proxy.password}\n".toByteArray())
				stdin.flush()
				stdin.close()
			}
			val output = stdout.bufferedReader().readText()
			val error = stderr.bufferedReader().readText()
			while (!channel.isClosed) {
				Thread.sleep(100)
			}
			val exitCode = channel.exitStatus
			channel.disconnect()
			session.disconnect()
			if (exitCode != 0) {
				throw RuntimeException(
					"useradd failed with exit code $exitCode${privilegeHint(error)}" +
						"\nSTDOUT:\n$output\nSTDERR:\n$error\nCOMMAND:\n$command"
				)
			}
			return password
		} catch (e: Exception) {
			throw RuntimeException("Error while trying to add user", e)
		}
	}


	/* The raw sudo/useradd output does not say what an admin has to change, so spell it out. */
	private fun privilegeHint(error: String): String {
		val lowercased = error.lowercase()
		val needsPrivileges = listOf(
			"permission denied",
			"cannot lock /etc/passwd",
			"is not in the sudoers file",
			"incorrect password attempt",
			"sudo: command not found",
			"a terminal is required",
		).any { lowercased.contains(it) }

		return if (needsPrivileges) {
			" (the SSH account has no rights to create users: connect as root, " +
				"or give this account sudo rights for useradd with its own password and no TTY)"
		} else {
			""
		}
	}

}
