package io.github.xchacha20_poly1305.libghostty.shrinkcheck

import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession

/** OSC 52 clipboard read: the native side answers it through GhosttyVtCallbacks. */
private const val CLIPBOARD_READ = "\u001b]52;c;?\u0007"

/**
 * Uses as little of the library as an app can, so the shrinker deletes everything the rules do not keep.
 * Creating the session loads the JNI library, whose JNI_OnLoad fails if anything it looks up by name is gone.
 */
fun main() {
    val session = GhosttyTerminalSession()
    session.feedOutput(CLIPBOARD_READ.encodeToByteArray())
    println("libghostty loads after R8")
}
