@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.xchacha20_poly1305.libghostty

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.clipboard.Clipboard

/**
 * Browser clipboard policy. Program reads are denied. Program writes require explicit confirmation.
 *
 * @param approveProgramWrite decides whether to let the terminal program replace the clipboard.
 * @since 0.5.0
 */
public class GhosttyBrowserClipboard(
    private val approveProgramWrite: (String) -> Boolean = { confirmClipboardWrite() },
) : GhosttyClipboard {
    /**
     * Reports no clipboard text to terminal programs.
     *
     * @since 0.5.0
     */
    override fun hasText(): Boolean = false

    /**
     * Denies synchronous clipboard reads.
     *
     * @since 0.5.0
     */
    override fun getText(): String? = null

    /**
     * Reads the clipboard asynchronously for a user paste gesture.
     *
     * @since 0.5.0
     */
    override fun readText(onResult: (String?) -> Unit) {
        readClipboardForUser(onResult)
    }

    /**
     * Copies [text] to the browser clipboard from a user action.
     *
     * @since 0.5.0
     */
    override fun setText(text: String) {
        writeClipboard(text)
    }

    /**
     * Writes [text] to the browser clipboard after the approval callback allows it.
     *
     * @since 0.5.0
     */
    override fun setTextForProgram(text: String) {
        if (approveProgramWrite(text)) {
            setText(text)
        }
    }

    /**
     * Must be called from a user gesture. Ordinary browser paste events also work when this API is unavailable.
     *
     * @since 0.5.0
     */
    public suspend fun readText(): String = readClipboardText()
}

private fun confirmClipboardWrite(): Boolean {
    return window.confirm("Allow the terminal program to replace your clipboard?")
}

private fun copyWithTextArea(text: String) {
    val input = document.createElement("textarea") as HTMLTextAreaElement
    input.value = text
    input.style.position = "fixed"
    input.style.opacity = "0"
    document.body?.appendChild(input)
    try {
        input.select()
        document.execCommand("copy")
    } finally {
        input.remove()
    }
}

// The browser bindings declare this property non-null, but insecure/older contexts may omit it.
private fun browserClipboard(): Clipboard? = try {
    window.navigator.clipboard
} catch (_: Throwable) {
    null
}

private fun writeClipboard(text: String) {
    val clipboard = browserClipboard()
    if (clipboard == null) {
        copyWithTextArea(text)
    } else {
        try {
            clipboard.writeText(text).catch {
                copyWithTextArea(text)
                null
            }
        } catch (_: Throwable) {
            copyWithTextArea(text)
        }
    }
}

private suspend fun readClipboardText(): String = window.navigator.clipboard.readText().await().toString()

private fun readClipboardForUser(onResult: (String?) -> Unit) {
    val clipboard = browserClipboard()
    if (clipboard == null) {
        onResult(null)
        return
    }
    try {
        clipboard.readText().then {
            onResult(it.toString())
            null
        }.catch {
            onResult(null)
            null
        }
    } catch (_: Throwable) {
        onResult(null)
    }
}
