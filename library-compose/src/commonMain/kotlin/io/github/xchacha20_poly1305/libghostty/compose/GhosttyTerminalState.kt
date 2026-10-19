package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.xchacha20_poly1305.libghostty.GhosttyCellSize
import io.github.xchacha20_poly1305.libghostty.GhosttyKey

/** State and settings of a [GhosttyTerminal], shared with the [GhosttyExtraKeysBar] and [GhosttyDialogs]. */
@Stable
public class GhosttyTerminalState {

    public val extraKeys: GhosttyExtraKeysState = GhosttyExtraKeysState()

    /** Vibrates on BEL. */
    public var bellHapticEnabled: Boolean by mutableStateOf(true)

    /** Shows the software keyboard when the terminal is tapped. */
    public var showImeOnTap: Boolean by mutableStateOf(true)

    /** Shows a button that scrolls back to the bottom while the viewport is in the scrollback. */
    public var showJumpToBottom: Boolean by mutableStateOf(true)

    /**
     * Makes the input method treat the terminal as a text field without suggestions, instead of a raw key target.
     * Some Samsung keyboards keep composing state across raw key targets (termux/termux-app#686).
     */
    public var enforceCharBasedInput: Boolean by mutableStateOf(false)

    /** Matches plain text as a URL, in addition to the hyperlinks the terminal reported. */
    public var urlDetectionEnabled: Boolean by mutableStateOf(true)

    public var urlPattern: Regex by mutableStateOf(Regex("""https?://\S+"""))

    /** The font sizes pinch zoom may reach, in sp. */
    public var fontSizeRangeSp: ClosedFloatingPointRange<Float> by mutableStateOf(8f..48f)

    /** The cell size of the current font in pixels, once the terminal is laid out. */
    public val cellSize: GhosttyCellSize?
        get() = controller?.cellSize

    internal var controller: TerminalController? by mutableStateOf(null)

    /** The font size zoom moved to; reset when the font size parameter changes. */
    internal var zoomedFontSize: TextUnit? by mutableStateOf(null)
    internal var baseFontSize: TextUnit = 14.sp

    internal fun fontSizeSteps(steps: Int) {
        val current = zoomedFontSize ?: baseFontSize
        val next = (current.value + steps).coerceIn(fontSizeRangeSp).sp
        if (next != current) zoomedFontSize = next
    }

    /** Types [text] as if typed on a keyboard, with the armed extra modifiers. */
    public fun sendText(text: String) {
        controller?.sendText(text)
    }

    /** Taps [key] with the armed extra modifiers. */
    public fun sendKey(key: GhosttyKey) {
        controller?.sendKey(key)
    }

    /** Pastes the clipboard, asking first when the text could run commands by itself. */
    public fun paste() {
        controller?.paste()
    }

    /** Gives the terminal input focus and shows the software keyboard. */
    public fun showKeyboard() {
        focusTerminal()
        controller?.showSoftwareKeyboard?.invoke()
    }

    internal var focusRequester: FocusRequester? = null

    internal fun focusTerminal() {
        focusRequester?.requestFocus()
    }

    internal sealed interface DialogRequest {
        class UnsafePaste(val paste: () -> Unit) : DialogRequest

        class ClipboardRead(val respond: (approved: Boolean) -> Unit) : DialogRequest

        class LinkMenu(val url: String, val openLink: () -> Unit, val copyUrl: () -> Unit) : DialogRequest
    }

    internal var dialogRequest: DialogRequest? by mutableStateOf(null)
}

@Composable
public fun rememberGhosttyTerminalState(): GhosttyTerminalState = remember { GhosttyTerminalState() }
