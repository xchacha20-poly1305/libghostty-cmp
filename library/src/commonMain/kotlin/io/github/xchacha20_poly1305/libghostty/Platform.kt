package io.github.xchacha20_poly1305.libghostty

/** Loads the JNI library that backs [GhosttyVt]. */
internal expect fun loadGhosttyJni()

internal expect fun logError(tag: String, message: String, error: Throwable? = null)
