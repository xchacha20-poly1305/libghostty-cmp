package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import io.github.xchacha20_poly1305.libghostty.GhosttyKey
import io.github.xchacha20_poly1305.libghostty.GhosttyKeyAction
import io.github.xchacha20_poly1305.libghostty.GhosttyMods
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import java.awt.event.KeyEvent as AwtKeyEvent

internal actual fun Modifier.terminalInput(controller: TerminalController): Modifier =
    this then TerminalInputElement(controller)

private data class TerminalInputElement(val controller: TerminalController) :
    ModifierNodeElement<TerminalInputNode>() {
    override fun create() = TerminalInputNode(controller)

    override fun update(node: TerminalInputNode) {
        node.controller = controller
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "terminalInput"
    }
}

/**
 * Translates AWT key events, and keeps an input method session open while focused for composed input such as
 * CJK. The input method sees an editor holding only its composition: committed text goes to the terminal.
 */
private class TerminalInputNode(var controller: TerminalController) :
    Modifier.Node(),
    PlatformTextInputModifierNode,
    FocusEventModifierNode,
    KeyInputModifierNode {

    private var session: Job? = null

    /** Keys held down, to tell repeats from presses and to name the key of KEY_TYPED events. */
    private val held = HashSet<Int>()
    private var lastPressed: GhosttyKey? = null

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused && session == null) {
            session = coroutineScope.launch {
                establishTextInputSession { startInputMethod(CompositionRequest(controller)) }
            }
        } else if (!focusState.isFocused) {
            session?.cancel()
            session = null
            held.clear()
            controller.setComposing(null)
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val awt = event.awtEventOrNull ?: return false
        return when (awt.id) {
            AwtKeyEvent.KEY_PRESSED -> pressed(event, awt)
            AwtKeyEvent.KEY_TYPED -> typed(awt)
            AwtKeyEvent.KEY_RELEASED -> released(event, awt)
            else -> false
        }
    }

    private fun mods(event: KeyEvent, awt: AwtKeyEvent): Int {
        var mods = event.ghosttyMods
        if (awt.keyLocation == AwtKeyEvent.KEY_LOCATION_RIGHT) {
            mods = mods or when (awt.keyCode) {
                AwtKeyEvent.VK_SHIFT -> GhosttyMods.SHIFT_RIGHT
                AwtKeyEvent.VK_CONTROL -> GhosttyMods.CTRL_RIGHT
                AwtKeyEvent.VK_ALT -> GhosttyMods.ALT_RIGHT
                AwtKeyEvent.VK_META -> GhosttyMods.SUPER_RIGHT
                else -> 0
            }
        }
        return mods
    }

    private fun pressed(event: KeyEvent, awt: AwtKeyEvent): Boolean {
        if (clipboardShortcut(awt)) return true
        val key = event.key.ghosttyKey
        val repeat = !held.add(awt.keyCode)
        lastPressed = key
        val mods = mods(event, awt)
        // Plain typing arrives as KEY_TYPED with the layout and dead keys applied; everything else, including
        // modified letters, is encoded from the key.
        if (producesText(awt)) return true
        return controller.onKey(
            key,
            if (repeat) {
                GhosttyKeyAction.REPEAT
            } else {
                GhosttyKeyAction.PRESS
            },
            mods,
            text = null,
            unshiftedCodepoint = key.unshiftedCodepoint,
        )
    }

    private fun typed(awt: AwtKeyEvent): Boolean {
        val char = awt.keyChar
        if (char == AwtKeyEvent.CHAR_UNDEFINED || char.isISOControl() || !isPlainTyping(awt)) return false
        val key = lastPressed ?: keyForChar(char)
        val mods = if (awt.isShiftDown) GhosttyMods.SHIFT else 0
        return controller.onKey(key, GhosttyKeyAction.PRESS, mods, char.toString(), key.unshiftedCodepoint)
    }

    private fun released(event: KeyEvent, awt: AwtKeyEvent): Boolean {
        held.remove(awt.keyCode)
        val key = event.key.ghosttyKey
        return controller.onKey(key, GhosttyKeyAction.RELEASE, mods(event, awt), null, key.unshiftedCodepoint)
    }

    private fun producesText(awt: AwtKeyEvent): Boolean =
        awt.keyChar != AwtKeyEvent.CHAR_UNDEFINED && !awt.keyChar.isISOControl() && isPlainTyping(awt)

    /** Whether the modifiers leave typing alone: none, Shift, or AltGr. */
    private fun isPlainTyping(awt: AwtKeyEvent): Boolean =
        awt.isAltGraphDown || !(awt.isControlDown || awt.isAltDown || awt.isMetaDown)

    /** Copy and paste: Cmd+C and Cmd+V on macOS, Ctrl+Shift+C and Ctrl+Shift+V elsewhere. */
    private fun clipboardShortcut(awt: AwtKeyEvent): Boolean {
        val shortcut = if (hostOs == OS.MacOS) {
            awt.isMetaDown && !awt.isControlDown && !awt.isAltDown
        } else {
            awt.isControlDown && awt.isShiftDown && !awt.isAltDown && !awt.isMetaDown
        }
        if (!shortcut) return false
        when (awt.keyCode) {
            AwtKeyEvent.VK_C -> if (controller.selectionActive) {
                controller.copySelection()
            } else {
                return false
            }
            AwtKeyEvent.VK_V -> controller.paste()
            else -> return false
        }
        return true
    }
}

