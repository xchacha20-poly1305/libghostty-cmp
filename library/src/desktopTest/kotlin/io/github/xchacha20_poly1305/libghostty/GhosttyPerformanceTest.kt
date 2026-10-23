package io.github.xchacha20_poly1305.libghostty

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** Always checks output coalescing; run with GHOSTTY_PERF=1 to print hot-path timings. */
class GhosttyPerformanceTest {

    @Test
    fun outputBurstDrainsLatestStateAndReportsTimings() {
        val columns = 240
        val rows = 80
        val session = GhosttyTerminalSession(GhosttyTerminalSession.Options(columns = columns, rows = rows))
        try {
            repeat(128) { index ->
                session.feedOutput("\u001b]2;title-$index\u0007".encodeToByteArray())
            }
            awaitTitle(session, "title-127")
            assertEquals("title-127", session.title.value)
            if (System.getenv("GHOSTTY_PERF") != "1") return

            session.feedOutput(("x".repeat(columns) + "\r\n").repeat(rows).encodeToByteArray())
            val pixels = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5, 6))
            session.feedOutput("\u001b[3;5H\u001b_Ga=T,f=24,s=2,v=1,i=7,q=2;$pixels\u001b\\".encodeToByteArray())
            val render = GhosttyRenderState()
            session.withTerminal { render.update(it) }
            check(render.kittyPlacements.isNotEmpty())

            val unchangedSamples = measureRepeated(5_000) {
                session.withTerminal { render.update(it) }
            }

            var value = 'a'
            val incrementalSamples = measureRepeated(2_000) {
                value = if (value == 'a') 'b' else 'a'
                session.feedOutput("\u001b[1;1H$value".encodeToByteArray())
                session.withTerminal { render.update(it) }
            }

            val output = "output\r\n".encodeToByteArray()
            val outputSamples = measureRepeated(1_000) {
                session.feedOutput(output)
            }
            session.feedOutput("\u001b]2;perf-drained\u0007".encodeToByteArray())
            awaitTitle(session, "perf-drained")

            println(
                "Ghostty performance: JVM=${System.getProperty("java.vm.name")}, " +
                    "grid=${columns}x$rows; " +
                    "unchangedRender=${format(unchangedSamples)} ns/op; " +
                    "singleCellUpdate=${format(incrementalSamples)} ns/op; " +
                    "smallOutput=${format(outputSamples)} ns/op",
            )
            assertEquals("perf-drained", session.title.value)
        } finally {
            session.close()
        }
    }

    private fun awaitTitle(session: GhosttyTerminalSession, expected: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (session.title.value != expected && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
    }

    private inline fun measureRepeated(iterations: Int, block: () -> Unit): List<Long> =
        List(5) { measure(iterations, block) }

    private inline fun measure(iterations: Int, block: () -> Unit): Long {
        repeat(500) { block() }
        val started = System.nanoTime()
        repeat(iterations) { block() }
        return (System.nanoTime() - started) / iterations
    }

    private fun format(samples: List<Long>): String {
        val sorted = samples.sorted()
        return "median=${sorted[sorted.size / 2]}, samples=${samples.joinToString(",")}"
    }
}
