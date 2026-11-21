package io.github.xchacha20_poly1305.libghostty

/** Platform-independent terminal operations. The JVM implementation preserves direct-buffer JNI I/O. */
internal interface GhosttyBackend {
    fun nativeCreate(
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

    fun nativeFree(handle: Long)

    fun nativeWrite(handle: Long, data: ByteArray, offset: Int, length: Int): ByteArray?

    fun nativeResize(handle: Long, cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int)

    fun nativeTakeEventFlags(handle: Long): Int

    fun nativeGetTitle(handle: Long): ByteArray?

    fun nativeTakeClipboard(handle: Long): ByteArray?

    fun nativeGetPwd(handle: Long): ByteArray?

    fun nativeTakeNotification(handle: Long): ByteArray?

    fun nativeGetProgress(handle: Long): IntArray?

    fun nativeSetColorScheme(handle: Long, dark: Boolean): ByteArray?

    fun nativeEncodeFocus(handle: Long, gained: Boolean): ByteArray?

    fun nativeHyperlinkAt(handle: Long, col: Int, row: Int): ByteArray?

    fun nativeSetTheme(
        handle: Long,
        foreground: Long,
        background: Long,
        cursor: Long,
        palette: LongArray?,
    )

    fun nativeParseColor(color: String): Int

    fun nativeScroll(handle: Long, deltaRows: Int)

    fun nativeScrollToBottom(handle: Long)

    fun nativeScrollbar(handle: Long): LongArray?


    fun nativeViewportState(handle: Long): Int

    /** With [full], every row is written, not only those changed since the last snapshot. */
    fun nativeSnapshot(handle: Long, buffer: SnapshotBuffer, full: Boolean): Int


    fun nativeKittyPlacements(handle: Long): LongArray?

    fun nativeKittyImage(handle: Long, imageId: Int): IntArray?


    /** [key] is a [GhosttyKey] value and [mods] a combination of [GhosttyMods] bits. */
    fun nativeEncodeKey(
        handle: Long,
        key: Int,
        action: Int,
        mods: Int,
        unshiftedCodepoint: Int,
        composing: Boolean,
        utf8: ByteArray?,
    ): ByteArray?


    fun nativeEncodeMouse(
        handle: Long,
        action: Int,
        button: Int,
        col: Int,
        row: Int,
        mods: Int,
    ): ByteArray?

    fun nativeSelectWord(handle: Long, col: Int, row: Int): Boolean

    fun nativeSelectAll(handle: Long): Boolean

    fun nativeSetSelection(
        handle: Long,
        anchorCol: Int,
        anchorRow: Int,
        col: Int,
        row: Int,
    ): Boolean

    fun nativeClearSelection(handle: Long)

    fun nativeSelectionText(handle: Long): ByteArray?

    fun nativeViewportText(handle: Long): ByteArray?

    fun nativeTranscriptText(handle: Long): ByteArray?

    fun nativeGetBackgroundColor(handle: Long): Int

    fun nativeIsPasteSafe(data: ByteArray): Boolean

    fun nativeIsPasteSafeWithMode(handle: Long, data: ByteArray): Boolean

    fun nativeEncodePaste(handle: Long, data: ByteArray): ByteArray?
}

internal expect fun platformBackend(): GhosttyBackend

internal object GhosttyVt : GhosttyBackend by platformBackend() {
    const val EVENT_BELL = 1
    const val EVENT_TITLE = 1 shl 1
    const val EVENT_CLIPBOARD = 1 shl 2
    const val EVENT_PWD = 1 shl 3
    const val EVENT_NOTIFICATION = 1 shl 4
    const val EVENT_PROGRESS = 1 shl 5
    const val EVENT_COLORS = 1 shl 6
    const val PROGRESS_STATE_REMOVE = 0
    const val PROGRESS_STATE_SET = 1
    const val PROGRESS_STATE_ERROR = 2
    const val PROGRESS_STATE_INDETERMINATE = 3
    const val PROGRESS_STATE_PAUSE = 4
    const val VIEWPORT_ALTERNATE_SCREEN = 1
    const val VIEWPORT_MOUSE_TRACKING = 2
    const val VIEWPORT_SYNC_OUTPUT = 4
    const val KITTY_PLACEMENT_LONGS = 14
    const val KEY_ACTION_RELEASE = 0
    const val KEY_ACTION_PRESS = 1
    const val KEY_ACTION_REPEAT = 2
    const val MOUSE_ACTION_PRESS = 0
    const val MOUSE_ACTION_RELEASE = 1
    const val MOUSE_ACTION_MOTION = 2
    const val MOUSE_BUTTON_LEFT = 1
    const val MOUSE_BUTTON_RIGHT = 2
    const val MOUSE_BUTTON_MIDDLE = 3
    const val MOUSE_BUTTON_WHEEL_UP = 4
    const val MOUSE_BUTTON_WHEEL_DOWN = 5
}