/** What a key types on a US layout without modifiers, which the Kitty keyboard protocol reports. */
private val GhosttyKey.unshiftedCodepoint: Int
    get() {
        val name = name
        return when {
            name.startsWith("KEY_") && name.length == 5 -> name[4].lowercaseChar().code
            name.startsWith("DIGIT_") -> name[6].code
            else -> when (this) {
                GhosttyKey.SPACE -> ' '.code
                GhosttyKey.MINUS -> '-'.code
                GhosttyKey.EQUAL -> '='.code
                GhosttyKey.BRACKET_LEFT -> '['.code
                GhosttyKey.BRACKET_RIGHT -> ']'.code
                GhosttyKey.BACKSLASH -> '\\'.code
                GhosttyKey.SEMICOLON -> ';'.code
                GhosttyKey.QUOTE -> '\''.code
                GhosttyKey.BACKQUOTE -> '`'.code
                GhosttyKey.COMMA -> ','.code
                GhosttyKey.PERIOD -> '.'.code
                GhosttyKey.SLASH -> '/'.code
                else -> 0
            }
        }
    }

/**
 * The editor the input method edits: just its own composition, which the terminal draws at the cursor until the
 * input method commits it.
 */
@OptIn(ExperimentalComposeUiApi::class)
private class CompositionRequest(private val controller: TerminalController) : PlatformTextInputMethodRequest {

    override val state: TextEditorState = object : TextEditorState {
        override val text: String
            get() = controller.composingText.orEmpty()
        override val length: Int
            get() = text.length

        override fun get(index: Int): Char = text[index]

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)

        override val selection: TextRange
            get() = TextRange(text.length)
        override val composition: TextRange?
            get() = controller.composingText?.let { TextRange(0, it.length) }
    }

    override val value: () -> TextFieldValue = {
        TextFieldValue(state.text, state.selection, state.composition)
    }

    override val imeOptions: ImeOptions = ImeOptions(autoCorrect = false, imeAction = ImeAction.None)

    override val onEditCommand: (List<EditCommand>) -> Unit = {}

    override val onImeAction: ((ImeAction) -> Unit)? = null

    override val textLayoutResult: () -> TextLayoutResult? = { null }

    /** Where candidate windows go: the cursor cell. */
    override val focusedRectInRoot: () -> Rect? = { controller.cursorRectInRoot() }

    override val textFieldRectInRoot: () -> Rect? = { controller.cursorRectInRoot() }

    override val textClippingRectInRoot: () -> Rect? = { null }

    override val unclippedTextOffsetInRoot: () -> Offset? = { null }

    override val editText: (block: TextEditingScope.() -> Unit) -> Unit = { block ->
        Snapshot.withMutableSnapshot { EditingScope().block() }
    }

    private inner class EditingScope : TextEditingScope {
        override fun commitText(text: CharSequence, newCursorPosition: Int) {
            controller.setComposing(null)
            controller.sendText(text.toString())
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int) {
            controller.setComposing(text.toString())
        }

        override fun finishComposingText() {
            val composing = controller.composingText ?: return
            controller.setComposing(null)
            controller.sendText(composing)
        }

        override fun deleteSurroundingTextInCodePoints(lengthBeforeCursor: Int, lengthAfterCursor: Int) {
            repeat(lengthBeforeCursor) { controller.sendKey(GhosttyKey.BACKSPACE) }
        }

        override fun setSelection(start: Int, end: Int) {}

        override fun setComposingRegion(start: Int, end: Int) {}
    }
}
