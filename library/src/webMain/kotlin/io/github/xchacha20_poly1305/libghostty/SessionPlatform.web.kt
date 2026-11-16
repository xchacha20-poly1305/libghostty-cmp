package io.github.xchacha20_poly1305.libghostty

// Browser sessions run on the browser event loop. No Kotlin threads share the terminal.
internal actual class SessionLock {
    actual fun <T> withLock(block: () -> T): T = block()
}

internal actual fun fileUriPath(raw: String): String? {
    val rest = raw.removePrefix("file://")
    val start = rest.indexOf('/')
    if (start < 0) return null
    val path = rest.substring(start).substringBefore('?').substringBefore('#')
    return decodeUriPath(path)
}


private external fun decodeURIComponent(text: String): String

private fun decodeUriPath(text: String): String = try {
    decodeURIComponent(text)
} catch (_: Throwable) {
    text
}

internal actual fun logError(tag: String, message: String, error: Throwable?) {
    println("$tag: $message ${error?.message.orEmpty()}")
}
