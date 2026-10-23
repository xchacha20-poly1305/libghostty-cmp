package io.github.xchacha20_poly1305.libghostty.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyDialogs
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyJumpToBottomButton
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyProgressIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyScrollIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.rememberGhosttyTerminalState
import kotlin.concurrent.thread

fun main() = application {
    var generation by remember { mutableIntStateOf(0) }
    val session = remember(generation) { ShellSession.start() }
    DisposableEffect(session) { onDispose { session.close() } }
    val title by session.title.collectAsState()
    Window(onCloseRequest = ::exitApplication, title = title ?: "libghostty demo") {
        MaterialTheme(colorScheme = darkColorScheme()) {
            key(session) {
                val state = rememberGhosttyTerminalState()
                Column(Modifier.fillMaxSize()) {
                    GhosttyProgressIndicator(session, Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxSize()) {
                        GhosttyTerminal(session, Modifier.fillMaxSize(), state = state)
                        GhosttyScrollIndicator(state, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
                        GhosttyJumpToBottomButton(state, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                        ExitNotification(
                            session,
                            onRestart = { generation++ },
                            onClose = ::exitApplication,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
                GhosttyDialogs(state)
            }
        }
    }
}

@Composable
private fun ExitNotification(
    session: GhosttyTerminalSession,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exitStatus = session.exitStatus.collectAsState().value ?: return
    Surface(modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shell exited with status $exitStatus")
            TextButton(onClick = onRestart) { Text("Restart") }
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

private object ShellSession {
    fun start(): GhosttyTerminalSession {
        val session = GhosttyTerminalSession()
        val shell = System.getenv("SHELL")
            ?: if (System.getProperty("os.name").startsWith("Windows")) {
                "cmd.exe"
            } else {
                "/bin/sh"
            }
        val process: PtyProcess = PtyProcessBuilder(arrayOf(shell))
            .setEnvironment(System.getenv() + mapOf("TERM" to "xterm-256color", "COLORTERM" to "truecolor"))
            .setInitialColumns(session.columns.fastCoerceAtLeast(80))
            .setInitialRows(session.rows.fastCoerceAtLeast(24))
            .start()
        session.transport = object : GhosttyTerminalSession.Transport {
            override fun sendInput(data: ByteArray) {
                process.outputStream.write(data)
                process.outputStream.flush()
            }

            override fun sendResize(columns: Int, rows: Int, widthPixels: Int, heightPixels: Int) {
                process.winSize = WinSize(columns, rows)
            }

            override fun close() {
                process.destroy()
            }
        }
        thread(name = "pty-reader", isDaemon = true) {
            val buffer = ByteArray(64 * 1024)
            val input = process.inputStream
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                session.feedOutput(buffer, 0, read)
            }
            session.finish(process.waitFor())
        }
        return session
    }
}
