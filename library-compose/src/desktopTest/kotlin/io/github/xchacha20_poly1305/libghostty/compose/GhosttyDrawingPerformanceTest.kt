package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.ui.graphics.asComposeCanvas
import java.lang.management.ManagementFactory
import kotlin.test.Test
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.FontStyle as SkFontStyle

/** Opt-in desktop drawing benchmark; run with GHOSTTY_PERF=1 and :library-compose:desktopTest. */
class GhosttyDrawingPerformanceTest {

    @Test
    fun reportAsciiCellDrawCost() {
        if (System.getenv("GHOSTTY_PERF") != "1") return

        val fontMgr = FontMgr.default
        val typeface = fontMgr.matchFamilyStyle("monospace", SkFontStyle.NORMAL)
            ?: fontMgr.matchFamilyStyle(fontMgr.getFamilyName(0), SkFontStyle.NORMAL)!!
        val painter = SkiaGlyphPainter(typeface, null, null, null, 16f)
        val surface = Surface.makeRasterN32Premul(800, 480)
        val canvas = surface.canvas.asComposeCanvas()
        val row = "echo terminal throughput 0123456789".repeat(3).take(108).toCharArray()
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        try {
            val samples = List(5) {
                repeat(20) { drawGrid(painter, canvas, row) }
                val threadId = Thread.currentThread().id
                val allocatedBefore = bean?.takeIf { it.isThreadAllocatedMemorySupported }
                    ?.getThreadAllocatedBytes(threadId)
                val started = System.nanoTime()
                repeat(100) { drawGrid(painter, canvas, row) }
                val elapsed = System.nanoTime() - started
                val allocatedAfter = bean?.takeIf { it.isThreadAllocatedMemorySupported }
                    ?.getThreadAllocatedBytes(threadId)
                Sample(
                    nanosPerGrid = elapsed / 100,
                    bytesPerGrid = if (allocatedBefore != null && allocatedAfter != null) {
                        (allocatedAfter - allocatedBefore) / 100
                    } else {
                        -1
                    },
                )
            }
            println("Ghostty desktop drawing: grid=108x24; samples=$samples")
        } finally {
            surface.close()
        }
    }

    private fun drawGrid(painter: GlyphPainter, canvas: androidx.compose.ui.graphics.Canvas, row: CharArray) {
        repeat(24) { y ->
            repeat(row.size) { x ->
                painter.draw(canvas, row, x, 1, x * 8f, y * 18f + 14f, 0xFFFFFFFF.toInt(), false, false)
            }
        }
    }

    private data class Sample(val nanosPerGrid: Long, val bytesPerGrid: Long)
}
