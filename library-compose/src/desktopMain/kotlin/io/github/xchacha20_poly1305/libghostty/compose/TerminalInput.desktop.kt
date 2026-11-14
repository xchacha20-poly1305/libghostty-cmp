package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
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

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        controller.linkModifierPressed = event.isCtrlPressed || event.isMetaPressed
        controller.linkShiftPressed = event.isShiftPressed
        return false
    }

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
