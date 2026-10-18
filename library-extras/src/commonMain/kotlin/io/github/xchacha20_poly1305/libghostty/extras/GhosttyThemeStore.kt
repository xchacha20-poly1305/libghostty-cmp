package io.github.xchacha20_poly1305.libghostty.extras

import io.github.xchacha20_poly1305.libghostty.GhosttyColors
import io.github.xchacha20_poly1305.libghostty.GhosttyTheme
import java.io.InputStream

/** The iTerm2 color themes Ghostty ships, bundled as resources. */
public object GhosttyThemeStore {

    public const val DEFAULT_LIGHT_THEME: String = "Alabaster"
    public const val DEFAULT_DARK_THEME: String = "Afterglow"

    public fun defaultTheme(isDark: Boolean): String = if (isDark) DEFAULT_DARK_THEME else DEFAULT_LIGHT_THEME

    /** Names of the themes with a dark or a light background. */
    public fun listThemes(isDark: Boolean): List<String> = index.filter { (_, background) ->
        val luminance = background?.let(::backgroundLuminance)
        if (luminance == null) {
            isDark
        } else if (isDark) {
            luminance <= 128
        } else {
            luminance > 128
        }
    }.map { it.first }

    /** Theme names with their background color, read from the `index` the build writes next to the themes. */
    private val index: List<Pair<String, String?>> by lazy {
        open("index")?.bufferedReader()?.useLines { lines ->
            lines.mapNotNull { line ->
                val fields = line.split('\t', limit = 2)
                if (fields.size < 2) {
                    null
                } else {
                    fields[0] to fields[1].takeIf { it.isNotEmpty() }
                }
            }.toList()
        }.orEmpty()
    }

    public fun loadTheme(name: String): GhosttyTheme? {
        // Theme names are file names; anything with a path separator is not one.
        if ('/' in name || name == "index") return null
        val stream = open(name) ?: return null
        var foreground: Int? = null
        var background: Int? = null
        var cursorColor: Int? = null
        var selectionBackground: Int? = null
        var selectionForeground: Int? = null
        val palette = arrayOfNulls<Int>(256)
        stream.bufferedReader().forEachLine { line ->
            val fields = line.split('=', limit = 2)
            if (fields.size < 2) return@forEachLine
            val value = fields[1].trim()
            when (fields[0].trim()) {
                "palette" -> {
                    val entry = value.split('=', limit = 2)
                    val index = if (entry.size < 2) {
                        null
                    } else {
                        entry[0].trim().toIntOrNull()
                    }
                    if (index != null && index in 0..255) {
                        palette[index] = GhosttyColors.parse(entry[1].trim())
                    }
                }

                "foreground" -> foreground = GhosttyColors.parse(value)
                "background" -> background = GhosttyColors.parse(value)
                "cursor-color" -> cursorColor = GhosttyColors.parse(value)
                "selection-background" -> selectionBackground = GhosttyColors.parse(value)
                "selection-foreground" -> selectionForeground = GhosttyColors.parse(value)
            }
        }
        return GhosttyTheme(
            foreground,
            background,
            cursorColor,
            selectionBackground,
            selectionForeground,
            palette.toList(),
        )
    }

    public fun loadThemeOrDefault(name: String, isDark: Boolean): GhosttyTheme? =
        loadTheme(name) ?: loadTheme(defaultTheme(isDark))

    private fun open(name: String): InputStream? = GhosttyThemeStore::class.java.getResourceAsStream("themes/$name")

    private fun backgroundLuminance(color: String): Int? {
        val argb = GhosttyColors.parse(color) ?: return null
        val red = argb shr 16 and 0xFF
        val green = argb shr 8 and 0xFF
        val blue = argb and 0xFF
        return (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
    }
}
