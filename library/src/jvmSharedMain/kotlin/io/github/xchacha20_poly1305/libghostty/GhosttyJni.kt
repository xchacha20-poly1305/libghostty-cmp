package io.github.xchacha20_poly1305.libghostty

import java.nio.ByteBuffer

internal object GhosttyJni : GhosttyBackend {

    init {
        loadGhosttyJni()
    }

    external override fun nativeCreate(
        cols: Int,
        rows: Int,
        maxScrollbackLines: Long,
        xtversion: String,
        terminfoName: String?,
        cursorStyle: Int,
        cursorBlink: Boolean,
        graphemeCluster: Boolean,
        callbacks: GhosttyVtCallbacks?,
    ): Long

    external override fun nativeFree(handle: Long)

    external override fun nativeWrite(handle: Long, data: ByteArray, offset: Int, length: Int): ByteArray?

    external override fun nativeResize(handle: Long, cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int)

    external override fun nativeTakeEventFlags(handle: Long): Int

    external override fun nativeGetTitle(handle: Long): ByteArray?

    external override fun nativeTakeClipboard(handle: Long): ByteArray?

    external override fun nativeGetPwd(handle: Long): ByteArray?

    external override fun nativeTakeNotification(handle: Long): ByteArray?

    external override fun nativeGetProgress(handle: Long): IntArray?

    external override fun nativeSetColorScheme(handle: Long, dark: Boolean): ByteArray?

    external override fun nativeEncodeFocus(handle: Long, gained: Boolean): ByteArray?

    external override fun nativeHyperlinkAt(handle: Long, col: Int, row: Int): ByteArray?

    external override fun nativeSetTheme(
        handle: Long,
        foreground: Long,
        background: Long,
        cursor: Long,
        palette: LongArray?,
    )

    external override fun nativeParseColor(color: String): Int

    external override fun nativeScroll(handle: Long, deltaRows: Int)

    external override fun nativeScrollToBottom(handle: Long)

    external override fun nativeScrollbar(handle: Long): LongArray?


    external override fun nativeViewportState(handle: Long): Int

    /** With [full], every row is written, not only those changed since the last snapshot. */
    override fun nativeSnapshot(handle: Long, buffer: SnapshotBuffer, full: Boolean): Int =
        nativeSnapshot(handle, buffer.storage, full)

    external fun nativeSnapshot(handle: Long, buffer: ByteBuffer, full: Boolean): Int


    external override fun nativeKittyPlacements(handle: Long): LongArray?

    external override fun nativeKittyImage(handle: Long, imageId: Int): IntArray?


    /** [key] is a [GhosttyKey] value and [mods] a combination of [GhosttyMods] bits. */
    external override fun nativeEncodeKey(
        handle: Long,
        key: Int,
        action: Int,
        mods: Int,
        unshiftedCodepoint: Int,
        composing: Boolean,
        utf8: ByteArray?,
    ): ByteArray?


    external override fun nativeEncodeMouse(
        handle: Long,
        action: Int,
        button: Int,
        col: Int,
        row: Int,
        mods: Int,
    ): ByteArray?

    external override fun nativeSelectWord(handle: Long, col: Int, row: Int): Boolean

    external override fun nativeSelectAll(handle: Long): Boolean

    external override fun nativeSetSelection(
        handle: Long,
        anchorCol: Int,
        anchorRow: Int,
        col: Int,
        row: Int,
    ): Boolean

    external override fun nativeClearSelection(handle: Long)

    external override fun nativeSelectionText(handle: Long): ByteArray?

    external override fun nativeViewportText(handle: Long): ByteArray?

    external override fun nativeTranscriptText(handle: Long): ByteArray?

    external override fun nativeGetBackgroundColor(handle: Long): Int

    external override fun nativeIsPasteSafe(data: ByteArray): Boolean

    external override fun nativeIsPasteSafeWithMode(handle: Long, data: ByteArray): Boolean

    external override fun nativeEncodePaste(handle: Long, data: ByteArray): ByteArray?
}

internal actual fun platformBackend(): GhosttyBackend = GhosttyJni

internal fun GhosttyVt.nativeSnapshot(handle: Long, buffer: ByteBuffer, full: Boolean): Int =
    GhosttyJni.nativeSnapshot(handle, buffer, full)
