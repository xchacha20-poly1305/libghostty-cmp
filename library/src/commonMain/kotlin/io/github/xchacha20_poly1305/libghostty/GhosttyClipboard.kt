package io.github.xchacha20_poly1305.libghostty

/**
 * The system clipboard as programs reach it through OSC 52. Methods are called on the thread that calls
 * [GhosttyTerminalSession.feedOutput] and on the main thread, so implementations must be thread safe.
 */
public interface GhosttyClipboard {
    public fun hasText(): Boolean

    public fun getText(): String?

    /**
     * Reads for a user paste gesture. Browser hosts can complete asynchronously on the main thread.
     *
     * @since 0.5.0
     */
    public fun readText(onResult: (String?) -> Unit): Unit = onResult(getText())

    public fun setText(text: String)

    /**
     * A terminal program requested a write. Hosts can require consent independently of user copy actions.
     *
     * @since 0.5.0
     */
    public fun setTextForProgram(text: String): Unit = setText(text)
}
