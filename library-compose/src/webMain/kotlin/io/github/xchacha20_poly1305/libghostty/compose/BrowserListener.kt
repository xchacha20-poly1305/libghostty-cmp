package io.github.xchacha20_poly1305.libghostty.compose

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.clipboard.ClipboardEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

internal class BrowserListener(val key: (Event) -> Unit, val blur: ((Event) -> Unit)? = null)

internal fun installPasteListener(paste: (String) -> Unit): BrowserListener {
    val listener: (Event) -> Unit = { event ->
        val text = (event as? ClipboardEvent)?.clipboardData?.getData("text/plain")
        if (text != null) {
            event.preventDefault()
            event.stopPropagation()
            paste(text)
        }
    }
    document.addEventListener("paste", listener, true)
    return BrowserListener(listener)
}

internal fun removePasteListener(listener: BrowserListener) {
    document.removeEventListener("paste", listener.key, true)
}

internal fun installModifierListener(update: (Boolean, Boolean, Boolean) -> Unit): BrowserListener {
    val key: (Event) -> Unit = { event ->
        if (event is KeyboardEvent) {
            update(event.ctrlKey, event.metaKey, event.shiftKey)
        }
    }
    val blur: (Event) -> Unit = {
        update(false, false, false)
    }
    document.addEventListener("keydown", key, true)
    document.addEventListener("keyup", key, true)
    window.addEventListener("blur", blur)
    return BrowserListener(key, blur)
}

internal fun removeModifierListener(listener: BrowserListener) {
    document.removeEventListener("keydown", listener.key, true)
    document.removeEventListener("keyup", listener.key, true)
    window.removeEventListener("blur", listener.blur)
}
