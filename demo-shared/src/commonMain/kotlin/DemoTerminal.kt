package io.github.xchacha20_poly1305.libghostty.demo.shared

import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession

/** An offline simulated shell attached to a real Ghostty terminal. Use on the main thread. */
public class DemoTerminal(onResize: (columns: Int, rows: Int) -> Unit = { _, _ -> }) {
    public val session: GhosttyTerminalSession = GhosttyTerminalSession(
        GhosttyTerminalSession.Options(defaultGraphemeCluster = true),
    )
    private val shell = DemoShell { session.feedOutput(it.encodeToByteArray()) }

    init {
        session.transport = object : GhosttyTerminalSession.Transport {
            override fun sendInput(data: ByteArray) { shell.input(data) }
            override fun sendResize(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {
                onResize(columns, rows)
            }
            override fun close() { shell.close() }
        }
        shell.start()
    }

    public fun runCommand(command: String) { shell.example(command) }

    public fun close() { session.close() }

    public companion object {
        // The end marker requires confirmation even while the demo shell uses bracketed paste.
        public const val PASTE_PROTECTION_EXAMPLE: String = "echo 'Paste protection example: \u001b[201~'"
    }
}
