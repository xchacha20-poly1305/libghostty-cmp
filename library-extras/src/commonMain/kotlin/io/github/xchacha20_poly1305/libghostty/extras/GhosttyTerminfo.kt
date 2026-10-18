package io.github.xchacha20_poly1305.libghostty.extras

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Ghostty's `xterm-ghostty` terminfo entry, compiled and bundled as a resource. */
public object GhosttyTerminfo {

    public const val TERM: String = "xterm-ghostty"

    private val entry: ByteArray by lazy {
        GhosttyTerminfo::class.java.getResourceAsStream("terminfo/$TERM")?.use { it.readBytes() }
            ?: error("libghostty-extras is missing its terminfo resource")
    }

    /**
     * Writes the compiled terminfo entry to `terminfoDir/x/xterm-ghostty`. The host points `TERMINFO` (or the
     * terminfo search path of its libc) at [terminfoDir], sets `TERM=xterm-ghostty`, and passes [TERM] as
     * [io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession.Options.terminfoName].
     */
    @Throws(IOException::class)
    public fun install(terminfoDir: File) {
        if (isInstalled(terminfoDir)) return
        val target = target(terminfoDir)
        val dir = File(terminfoDir, "x").apply { mkdirs() }
        val temp = File(dir, "$TERM.tmp")
        temp.writeBytes(entry)
        if (!temp.renameTo(target)) throw IOException("cannot move $temp to $target")
    }

    public fun isInstalled(terminfoDir: File): Boolean {
        val target = target(terminfoDir)
        return target.isFile && sha256(target.readBytes()).contentEquals(sha256(entry))
    }

    private fun target(terminfoDir: File) = File(terminfoDir, "x/$TERM")

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}
