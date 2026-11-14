package io.github.xchacha20_poly1305.libghostty.compose

import io.github.xchacha20_poly1305.libghostty.GhosttyCellFlags
import io.github.xchacha20_poly1305.libghostty.GhosttyClipboard
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TerminalLinkTest {
    @Test
    fun detectedUrlRangeIncludesWideCellsAndExcludesTrailingPunctuation() = withController(
        "前 https://example.com/中😀). next",
    ) { controller ->
        val link = controller.resolveLink(TerminalController.Cell(5, 0))!!
        assertEquals("https://example.com/中😀", link.uri)
        assertEquals(3..26, link.cells)
        // Both halves of wide characters belong to the link.
        assertEquals(link, controller.resolveLink(TerminalController.Cell(26, 0)))
        assertNull(controller.resolveLink(TerminalController.Cell(27, 0)))
        controller.state.urlDetectionEnabled = false
        assertNull(controller.resolveLink(TerminalController.Cell(5, 0)))
    }

    @Test
    fun osc8RangeCrossesRowsAndDoesNotDependOnUrlDetection() = withController(
        "\u001b]8;;https://example.com\u001b\\" + "x".repeat(65) + "\u001b]8;;\u001b\\.",
    ) { controller ->
        controller.state.urlDetectionEnabled = false
        val link = controller.resolveLink(TerminalController.Cell(2, 1))!!
        assertEquals("https://example.com", link.uri)
        assertEquals(0..64, link.cells)
        assertNull(controller.resolveLink(TerminalController.Cell(5, 1)))
        assertNull(controller.resolveLink(TerminalController.Cell(-1, 0)))
        assertNull(controller.resolveLink(TerminalController.Cell(0, -1)))
    }

    @Test
    fun hoverDecorationIsOptionalAndPreservesOriginalUnderlineWhenInactive() {
        assertFalse(GhosttyTerminalState().linkHoverUnderlineEnabled)
        assertEquals(GhosttyCellFlags.UNDERLINE_SINGLE, linkUnderline(0, true))
        assertEquals(
            GhosttyCellFlags.UNDERLINE_DOUBLE,
            linkUnderline(GhosttyCellFlags.UNDERLINE_SINGLE, true),
        )
        assertEquals(
            GhosttyCellFlags.UNDERLINE_CURLY,
            linkUnderline(GhosttyCellFlags.UNDERLINE_CURLY, false),
        )
    }

    private fun withController(output: String, block: (TerminalController) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = GhosttyTerminalSession(GhosttyTerminalSession.Options(columns = 60, rows = 3))
        val controller = TerminalController(GhosttyTerminalState(), scope)
        controller.clipboard = object : GhosttyClipboard {
            override fun hasText(): Boolean = false
            override fun getText(): String? = null
            override fun setText(text: String) {}
        }
        try {
            controller.attach(session)
            session.feedOutput(output.encodeToByteArray())
            session.withTerminal { controller.render.update(it) }
            block(controller)
        } finally {
            controller.attach(null)
            scope.cancel()
            session.close()
        }
    }
}
