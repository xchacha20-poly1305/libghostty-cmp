@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.xchacha20_poly1305.libghostty

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.fetch.Response
import kotlin.js.Promise

/**
 * Loads the Ghostty Wasm core before creating a session. Browser event-loop only.
 *
 * @since 0.5.0
 */
public object GhosttyWebAssembly {
    /**
     * Load the binary shipped with this library. Hosts may override the asset URL.
     *
     * @since 0.5.0
     */
    public suspend fun load(url: String = "ghostty-vt.wasm") { loadGhosttyWasm(url) }

    /**
     * Initialize from cached or embedded bytes without networking.
     *
     * @since 0.5.0
     */
    public suspend fun initialize(bytes: ByteArray) { initializeGhosttyWasm(bytes) }
}

internal data class WasmResult(val value: Int, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WasmResult

        if (value != other.value) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

// The pinned Kotlin/JS Window.fetch binding requires RequestInit, whose defaults become null fields.
private external fun fetch(url: String): Promise<Response>

private suspend fun loadGhosttyWasm(url: String) {
    val response = fetch(url).await()
    check(response.ok) { "Cannot load Ghostty: ${response.status}" }
    initializeGhosttyWasm(Int8Array(response.arrayBuffer().await()).toByteArray())
}

internal expect suspend fun initializeGhosttyWasm(bytes: ByteArray)
internal expect fun callWasm(op: Int, handle: Int, a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, input: ByteArray): WasmResult

internal actual fun platformBackend(): GhosttyBackend = GhosttyWasmBackend

// Mirrored by native/src/wasm.zig; bump the ABI version if the wire contract changes.
private enum class WasmOperation(val code: Int) {
    CREATE(0),
    FREE(1),
    WRITE(2),
    RESIZE(3),
    TAKE_EVENTS(4),
    TITLE(5),
    TAKE_CLIPBOARD(6),
    PWD(7),
    TAKE_NOTIFICATION(8),
    PROGRESS(9),
    COLOR_SCHEME(10),
    FOCUS(11),
    HYPERLINK(12),
    THEME(13),
    PARSE_COLOR(15),
    SCROLL(16),
    SCROLL_BOTTOM(17),
    SCROLLBAR(18),
    VIEWPORT_STATE(19),
    SNAPSHOT(20),
    KITTY_PLACEMENTS(21),
    KITTY_IMAGE(22),
    ENCODE_KEY(23),
    ENCODE_MOUSE(24),
    SELECT_WORD(25),
    SELECT_ALL(26),
    SET_SELECTION(27),
    CLEAR_SELECTION(28),
    SELECTION_TEXT(29),
    VIEWPORT_TEXT(30),
    IS_PASTE_SAFE(31),
    ENCODE_PASTE(32),
    TRANSCRIPT(33),
    BACKGROUND(34),
    IS_PASTE_SAFE_WITH_MODE(35),
}

private object GhosttyWasmBackend : GhosttyBackend {

    private fun call(
        op: WasmOperation,
        handle: Long = 0,
        a: Int = 0,
        b: Int = 0,
        c: Int = 0,
        d: Int = 0,
        e: Int = 0,
        f: Int = 0,
        data: ByteArray = byteArrayOf()
    ): WasmResult = callWasm(op.code, handle.toInt(), a, b, c, d, e, f, data)

    private fun WasmResult.data(): ByteArray? = bytes.takeIf { it.isNotEmpty() }

    private fun WasmResult.ints(): IntArray? = data()?.let { data ->
        IntArray(data.size / 4) { index ->
            readInt(data, index * 4)
        }
    }

    private fun WasmResult.longs(): LongArray? = data()?.let { data ->
        LongArray(data.size / 8) { index ->
            (readInt(data, index * 8).toLong() and 0xffffffffL) or
                    (readInt(data, index * 8 + 4).toLong() shl 32)
        }
    }

    // little binary
    private fun readInt(data: ByteArray, offset: Int): Int {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    override fun nativeCreate(cols: Int, rows: Int, maxScrollbackLines: Long, xtversion: String,
        terminfoName: String?, cursorStyle: Int, cursorBlink: Boolean, graphemeCluster: Boolean,
        callbacks: GhosttyVtCallbacks?): Long {
        return call(
            op = WasmOperation.CREATE,
            a = cols,
            b = rows,
            c = maxScrollbackLines.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            d = cursorStyle,
            e = if (cursorBlink) 1 else 0,
            f = if (graphemeCluster) 1 else 0,
            data = (xtversion + '\u0000' + terminfoName.orEmpty()).encodeToByteArray()
        ).value.toUInt().toLong().also {
                check(it != 0L) { "Cannot create Ghostty Wasm terminal" }
            }
    }

    override fun nativeFree(handle: Long) {
        call(WasmOperation.FREE, handle)
    }

    override fun nativeWrite(handle: Long, data: ByteArray, offset: Int, length: Int): ByteArray? {
        require(offset >= 0 && length >= 0 && offset <= data.size - length)
        return call(WasmOperation.WRITE, handle, data = data.copyOfRange(offset, offset + length)).data()
    }

    override fun nativeResize(handle: Long, cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        call(WasmOperation.RESIZE, handle, cols, rows, cellWidthPx, cellHeightPx)
    }

    override fun nativeTakeEventFlags(handle: Long): Int {
        return call(WasmOperation.TAKE_EVENTS, handle).value
    }

    override fun nativeGetTitle(handle: Long): ByteArray? {
        return call(WasmOperation.TITLE, handle).data()
    }

    override fun nativeTakeClipboard(handle: Long): ByteArray? {
        return call(WasmOperation.TAKE_CLIPBOARD, handle).data()
    }

    override fun nativeGetPwd(handle: Long): ByteArray? {
        return call(WasmOperation.PWD, handle).data()
    }

    override fun nativeTakeNotification(handle: Long): ByteArray? {
        return call(WasmOperation.TAKE_NOTIFICATION, handle).data()
    }

    override fun nativeGetProgress(handle: Long): IntArray? {
        return call(WasmOperation.PROGRESS, handle).ints()
    }

    override fun nativeSetColorScheme(handle: Long, dark: Boolean): ByteArray?  {
        return call(
            op = WasmOperation.COLOR_SCHEME,
            handle = handle,
            a = if (dark) 1 else 0
        ).data()
    }

    override fun nativeEncodeFocus(handle: Long, gained: Boolean): ByteArray?  {
        return call(WasmOperation.FOCUS, handle, if (gained) 1 else 0).data()
    }

    override fun nativeHyperlinkAt(handle: Long, col: Int, row: Int): ByteArray?  {
        return call(WasmOperation.HYPERLINK, handle, col, row).data()
    }

    override fun nativeSetTheme(handle: Long, foreground: Long, background: Long, cursor: Long, palette: LongArray?) {
        val values = longArrayOf(foreground, background, cursor) + (palette ?: longArrayOf())
        val data = ByteArray(values.size * 8) { index ->
            (values[index / 8] ushr ((index % 8) * 8)).toByte()
        }
        call(WasmOperation.THEME, handle, data = data)
    }

    override fun nativeParseColor(color: String): Int {
        return call(WasmOperation.PARSE_COLOR, data = color.encodeToByteArray()).value
    }

    override fun nativeScroll(handle: Long, deltaRows: Int) {
        call(WasmOperation.SCROLL, handle, deltaRows)
    }

    override fun nativeScrollToBottom(handle: Long) {
        call(WasmOperation.SCROLL_BOTTOM, handle)
    }

    override fun nativeScrollbar(handle: Long): LongArray? {
        return call(WasmOperation.SCROLLBAR, handle).longs()
    }

    override fun nativeViewportState(handle: Long): Int {
        return call(WasmOperation.VIEWPORT_STATE, handle).value
    }

    override fun nativeSnapshot(handle: Long, buffer: SnapshotBuffer, full: Boolean): Int {
        val result = call(
            op = WasmOperation.SNAPSHOT,
            handle = handle,
            a = buffer.capacity(),
            b = if (full) 1 else 0
        )
        result.data()?.copyInto(buffer.bytes)
        return result.value
    }

    override fun nativeKittyPlacements(handle: Long): LongArray? = null

    override fun nativeKittyImage(handle: Long, imageId: Int): IntArray? = null

    override fun nativeEncodeKey(
        handle: Long,
        key: Int,
        action: Int,
        mods: Int,
        unshiftedCodepoint: Int,
        composing: Boolean,
        utf8: ByteArray?
    ): ByteArray?  {
        return call(
            op = WasmOperation.ENCODE_KEY,
            handle = handle,
            a = key,
            b = action,
            c = mods,
            d = unshiftedCodepoint,
            e = if (composing) 1 else 0,
            data = utf8 ?: byteArrayOf()
        ).data()
    }

    override fun nativeEncodeMouse(handle: Long, action: Int, button: Int, col: Int, row: Int, mods: Int): ByteArray? {
        return call(WasmOperation.ENCODE_MOUSE, handle, action, button, col, row, mods).data()
    }

    override fun nativeSelectWord(handle: Long, col: Int, row: Int): Boolean {
        return call(WasmOperation.SELECT_WORD, handle, col, row).value != 0
    }

    override fun nativeSelectAll(handle: Long): Boolean {
        return call(WasmOperation.SELECT_ALL, handle).value != 0
    }

    override fun nativeSetSelection(handle: Long, anchorCol: Int, anchorRow: Int, col: Int, row: Int): Boolean {
        return call(WasmOperation.SET_SELECTION, handle, anchorCol, anchorRow, col, row).value != 0
    }

    override fun nativeClearSelection(handle: Long) {
        call(WasmOperation.CLEAR_SELECTION, handle)
    }

    override fun nativeSelectionText(handle: Long): ByteArray? {
        return call(WasmOperation.SELECTION_TEXT, handle).data()
    }

    override fun nativeViewportText(handle: Long): ByteArray? {
        return call(WasmOperation.VIEWPORT_TEXT, handle).data()
    }

    override fun nativeIsPasteSafe(data: ByteArray): Boolean {
        return call(WasmOperation.IS_PASTE_SAFE, data = data).value != 0
    }

    override fun nativeIsPasteSafeWithMode(handle: Long, data: ByteArray): Boolean {
        return call(WasmOperation.IS_PASTE_SAFE_WITH_MODE, handle, data = data).value == 1
    }

    override fun nativeEncodePaste(handle: Long, data: ByteArray): ByteArray? {
        return call(WasmOperation.ENCODE_PASTE, handle, data = data).data()
    }

    override fun nativeTranscriptText(handle: Long): ByteArray? {
        return call(WasmOperation.TRANSCRIPT, handle).data()
    }

    override fun nativeGetBackgroundColor(handle: Long): Int {
        return call(WasmOperation.BACKGROUND, handle).value
    }

}
