package io.github.xchacha20_poly1305.libghostty

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class GhosttyTerminalSessionTest {

    @Test
    fun clipboardReadWithoutApprovalAnswersEmptyClipboard() {
        val session = GhosttyTerminalSession()
        session.clipboard = object : GhosttyClipboard {
            override fun hasText() = true

            override fun getText() = "secret"

            override fun setText(text: String) {}
        }
        val sent = ByteArrayOutputStream()
        session.transport = object : GhosttyTerminalSession.Transport {
            override fun sendInput(data: ByteArray) {
                sent.write(data)
            }

            override fun sendResize(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {}

            override fun close() {}
        }
        try {
            session.feedOutput("\u001b]52;c;?\u001b\\".toByteArray(Charsets.UTF_8))
            assertEquals("\u001b]52;c;\u001b\\", sent.toString(Charsets.UTF_8.name()))
        } finally {
            session.close()
        }
    }
}
