# libghostty-cmp

Use the Ghostty terminal emulator in an Android, desktop JVM, or browser application. This API reference follows the `cmp` branch.

**The API is unstable and may change in future releases.** Updates may require changes to your application.

- <a href="demo/js/">Kotlin/JS demo</a>
- <a href="demo/wasm/">Kotlin/Wasm demo</a>

## Quick start

### Install from Maven Central

Add `mavenCentral()` to your Gradle repositories. These coordinates use the published version `0.5.1`. In a Kotlin Multiplatform project, add each dependency to a source set whose targets the library supports.

```kotlin
dependencies {
    implementation("io.github.xchacha20-poly1305:libghostty:0.5.1")
    // Optional Compose Multiplatform UI
    implementation("io.github.xchacha20-poly1305:libghostty-compose:0.5.1")
    // Optional themes, terminfo, and font utilities on Android and desktop JVM
    implementation("io.github.xchacha20-poly1305:libghostty-extras:0.5.1")
}
```

### Feed output to a terminal session

Call this example from the main thread on Android or desktop JVM. It feeds a line of green text, reads the visible terminal text, and closes the session. For an interactive terminal, connect a `GhosttyTerminalSession.Transport` to your process or connection. The transport receives keyboard input and resize events. Send the process output back with `feedOutput`.

```kotlin
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession

fun printTerminalOutput() {
    val session = GhosttyTerminalSession()
    try {
        session.feedOutput("\u001B[32mHello, terminal!\u001B[0m\r\n".encodeToByteArray())
        println(session.screenText())
    } finally {
        session.close()
    }
}
```

### Render the terminal with Compose

Pass a live session to the terminal composable and give it space in the layout. Keep the session open while the UI uses it, then close it when its owner is disposed.

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal

@Composable
fun TerminalPane(session: GhosttyTerminalSession) {
    GhosttyTerminal(session, Modifier.fillMaxSize())
}
```

### Initialize the browser backend

Both Kotlin/JS and Kotlin/Wasm use the Ghostty WebAssembly engine. Serve the matching `ghostty-vt.wasm` binary with your application. It is published with the `ghostty-vt` classifier and `wasm` extension on `libghostty-js` and `libghostty-wasm-js`. Await the loader in a coroutine before creating the first session.

```kotlin
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import io.github.xchacha20_poly1305.libghostty.GhosttyWebAssembly

suspend fun createBrowserTerminal(): GhosttyTerminalSession {
    GhosttyWebAssembly.load("ghostty-vt.wasm")
    return GhosttyTerminalSession()
}
```
