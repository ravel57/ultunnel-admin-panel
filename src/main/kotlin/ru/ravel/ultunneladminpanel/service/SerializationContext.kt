package ru.ravel.ultunneladminpanel.service

object SerializationContext {
	private val isControllerCall = ThreadLocal<Boolean>()
	private val isConfigCall = ThreadLocal<Boolean>()

	fun setControllerCall(value: Boolean) {
		isControllerCall.set(value)
	}

	fun isControllerCall(): Boolean {
		return isControllerCall.get() ?: false
	}

	fun clearControllerCall() {
		isControllerCall.remove()
	}

	fun setConfigCall(value: Boolean) {
		isConfigCall.set(value)
	}

	fun isConfigCall(): Boolean {
		return isConfigCall.get() ?: false
	}

	fun clearConfigCall() {
		isConfigCall.remove()
	}

	fun clear() {
		clearControllerCall()
		clearConfigCall()
	}
}