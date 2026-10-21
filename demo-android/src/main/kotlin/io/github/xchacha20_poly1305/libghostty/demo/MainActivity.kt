package io.github.xchacha20_poly1305.libghostty.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyDialogs
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKeysBar
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyJumpToBottomButton
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyScrollIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.rememberGhosttyTerminalState
import io.github.xchacha20_poly1305.libghostty.extras.GhosttyThemeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val session = remember { echoSession() }
                DisposableEffect(session) { onDispose { session.close() } }
                val state = rememberGhosttyTerminalState()
                val theme = remember { GhosttyThemeStore.loadTheme(GhosttyThemeStore.DEFAULT_DARK_THEME) }
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                    Box(Modifier.weight(1f)) {
                        GhosttyTerminal(session, Modifier.fillMaxSize(), state = state, theme = theme)
                        GhosttyScrollIndicator(state, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                        GhosttyJumpToBottomButton(state, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                    }
                    GhosttyExtraKeysBar(state)
                }
                GhosttyDialogs(state)
            }
        }
    }
}

private fun echoSession(): GhosttyTerminalSession {
    val session = GhosttyTerminalSession()
    session.transport = object : GhosttyTerminalSession.Transport {
        override fun sendInput(data: ByteArray) {
            val echoed = data.toString(Charsets.UTF_8).replace("\r", "\r\n").replace("\u007f", "\b \b")
            session.feedOutput(echoed.toByteArray())
        }

        override fun sendResize(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {}

        override fun close() {}
    }
    session.feedOutput(
        (
            "\u001b]2;libghostty demo\u0007" +
                "\u001b[1;31mbold red\u001b[0m \u001b[4:3mcurly\u001b[0m \u001b[7minverse\u001b[0m \u001b[3mitalic\u001b[0m\r\n" +
                "中文 宽字符 émoji 🎉 \u001b]8;;https://ghostty.org\u001b\\hyperlink\u001b]8;;\u001b\\\r\n" +
                (0..15).joinToString("") { "\u001b[48;5;${it}m  " } + "\u001b[0m\r\n" +
                "Type here; input echoes back.\r\n$ "
            ).toByteArray(),
    )
    return session
}
