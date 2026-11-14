@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font as platformFont
import androidx.compose.ui.window.ComposeViewport
import io.github.xchacha20_poly1305.libghostty.demo.shared.DemoTerminal
import io.github.xchacha20_poly1305.libghostty.GhosttyWebAssembly
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState
import kotlin.io.encoding.Base64
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        var runtime by remember { mutableStateOf<DemoRuntime?>(null) }
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
            onDispose { terminal?.close() }
        }
        DemoRoot(preferences, runtime)
    }
}

private suspend fun loadRuntime(): DemoRuntime {
    return try {
        val latin = coroutineScope {
            val wasm = async { GhosttyWebAssembly.load() }
            val latinBytes = async { fetchBytes("fonts/NotoSansMono-Latin.woff2") }
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
            async { loadOptionalFont(name, url) }
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
                async { loadOptionalFont("NotoColorEmoji-$index", url) }
            }.mapNotNull { it.await() }
        }
    }
    if (extras.isEmpty()) return FontFamily(latin)
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

private suspend fun fetchBytes(url: String): ByteArray = fetchAsset(url).await().toByteArray()

private suspend fun fetchText(url: String): String = jsToKotlinString(fetchTextAsset(url).await())

private fun JsAny.toByteArray(): ByteArray = Base64.decode(assetBase64(this))

@JsFun("async (url) => { const r = await fetch(url); if (!r.ok) throw new Error('Missing asset: ' + url); return new Uint8Array(await r.arrayBuffer()); }")
private external fun fetchAsset(url: String): Promise<JsAny>
@JsFun("async (url) => { const r = await fetch(url); if (!r.ok) throw new Error('HTTP ' + r.status + ' ' + url); return r.text(); }")
private external fun fetchTextAsset(url: String): Promise<JsString>
@JsFun("""(bytes) => {
  const u8 = bytes;
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < u8.length; i += chunk) {
    binary += String.fromCharCode.apply(null, u8.subarray(i, i + chunk));
  }
  return btoa(binary);
}""")
private external fun assetBase64(bytes: JsAny): String
@JsFun("(value) => value")
private external fun jsToKotlinString(value: JsString): String
@JsFun("() => { document.body.dataset.ready = 'true'; }")
private external fun markReady()
@JsFun("(message) => { console.error(message); }")
private external fun consoleError(message: String)
@JsFun("(message) => { console.warn(message); }")
private external fun consoleWarn(message: String)
