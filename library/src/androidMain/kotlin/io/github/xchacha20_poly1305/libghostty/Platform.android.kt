package io.github.xchacha20_poly1305.libghostty

import android.util.Log

internal actual fun loadGhosttyJni() {
    System.loadLibrary("ghostty_jni")
}

internal actual fun logError(tag: String, message: String, error: Throwable?) {
    Log.e(tag, message, error)
}
