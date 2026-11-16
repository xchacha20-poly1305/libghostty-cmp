package io.github.xchacha20_poly1305.libghostty.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import io.github.xchacha20_poly1305.libghostty.GhosttyClipboard
import io.github.xchacha20_poly1305.libghostty.GhosttyBrowserClipboard

internal actual fun GlyphPainter(fontFamily: FontFamily, fontSizePx: Float, resolver: FontFamily.Resolver): GlyphPainter {
    return skiaGlyphPainter(fontFamily, fontSizePx, resolver)
}

internal actual fun imageBitmapOf(width: Int, height: Int, argb: IntArray): ImageBitmap = skiaImageBitmapOf(width, height, argb)

internal actual fun Modifier.selectionMagnifier(center: () -> Offset): Modifier = this

@Composable
internal actual fun rememberPlatformClipboard(): GhosttyClipboard = remember { GhosttyBrowserClipboard() }
