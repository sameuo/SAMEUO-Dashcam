package com.sameuo.dashcam.core

/**
 * Generic result wrapper used across the data/domain layers.
 * Kept independent of Kotlin Result so it can carry a [Cause] tree.
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val cause: Cause) : Outcome<Nothing>

    val isOk: Boolean get() = this is Ok

    fun getOrNull(): T? = (this as? Ok)?.value

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    companion object {
        inline fun <T> catching(block: () -> T): Outcome<T> =
            try {
                Ok(block())
            } catch (t: Throwable) {
                Err(Cause.from(t))
            }
    }
}

/** Structured error cause with a user-presentable message. */
sealed class Cause(open val message: String, open val throwable: Throwable? = null) {
    data class Network(override val message: String, override val throwable: Throwable? = null) : Cause(message, throwable)
    data class Protocol(override val message: String, override val throwable: Throwable? = null) : Cause(message, throwable)
    data class Timeout(override val message: String = "Request timed out") : Cause(message)
    data class NotConnected(override val message: String = "Not connected to the dashcam") : Cause(message)
    data class Http(override val message: String, val code: Int = -1) : Cause(message)
    data class Unknown(override val message: String, override val throwable: Throwable? = null) : Cause(message, throwable)

    companion object {
        fun from(t: Throwable): Cause = when (t) {
            is java.net.SocketTimeoutException -> Timeout()
            is java.io.IOException -> Network(t.message ?: "Network error", t)
            else -> Unknown(t.message ?: t.javaClass.simpleName, t)
        }
    }
}
