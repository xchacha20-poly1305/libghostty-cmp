package io.github.xchacha20_poly1305.libghostty.extras

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GhosttyThemeStoreTest {

    @Test
    fun loadThemesFromResources() {
        assertTrue(GhosttyThemeStore.listThemes(isDark = true).isNotEmpty(), "no dark themes")
        assertTrue(GhosttyThemeStore.listThemes(isDark = false).isNotEmpty(), "no light themes")
        val theme = assertNotNull(GhosttyThemeStore.loadTheme(GhosttyThemeStore.DEFAULT_DARK_THEME))
        assertNotNull(theme.background, "default dark theme has no background")
        assertNull(GhosttyThemeStore.loadTheme("no such theme"))
    }

    @Test
    fun installTerminfo() {
        val dir = Files.createTempDirectory("terminfo").toFile()
        try {
            GhosttyTerminfo.install(dir)
            assertTrue(GhosttyTerminfo.isInstalled(dir))
            GhosttyTerminfo.install(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
