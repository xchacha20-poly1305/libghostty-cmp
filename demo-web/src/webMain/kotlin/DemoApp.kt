import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xchacha20_poly1305.libghostty.GhosttyTheme
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyDialogs
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKeysBar
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.demo.resources.Res
import io.github.xchacha20_poly1305.libghostty.demo.resources.app_title
import io.github.xchacha20_poly1305.libghostty.demo.resources.clear
import io.github.xchacha20_poly1305.libghostty.demo.resources.clear_hint
import io.github.xchacha20_poly1305.libghostty.demo.resources.document_title
import io.github.xchacha20_poly1305.libghostty.demo.resources.error_prefix
import io.github.xchacha20_poly1305.libghostty.demo.resources.example_options
import io.github.xchacha20_poly1305.libghostty.demo.resources.footer_keys
import io.github.xchacha20_poly1305.libghostty.demo.resources.home
import io.github.xchacha20_poly1305.libghostty.demo.resources.ic_clear
import io.github.xchacha20_poly1305.libghostty.demo.resources.ic_contrast
import io.github.xchacha20_poly1305.libghostty.demo.resources.ic_expand
import io.github.xchacha20_poly1305.libghostty.demo.resources.ic_language
import io.github.xchacha20_poly1305.libghostty.demo.resources.ic_paste
import io.github.xchacha20_poly1305.libghostty.demo.resources.language_chinese
import io.github.xchacha20_poly1305.libghostty.demo.resources.language_english
import io.github.xchacha20_poly1305.libghostty.demo.resources.loading
import io.github.xchacha20_poly1305.libghostty.demo.resources.paste
import io.github.xchacha20_poly1305.libghostty.demo.resources.paste_hint
import io.github.xchacha20_poly1305.libghostty.demo.resources.run_example
import io.github.xchacha20_poly1305.libghostty.demo.resources.run_example_hint
import io.github.xchacha20_poly1305.libghostty.demo.resources.simulate_paste_protection
import io.github.xchacha20_poly1305.libghostty.demo.resources.source
import io.github.xchacha20_poly1305.libghostty.demo.resources.terminal_heading
import io.github.xchacha20_poly1305.libghostty.demo.resources.theme_dark
import io.github.xchacha20_poly1305.libghostty.demo.resources.theme_light
import io.github.xchacha20_poly1305.libghostty.demo.resources.theme_system
import io.github.xchacha20_poly1305.libghostty.demo.shared.DemoTerminal
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.w3c.dom.url.URL

private val AdwaitaBlue = Color(0xFF3584E4)
private val SourceUrl = "https://github.com/xchacha20-poly1305/libghostty-cmp"

private val AdwaitaDark = GhosttyTheme(
    0xffffffff.toInt(),
    0xff1c1c1f.toInt(),
    0xffffffff.toInt(),
    0xffffffff.toInt(),
    0xff1c1c1f.toInt(),
    listOf(
        0x241f31, 0xc01c28, 0x2ec27e, 0xf5c211, 0x1e78e4, 0x9841bb, 0x0ab9dc, 0xc0bfbc,
        0x5e5c64, 0xed333b, 0x57e389, 0xf8e45c, 0x51a1ff, 0xc061cb, 0x4fd2fd, 0xf6f5f4,
    ).let { palette ->
        List(256) {
            palette.getOrNull(it)?.or(0xff000000.toInt())
        }
    },
)

private val AdwaitaLight = GhosttyTheme(
    0xff1d1d20.toInt(),
    0xffffffff.toInt(),
    0xff1d1d20.toInt(),
    0xff1d1d20.toInt(),
    0xffffffff.toInt(),
    listOf(
        0x1d1d20, 0xc01c28, 0x26a269, 0xa2734c, 0x12488b, 0xa347ba, 0x2aa1b3, 0xc2c2c2,
        0x5d5d5d, 0xf66151, 0x33d17a, 0xe9ad0c, 0x2a7bde, 0xc061cb, 0x33c7de, 0xffffff,
    ).let { palette ->
        List(256) {
            palette.getOrNull(it)?.or(0xff000000.toInt())
        }
    },
)

