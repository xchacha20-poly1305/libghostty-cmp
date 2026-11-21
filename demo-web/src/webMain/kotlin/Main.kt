@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalComposeUiApi::class)

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font as platformFont
import androidx.compose.ui.window.ComposeViewport
import io.github.xchacha20_poly1305.libghostty.GhosttyWebAssembly
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState
import io.github.xchacha20_poly1305.libghostty.demo.shared.DemoTerminal
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.fetch.Response
import kotlin.js.Promise

internal class DemoRuntime(
    val terminal: DemoTerminal?,
    val state: GhosttyTerminalState,
    fontFamily: FontFamily,
    val fontResolver: FontFamily.Resolver? = null,
    val latinFont: Font? = null,
    val error: String? = null,
) {
    val session get() = terminal?.session
    var columns by mutableIntStateOf(80)
    var rows by mutableIntStateOf(24)
    var fontFamily by mutableStateOf(fontFamily)

    fun runCommand(command: String) {
        terminal?.runCommand(command)
        state.showKeyboard()
    }

    fun paste() {
        state.paste()
        state.showKeyboard()
    }
}

public fun main() {
    val preferences = DemoPreferences.load()
    ComposeViewport {
        var runtime by remember {
            mutableStateOf<DemoRuntime?>(null)
        }
        LaunchedEffect(Unit) {
            val loaded = loadRuntime()
            runtime = loaded
            markReady()
            val resolver = loaded.fontResolver
            val latin = loaded.latinFont
            if (loaded.error == null && resolver != null && latin != null) {
                try {
                    loaded.fontFamily = loadFallbackFonts(latin, resolver)
                } catch (error: Throwable) {
                    if (error is CancellationException) currentCoroutineContext().ensureActive()
                    consoleWarn(error.message ?: error.toString())
                }
            }
        }
        DisposableEffect(runtime) {
            val terminal = runtime?.terminal
            onDispose {
                terminal?.close()
            }
        }
        DemoRoot(preferences, runtime)
    }
}

private suspend fun loadRuntime(): DemoRuntime {
    return try {
        val latin = coroutineScope {
            val wasm = async {
                GhosttyWebAssembly.load()
            }
            val latinBytes = async {
                fetchBytes("fonts/NotoSansMono-Latin.woff2")
            }
            wasm.await()
            platformFont("NotoSansMono", latinBytes.await())
        }
        val family = FontFamily(latin)
        val resolver = createFontFamilyResolver()
        resolver.preload(family)
        var runtime: DemoRuntime? = null
        val terminal = DemoTerminal { columns, rows ->
            runtime?.columns = columns
            runtime?.rows = rows
        }
        runtime = DemoRuntime(
            terminal = terminal,
            state = GhosttyTerminalState().apply {
                linkHoverUnderlineEnabled = true
            },
            fontFamily = family,
            fontResolver = resolver,
            latinFont = latin,
        )
        runtime
    } catch (error: Throwable) {
        if (error is CancellationException) {
            currentCoroutineContext().ensureActive()
        }
        consoleError(error.message ?: error.toString())
        DemoRuntime(
            terminal = null,
            state = GhosttyTerminalState().apply {
                linkHoverUnderlineEnabled = true
            },
            fontFamily = FontFamily.Monospace,
            error = error.message ?: error.toString(),
        )
    }
}

private val fallbackFontUrls = listOf(
    "https://cdn.jsdelivr.net/fontsource/fonts/noto-sans-sc@5.2.5/chinese-simplified-400-normal.woff2" to "NotoSansSC",
    "https://cdn.jsdelivr.net/fontsource/fonts/noto-sans-jp@5.2.5/japanese-400-normal.woff2" to "NotoSansJP",
    "https://cdn.jsdelivr.net/fontsource/fonts/noto-sans-kr@5.2.5/korean-400-normal.woff2" to "NotoSansKR",
)

private val fontSrc = Regex("""url\(["']?(https://fonts\.gstatic\.com/[^)"']+)["']?\)""")

private suspend fun loadFallbackFonts(latin: Font, resolver: FontFamily.Resolver): FontFamily {
    val extras = mutableListOf<Font>()
    coroutineScope {
        val cjk = fallbackFontUrls.map { (url, name) ->
            async {
                loadOptionalFont(name, url)
            }
        }
        val emojiUrls = async {
            try {
                googleFontUrls("Noto+Color+Emoji")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                consoleWarn(error.message ?: "Noto Color Emoji")
                emptyList()
            }
        }
        extras += cjk.mapNotNull { it.await() }
        extras += coroutineScope {
            emojiUrls.await().mapIndexed { index, url ->
                async {
                    loadOptionalFont("NotoColorEmoji-$index", url)
                }
            }.mapNotNull {
                it.await()
            }
        }
    }
    if (extras.isEmpty()) {
        return FontFamily(latin)
    }
    val family = FontFamily(listOf(latin) + extras)
    resolver.preload(family)
    return family
}

private suspend fun loadOptionalFont(name: String, url: String): Font? {
    return try {
        platformFont(name, fetchBytes(url))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        consoleWarn(error.message ?: url)
        null
    }
}

private suspend fun googleFontUrls(family: String): List<String> {
    val css = fetchText("https://fonts.googleapis.com/css2?family=$family&display=swap")
    return fontSrc.findAll(css).map { it.groupValues[1] }.distinct().toList()
}

// The pinned Kotlin/JS Window.fetch binding requires RequestInit, whose defaults become null fields.
private external fun fetch(url: String): Promise<Response>

internal suspend fun fetchBytes(url: String): ByteArray {
    val response = fetch(url).await()
    check(response.ok) { "Missing asset: $url (HTTP ${response.status})" }
    return Int8Array(response.arrayBuffer().await()).toByteArray()
}

internal suspend fun fetchText(url: String): String {
    val response = fetch(url).await()
    check(response.ok) { "HTTP ${response.status} $url" }
    return response.text().await().toString()
}

internal fun markReady() {
    document.body?.setAttribute("data-ready", "true")
}

internal fun consoleError(message: String) { console.error(message) }
internal fun consoleWarn(message: String) { console.warn(message) }

private external object console {
    fun error(message: String)
    fun warn(message: String)
}
