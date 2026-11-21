package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.xchacha20_poly1305.libghostty.compose.resources.Res
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_cancel
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_clipboard_read_allow
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_clipboard_read_message
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_clipboard_read_title
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_copy_link
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_open_link
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_paste
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_paste_unsafe_message
import io.github.xchacha20_poly1305.libghostty.compose.resources.ghostty_paste_unsafe_title
import org.jetbrains.compose.resources.stringResource

/** Shows the prompts a [GhosttyTerminal] raises: unsafe pastes, clipboard reads and link menus. */
@Composable
public fun GhosttyDialogs(state: GhosttyTerminalState) {
    GhosttyDialogs(state) { text, confirm, dismiss ->
        PasteWarningDialog(text, confirm, dismiss)
    }
}

/**
 * Shows the terminal prompts with a custom unsafe paste dialog. [pasteConfirmation] receives the original
 * clipboard text and callbacks to confirm or dismiss the current request. Call the dismissal callback for
 * every cancellation, including back, Escape and outside clicks.
 *
 * @since 0.5.2
 */
@Composable
public fun GhosttyDialogs(
    state: GhosttyTerminalState,
    pasteConfirmation: @Composable (text: String, confirm: () -> Unit, dismiss: () -> Unit) -> Unit,
) {
    when (val request = state.dialogRequest) {
        null -> {}

        is GhosttyTerminalState.DialogRequest.UnsafePaste -> pasteConfirmation(
            request.confirmation.text,
            {
                if (state.dialogRequest === request) {
                    state.dialogRequest = null
                    if (request.confirmation.confirm()) request.onConfirmed()
                }
            },
            {
                if (state.dialogRequest === request) {
                    state.dialogRequest = null
                    request.confirmation.cancel()
                }
            },
        )

        is GhosttyTerminalState.DialogRequest.ClipboardRead -> AlertDialog(
            onDismissRequest = {
                state.dialogRequest = null
                request.respond(false)
            },
            title = { Text(stringResource(Res.string.ghostty_clipboard_read_title)) },
            text = { Text(stringResource(Res.string.ghostty_clipboard_read_message)) },
            confirmButton = {
                TextButton(onClick = {
                    state.dialogRequest = null
                    request.respond(true)
                }) {
                    Text(stringResource(Res.string.ghostty_clipboard_read_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    state.dialogRequest = null
                    request.respond(false)
                }) {
                    Text(stringResource(Res.string.ghostty_cancel))
                }
            },
        )

        is GhosttyTerminalState.DialogRequest.LinkMenu -> AlertDialog(
            onDismissRequest = { state.dialogRequest = null },
            title = { Text(request.url) },
            text = {
                Column {
                    Text(
                        stringResource(Res.string.ghostty_open_link),
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.dialogRequest = null
                                request.openLink()
                            }
                            .padding(vertical = 12.dp),
                    )
                    Text(
                        stringResource(Res.string.ghostty_copy_link),
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.dialogRequest = null
                                request.copyUrl()
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { state.dialogRequest = null }) {
                    Text(stringResource(Res.string.ghostty_cancel))
                }
            },
        )
    }
}

@Composable
private fun PasteWarningDialog(text: String, confirm: () -> Unit, dismiss: () -> Unit) {
    val previewLimit = 4096
    val preview = text.take(previewLimit)
        .replace('\u001b', '␛')
        .replace('\r', '␍') + if (text.length > previewLimit) "…" else ""
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(Res.string.ghostty_paste_unsafe_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.ghostty_paste_unsafe_message))
                Text(
                    preview,
                    Modifier.padding(top = 12.dp).heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = confirm) { Text(stringResource(Res.string.ghostty_paste)) }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(Res.string.ghostty_cancel)) }
        },
    )
}
