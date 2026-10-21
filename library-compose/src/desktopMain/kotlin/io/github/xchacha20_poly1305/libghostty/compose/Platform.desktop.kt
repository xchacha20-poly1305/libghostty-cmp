package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FontLoadResult
import io.github.xchacha20_poly1305.libghostty.GhosttyAwtClipboard
import io.github.xchacha20_poly1305.libghostty.GhosttyClipboard
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.shaper.ShapingOptions
import org.jetbrains.skia.FontStyle as SkFontStyle

internal actual fun GlyphPainter(
    fontFamily: FontFamily,
    fontSizePx: Float,
    resolver: FontFamily.Resolver,
): GlyphPainter {
    fun resolve(weight: FontWeight, style: FontStyle): Typeface? =
        (resolver.resolve(fontFamily, weight, style).value as? FontLoadResult)?.typeface
    val regular = resolve(FontWeight.Normal, FontStyle.Normal) ?: FontMgr.default.matchFamilyStyle(null, SkFontStyle.NORMAL)!!
    return SkiaGlyphPainter(
        regular = regular,
        bold = resolve(FontWeight.Bold, FontStyle.Normal),
        italic = resolve(FontWeight.Normal, FontStyle.Italic),
        boldItalic = resolve(FontWeight.Bold, FontStyle.Italic),
        sizePx = fontSizePx,
    )
}

private const val SHAPED_CACHE_SIZE = 256

/**
 * Draws with Skia directly. Skia fonts do not fall back to other typefaces for missing glyphs, so cells whose
 * first character the terminal font lacks use the system's choice for that character.
 *
 * `drawString` maps each codepoint to a glyph on its own, which breaks emoji ZWJ sequences, flags and combining
 * marks apart; cells holding more than one codepoint are shaped instead, and the shaped lines are cached.
 */
private class SkiaGlyphPainter(
    regular: Typeface,
    bold: Typeface?,
    italic: Typeface?,
    boldItalic: Typeface?,
    private val sizePx: Float,
) : GlyphPainter {
    private val regularFont = font(regular)

    // A family without a bold or italic face gets synthesized ones.
    private val boldFont = font(bold.takeUnless { it == regular } ?: regular, synthesizeBold = bold == null || bold == regular)
    private val italicFont = font(italic.takeUnless { it == regular } ?: regular, synthesizeItalic = italic == null || italic == regular)
    private val boldItalicFont = font(
        boldItalic.takeUnless { it == regular } ?: regular,
        synthesizeBold = boldItalic == null || boldItalic == regular,
        synthesizeItalic = boldItalic == null || boldItalic == regular,
    )
    private val fallbacks = HashMap<Int, Font?>()
    private val shapingOptions = ShapingOptions.DEFAULT.withFontMgr(FontMgr.default)
    private val shaped = object : LinkedHashMap<ShapedKey, TextLine>(SHAPED_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ShapedKey, TextLine>): Boolean =
            (size > SHAPED_CACHE_SIZE).also { if (it) eldest.value.close() }
    }
    private val paint = Paint().apply { isAntiAlias = true }

    override val cellWidth: Float = regularFont.measureTextWidth("M")
    override val cellHeight: Int = ceil(regularFont.metrics.descent - regularFont.metrics.ascent).toInt()
    override val baseline: Float = -regularFont.metrics.ascent

    override fun measure(text: String): Float = regularFont.measureTextWidth(text)

    override fun draw(
        canvas: Canvas,
        text: CharArray,
        offset: Int,
        length: Int,
        x: Float,
        baselineY: Float,
        color: Int,
        bold: Boolean,
        italic: Boolean,
    ) {
        val styled = when {
            bold && italic -> boldItalicFont
            bold -> boldFont
            italic -> italicFont
            else -> regularFont
        }
        val codepoint = Character.codePointAt(text, offset, offset + length)
        val font = if (styled.getUTF32Glyph(codepoint) != 0.toShort()) styled else fallback(codepoint) ?: styled
        paint.color = color
        val string = String(text, offset, length)
        if (Character.charCount(codepoint) == length) {
            canvas.skiaCanvas.drawString(string, x, baselineY, font, paint)
        } else {
            val line = shaped.getOrPut(ShapedKey(string, font)) { TextLine.make(string, font, shapingOptions) }
            canvas.skiaCanvas.drawTextLine(line, x, baselineY, paint)
        }
    }

    private data class ShapedKey(val text: String, val font: Font)

    private fun fallback(codepoint: Int): Font? = fallbacks.getOrPut(codepoint) {
        FontMgr.default.matchFamilyStyleCharacter(null, SkFontStyle.NORMAL, null, codepoint)?.let { font(it) }
    }

    private fun font(typeface: Typeface, synthesizeBold: Boolean = false, synthesizeItalic: Boolean = false) =
        Font(typeface, sizePx).apply {
            isSubpixel = true
            edging = FontEdging.ANTI_ALIAS
            isEmboldened = synthesizeBold
            if (synthesizeItalic) {
                skewX = -0.25f
            }
        }
}

internal actual fun imageBitmapOf(width: Int, height: Int, argb: IntArray): ImageBitmap {
    // An ARGB int in little-endian memory is B, G, R, A.
    val bytes = ByteBuffer.allocate(argb.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    bytes.asIntBuffer().put(argb)
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes.array(), width * 4).toComposeImageBitmap()
}

@Composable
internal actual fun rememberPlatformClipboard(): GhosttyClipboard = remember { GhosttyAwtClipboard() }

internal actual fun Modifier.selectionMagnifier(center: () -> Offset): Modifier = this
