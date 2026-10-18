package io.github.xchacha20_poly1305.libghostty

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** The AWT system clipboard. */
public class GhosttyAwtClipboard : GhosttyClipboard {
    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    override fun hasText(): Boolean = clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)

    override fun getText(): String? = clipboard.getData(DataFlavor.stringFlavor) as? String

    override fun setText(text: String) {
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
    }
}
