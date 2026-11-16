@file:OptIn(ExperimentalWasmJsInterop::class, InternalResourceApi::class)
@file:Suppress("ERROR_SUPPRESSION", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.js.get
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.ScriptQualifier
import org.jetbrains.compose.resources.ThemeQualifier
import org.w3c.dom.HTMLElement

internal enum class DemoLanguage(val storage: String, val htmlLang: String) {
    English("en", "en"),
    Chinese("zh", "zh-CN");

    companion object {
        fun fromBrowser(): DemoLanguage {
            val tag = browserLanguage().lowercase()
            return if (tag == "zh" || tag.startsWith("zh-")) {
                Chinese
            } else {
                English
            }
        }

        fun fromStorage(value: String): DemoLanguage? = entries.find { it.storage == value }
    }
}

internal enum class DemoTheme(val storage: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromStorage(value: String): DemoTheme? = entries.find { it.storage == value }
    }
}

internal class DemoPreferences(
    language: DemoLanguage,
    theme: DemoTheme,
) {
    var language by mutableStateOf(language)
        private set
    var theme by mutableStateOf(theme)
        private set

    fun setLanguage(value: DemoLanguage) {
        language = value
        storageSet(KEY_LANGUAGE, value.storage)
    }

    fun setTheme(value: DemoTheme) {
        theme = value
        storageSet(KEY_THEME, value.storage)
    }

    companion object {
        private const val KEY_LANGUAGE = "ghostty-demo-language"
        private const val KEY_THEME = "ghostty-demo-theme"

        fun load(): DemoPreferences = DemoPreferences(
            language = DemoLanguage.fromStorage(storageGet(KEY_LANGUAGE)) ?: DemoLanguage.fromBrowser(),
            theme = DemoTheme.fromStorage(storageGet(KEY_THEME)) ?: DemoTheme.System,
        )
    }
}

@Composable
internal fun DemoResourceEnvironment(
    language: DemoLanguage,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val default = LocalComposeEnvironment.current
    CompositionLocalProvider(
        LocalComposeEnvironment provides object : ComposeEnvironment {
            @Composable
            override fun rememberEnvironment(): ResourceEnvironment {
                val environment = default.rememberEnvironment()
                return remember(environment, language, dark) {
                    ResourceEnvironment(
                        language = LanguageQualifier(if (language == DemoLanguage.Chinese) "zh" else "en"),
                        script = ScriptQualifier(""),
                        region = RegionQualifier(if (language == DemoLanguage.Chinese) "CN" else ""),
                        theme = ThemeQualifier.selectByValue(dark),
                        density = environment.density,
                    )
                }
            }
        },
    ) {
        content()
    }
}

@Composable
internal fun ApplyDocumentChrome(language: DemoLanguage, dark: Boolean, title: String) {
    LaunchedEffect(language, dark, title) {
        setHtmlLang(language.htmlLang)
        setColorScheme(if (dark) "dark" else "light")
        setDocumentTitle(title)
        setPageBackground(if (dark) "#242424" else "#fafafb")
    }
}


internal fun browserLanguage(): String =
    window.navigator.languages[0]?.toString()?.takeIf { it.isNotEmpty() }
        ?: window.navigator.language.ifEmpty { "en" }

internal fun storageGet(key: String): String = try {
    localStorage.getItem(key).orEmpty()
} catch (_: Throwable) {
    ""
}

internal fun storageSet(key: String, value: String) {
    try {
        localStorage.setItem(key, value)
    } catch (_: Throwable) {
        // Storage can be unavailable for file URLs or blocked by browser policy.
    }
}

internal fun setHtmlLang(lang: String) {
    document.documentElement?.setAttribute("lang", lang)
}

internal fun setColorScheme(scheme: String) {
    (document.documentElement as? HTMLElement)?.style?.setProperty("color-scheme", scheme)
}

internal fun setDocumentTitle(title: String) {
    document.title = title
}

internal fun setPageBackground(color: String) {
    (document.documentElement as? HTMLElement)?.style?.background = color
    document.body?.style?.background = color
}
