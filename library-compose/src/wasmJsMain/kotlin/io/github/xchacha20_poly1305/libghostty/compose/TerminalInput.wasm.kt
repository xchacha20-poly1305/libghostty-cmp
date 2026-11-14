@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.unit.IntSize
import io.github.xchacha20_poly1305.libghostty.GhosttyKey
import io.github.xchacha20_poly1305.libghostty.GhosttyKeyAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal actual fun Modifier.terminalInput(controller: TerminalController): Modifier =
    this then WebInputElement(controller)

private data class WebInputElement(val controller: TerminalController) : ModifierNodeElement<WebInputNode>() {
    override fun create(): WebInputNode = WebInputNode(controller)
    override fun update(node: WebInputNode) {
        node.controller = controller
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "terminalInput"
    }
}

private class WebInputNode(var controller: TerminalController) : Modifier.Node(),
    PlatformTextInputModifierNode, FocusEventModifierNode, KeyInputModifierNode, CompositionLocalConsumerModifierNode,
    PointerInputModifierNode {
    private var session: Job? = null
    private var pasteListener: JsAny? = null
    private var modifierListener: JsAny? = null
    private val request = CompositionRequest(controller, retainCommittedText = true)
    private val held = HashSet<GhosttyKey>()
    private var primaryMousePressed = false

    override fun onAttach() {
        controller.showSoftwareKeyboard = ::showKeyboard
        // Compose's backing textarea batches keydown events and drops keyup events.
        // Observe DOM modifiers directly so releasing Ctrl updates a stationary hover.
        modifierListener = installModifierListener { ctrl, meta, shift ->
            controller.linkModifierPressed = ctrl || meta
            controller.linkShiftPressed = shift
        }
    }

    private fun showKeyboard() {
        currentValueOf(LocalSoftwareKeyboardController)?.show()
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Final) return
        if (pointerEvent.type == PointerEventType.Press &&
            pointerEvent.changes.any { it.type == PointerType.Mouse } && pointerEvent.buttons.isPrimaryPressed
        ) {
            primaryMousePressed = true
        } else if (pointerEvent.type == PointerEventType.Release && primaryMousePressed) {
            primaryMousePressed = false
            // A DOM canvas click can move browser focus without changing Compose's focused node.
            // Restore the backing IME editor after the browser's mouse-down focus action.
            showKeyboard()
        }
    }

    override fun onCancelPointerInput() {
        primaryMousePressed = false
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused && session == null) {
            pasteListener = installPasteListener { controller.paste(it) }
            session = coroutineScope.launch {
                establishTextInputSession { startInputMethod(request) }
            }
        } else if (!focusState.isFocused) {
            stop()
        }
    }

    override fun onDetach() {
        modifierListener?.let(::removeModifierListener)
        modifierListener = null
        controller.linkModifierPressed = false
        if (controller.showSoftwareKeyboard == ::showKeyboard) {
            controller.showSoftwareKeyboard = {}
        }
        stop()
    }

    private fun stop() {
        session?.cancel(); session = null
        pasteListener?.let(::removePasteListener)
        pasteListener = null
        held.clear(); controller.setComposing(null)
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = event.key.ghosttyKey
        val press = event.type == KeyEventType.KeyDown
        val shortcut = (event.isCtrlPressed && event.isShiftPressed) || event.isMetaPressed
        if (press && shortcut && key == GhosttyKey.KEY_C && controller.selectionActive) {
            controller.copySelection()
            return true
        }
        if (key == GhosttyKey.KEY_V && (event.isCtrlPressed || event.isMetaPressed) && !event.isAltPressed) {
            // Browser paste events carry the text for both Cmd+V and Ctrl+Shift+V.
            // Reading the async API here would paste twice in a headed browser.
            return false
        }
        if (!event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && producesText(key)) {
            return false
        }
        if (press && key == GhosttyKey.ENTER) {
            request.reset()
        }
        val action = if (!press) {
            held.remove(key); GhosttyKeyAction.RELEASE
        } else if (held.add(key)) {
            GhosttyKeyAction.PRESS
        } else {
            GhosttyKeyAction.REPEAT
        }
        return controller.onKey(key, action, event.ghosttyMods, null, key.unshiftedCodepoint)
    }

    private fun producesText(key: GhosttyKey): Boolean = key.name.startsWith("KEY_")
            || key.name.startsWith("DIGIT_")
            || key in setOf(
        GhosttyKey.SPACE, GhosttyKey.MINUS,
        GhosttyKey.EQUAL, GhosttyKey.BRACKET_LEFT, GhosttyKey.BRACKET_RIGHT, GhosttyKey.BACKSLASH,
        GhosttyKey.SEMICOLON, GhosttyKey.QUOTE, GhosttyKey.BACKQUOTE, GhosttyKey.COMMA,
        GhosttyKey.PERIOD, GhosttyKey.SLASH
    )
}

@JsFun(
    """(paste) => {
    const listener = event => { const text = event.clipboardData?.getData('text/plain');
        if (text != null) { event.preventDefault(); event.stopPropagation(); paste(text); } };
    document.addEventListener('paste', listener, true); return listener;
}"""
)
private external fun installPasteListener(paste: (String) -> Unit): JsAny

@JsFun("(listener) => document.removeEventListener('paste', listener, true)")
private external fun removePasteListener(listener: JsAny)


@JsFun(
    """(update) => {
    const key = event => update(event.ctrlKey, event.metaKey, event.shiftKey);
    const blur = () => update(false, false, false);
    document.addEventListener('keydown', key, true);
    document.addEventListener('keyup', key, true);
    window.addEventListener('blur', blur);
    return {key, blur};
}"""
)
private external fun installModifierListener(update: (Boolean, Boolean, Boolean) -> Unit): JsAny

@JsFun(
    """(listener) => {
    document.removeEventListener('keydown', listener.key, true);
    document.removeEventListener('keyup', listener.key, true);
    window.removeEventListener('blur', listener.blur);
}"""
)
private external fun removeModifierListener(listener: JsAny)