@Composable
internal fun DemoRoot(preferences: DemoPreferences, runtime: DemoRuntime?) {
    val systemDark = isSystemInDarkTheme()
    val dark = preferences.theme.isDark(systemDark)
    val content = @Composable {
        DemoResourceEnvironment(preferences.language, dark) {
            MaterialTheme(
                colorScheme = if (dark) {
                    demoDarkColors()
                } else {
                    demoLightColors()
                },
            ) {
                ApplyDocumentChrome(preferences.language, dark, stringResource(Res.string.document_title))
                Surface(Modifier.fillMaxSize()) {
                    when {
                        runtime?.error != null -> StatusPane(stringResource(Res.string.error_prefix, runtime.error))
                        runtime?.session == null -> StatusPane(stringResource(Res.string.loading))
                        else -> DemoApp(preferences, runtime, dark)
                    }
                }
            }
        }
    }
    val resolver = runtime?.fontResolver
    if (resolver != null) {
        CompositionLocalProvider(LocalFontFamilyResolver provides resolver, content = content)
    } else {
        content()
    }
}

@Composable
private fun DemoApp(preferences: DemoPreferences, runtime: DemoRuntime, dark: Boolean) {
    val ghosttyTheme = if (dark) {
        AdwaitaDark
    } else {
        AdwaitaLight
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 650.dp
        val horizontal = if (maxWidth > 1368.dp) {
            (maxWidth - 1320.dp) / 2
        } else if (compact) {
            10.dp
        } else {
            24.dp
        }
        Column(Modifier.fillMaxSize()) {
            DemoHeader(preferences, compact)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontal,
                        vertical = if (compact) {
                            11.dp
                        } else {
                            24.dp
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 11.dp else 14.dp),
            ) {
                DemoToolbar(runtime)
                DemoTerminalWindow(
                    runtime = runtime,
                    theme = ghosttyTheme,
                    dark = dark,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (!compact) {
                    DemoFooter(compact)
                }
            }
            if (compact) GhosttyExtraKeysBar(runtime.state)
        }
        GhosttyDialogs(runtime.state)
    }
}

@Composable
private fun DemoHeader(preferences: DemoPreferences, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) {
                        12.dp
                    } else {
                        24.dp
                    },
                    vertical = if (compact) {
                        10.dp
                    } else {
                        12.dp
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (pagesHomeAvailable()) {
                            goHome()
                        } else {
                            openUrl(SourceUrl)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AdwaitaBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ">_",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (!compact || pagesHomeAvailable()) {
                    Text(
                        text = if (pagesHomeAvailable()) {
                            stringResource(Res.string.home)
                        } else {
                            "Ghostty"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.app_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) {
                        14.sp
                    } else {
                        15.sp
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageSelector(preferences, compact)
                ThemeSelector(preferences, compact)
                if (!compact) {
                    TextButton(
                        onClick = { openUrl(SourceUrl) },
                    ) {
                        Text(stringResource(Res.string.source))
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun DemoToolbar(runtime: DemoRuntime) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RunExampleButton(runtime)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolbarIconButton(
                icon = Res.drawable.ic_paste,
                label = stringResource(Res.string.paste),
                hint = stringResource(Res.string.paste_hint),
                onClick = runtime::paste,
            )
            ToolbarIconButton(
                icon = Res.drawable.ic_clear,
                label = stringResource(Res.string.clear),
                hint = stringResource(Res.string.clear_hint),
                onClick = { runtime.runCommand("clear") },
            )
        }
    }
}

@Composable
private fun RunExampleButton(runtime: DemoRuntime) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SplitButtonLayout(
            leadingButton = {
                Hinted(stringResource(Res.string.run_example_hint)) {
                    SplitButtonDefaults.LeadingButton(
                        onClick = { runtime.runCommand("showcase") },
                    ) {
                        Text(stringResource(Res.string.run_example))
                    }
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_expand),
                        contentDescription = stringResource(Res.string.example_options),
                        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                    )
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.simulate_paste_protection)) },
                onClick = {
                    expanded = false
                    runtime.state.paste(DemoTerminal.PASTE_PROTECTION_EXAMPLE)
                },
            )
        }
    }
}

