package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import io.github.xchacha20_poly1305.libghostty.GhosttyClipboard

/**
 * Measures and draws terminal text with a platform font: one cell at a time, at exact cell positions, which a
 * paragraph layout cannot do fast enough for a full screen every frame.
 */
internal interface GlyphPainter {
    val cellWidth: Float
    val cellHeight: Int

    /** Distance from the top of a cell to the text baseline. */
    val baseline: Float

    fun measure(text: String): Float

    fun draw(
        canvas: Canvas,
        text: CharArray,
        offset: Int,
        length: Int,
        x: Float,
        baselineY: Float,
        color: Int,
        bold: Boolean,
        italic: Boolean,
    )
}

internal expect fun GlyphPainter(fontFamily: FontFamily, fontSizePx: Float, resolver: FontFamily.Resolver): GlyphPainter

internal expect fun imageBitmapOf(width: Int, height: Int, argb: IntArray): ImageBitmap

@Composable
internal expect fun rememberPlatformClipboard(): GhosttyClipboard

/**
 * Routes input to the terminal while it is focused: hardware keys and the platform input method, including its
 * composition.
 */
internal expect fun Modifier.terminalInput(controller: TerminalController): Modifier

/** Shows a platform magnifier over [center] while it is specified. */
internal expect fun Modifier.selectionMagnifier(center: () -> Offset): Modifier
