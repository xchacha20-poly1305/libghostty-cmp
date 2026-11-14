package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.xchacha20_poly1305.libghostty.GhosttyRenderState
import io.github.xchacha20_poly1305.libghostty.GhosttyTerminalSession
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertTrue

class TerminalDrawingTest {

    @Test
    fun fractionalCellBoundariesDoNotBlendWithTheBackground() {
        val columns = 48
        val session = GhosttyTerminalSession(GhosttyTerminalSession.Options(columns = columns, rows = 2))
        try {
            val gradient = buildString {
                repeat(2) {
                    repeat(columns) { column ->
                        append("\u001b[48;2;${35 + column * 4};${150 + column * 2};${220 - column * 2}m ")
                    }
                }
            }
            session.feedOutput(gradient.encodeToByteArray())
            val render = GhosttyRenderState()
            session.withTerminal {
                render.update(it)
            }
            val colors = buildSet {
                for (x in 0 until columns) {
                    add(render.background(x, 0))
                }
            }
            for (scale in listOf(1f, 1.25f, 1.5f, 2f)) {
                Surface.makeRasterN32Premul(1000, 100).use { surface ->
                    surface.canvas.clear(0xFF212121.toInt())
                    CanvasDrawScope().draw(
                        density = Density(1f),
                        layoutDirection = LayoutDirection.Ltr,
                        canvas = surface.canvas.asComposeCanvas(),
                        size = Size(1000f, 100f),
                    ) {
                        scale(scale, scale, pivot = Offset.Zero) {
                            translate(0.3f, 0.4f) {
                                drawBackgrounds(render, cellWidth = 8.35f, cellHeight = 17.5f)
                            }
                        }
                    }
                    surface.makeImageSnapshot().use { image ->
                        image.peekPixels()!!.use { pixels ->
                            // Include horizontal and vertical cell boundaries, excluding the grid's outer edge.
                            for (y in 2 until (34 * scale).toInt()) {
                                for (x in 2 until (399 * scale).toInt()) {
                                    val color = pixels.getColor(x, y)
                                    assertTrue(color in colors, "Unexpected blended color at ($x, $y), scale=$scale")
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            session.close()
        }
    }

}