@Composable
private fun Hinted(hint: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun ToolbarIconButton(
    icon: DrawableResource,
    label: String,
    hint: String,
    onClick: () -> Unit,
) {
    Hinted(hint) {
        IconButton(onClick = onClick) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

@Composable
private fun DemoTerminalWindow(
    runtime: DemoRuntime,
    theme: GhosttyTheme,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = Color(if (dark) 0xFF1C1C1F else 0xFFFFFFFF)
    val heading = Color(if (dark) 0xFF27272B else 0xFFF0F0F0)
    val onHeading = Color(if (dark) 0xFFB0B0B5 else 0xFF5E5C64)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = background,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(35.dp)
                    .background(heading)
                    .padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(Res.string.terminal_heading), color = onHeading, fontSize = 11.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2EC27E)),
                    )
                    Text(
                        "${runtime.columns} × ${runtime.rows}",
                        color = onHeading,
                        fontSize = 11.sp,
                    )
                }
            }
            GhosttyTerminal(
                session = runtime.session,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("demo-terminal"),
                state = runtime.state,
                theme = theme,
                darkColorScheme = dark,
                fontFamily = runtime.fontFamily,
                fontSize = 15.sp,
                contentPadding = PaddingValues(18.dp),
            )
        }
    }
}

@Composable
private fun DemoFooter(compact: Boolean) {
    val style = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = if (compact) {
            10.sp
        } else {
            11.sp
        },
        lineHeight = 18.sp,
    )
    SelectionContainer {
        Text(stringResource(Res.string.footer_keys), style = style)
    }
}

@Composable
private fun LanguageSelector(preferences: DemoPreferences, compact: Boolean) {
    ChoiceButton(
        compact = compact,
        icon = Res.drawable.ic_language,
        value = if (preferences.language == DemoLanguage.Chinese) {
            stringResource(Res.string.language_chinese)
        } else {
            stringResource(Res.string.language_english)
        },
        options = listOf(
            stringResource(Res.string.language_english) to { preferences.setLanguage(DemoLanguage.English) },
            stringResource(Res.string.language_chinese) to { preferences.setLanguage(DemoLanguage.Chinese) },
        ),
    )
}

@Composable
private fun ThemeSelector(preferences: DemoPreferences, compact: Boolean) {
    val value = when (preferences.theme) {
        DemoTheme.System -> stringResource(Res.string.theme_system)
        DemoTheme.Light -> stringResource(Res.string.theme_light)
        DemoTheme.Dark -> stringResource(Res.string.theme_dark)
    }
    ChoiceButton(
        compact = compact,
        icon = Res.drawable.ic_contrast,
        value = value,
        options = listOf(
            stringResource(Res.string.theme_system) to { preferences.setTheme(DemoTheme.System) },
            stringResource(Res.string.theme_light) to { preferences.setTheme(DemoTheme.Light) },
            stringResource(Res.string.theme_dark) to { preferences.setTheme(DemoTheme.Dark) },
        ),
    )
}

@Composable
private fun ChoiceButton(
    compact: Boolean,
    icon: DrawableResource,
    value: String,
    options: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (compact) {
            Hinted(value) {
                IconButton(onClick = { expanded = true }) {
                    Icon(painterResource(icon), contentDescription = value, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            TextButton(onClick = { expanded = true }) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (title, onPick) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onPick()
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusPane(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

private fun demoDarkColors() = darkColorScheme(
    primary = AdwaitaBlue,
    background = Color(0xFF242424),
    surface = Color(0xFF303030),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAEAEAE),
    outlineVariant = Color(0x28FFFFFF),
)

private fun demoLightColors() = lightColorScheme(
    primary = AdwaitaBlue,
    background = Color(0xFFFAFAFB),
    surface = Color.White,
    onBackground = Color(0xFF2E3436),
    onSurface = Color(0xFF2E3436),
    onSurfaceVariant = Color(0xFF666666),
    outlineVariant = Color(0x20000000),
)


internal fun openUrl(url: String) {
    window.open(url, "_blank", "noopener,noreferrer")
}

internal fun pagesHomeAvailable(): Boolean =
    document.documentElement?.getAttribute("data-pages-home") == "true"

internal fun goHome() {
    window.location.href = URL("../index.html", document.baseURI).href
}
