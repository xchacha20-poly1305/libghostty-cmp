package io.github.xchacha20_poly1305.libghostty.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.xchacha20_poly1305.libghostty.demo.shared.DemoTerminal
import io.github.xchacha20_poly1305.libghostty.extras.GhosttyThemeStore
import androidx.core.content.edit
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyDialogs
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKeysBar
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyJumpToBottomButton
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyScrollIndicator
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminal
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoApp { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
}

private enum class Appearance(val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark),
}

@Composable
private fun DemoApp(onThemeChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("demo", 0)
    }
    var appearance by remember {
        mutableStateOf(Appearance.entries.firstOrNull {
            it.name == preferences.getString("appearance", null)
        } ?: Appearance.System)
    }
    val dark = when (appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    SideEffect {
        onThemeChanged(dark)
    }
    val terminal = remember {
        DemoTerminal()
    }
    DisposableEffect(terminal) {
        onDispose {
            terminal.close()
        }
    }
    val state = remember {
        GhosttyTerminalState().apply {
            linkHoverUnderlineEnabled = true
        }
    }
    val theme = remember(dark) {
        GhosttyThemeStore.loadTheme(
            if (dark) {
                GhosttyThemeStore.DEFAULT_DARK_THEME
            } else {
                GhosttyThemeStore.DEFAULT_LIGHT_THEME
            },
        )
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(stringResource(appearance.label))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                Appearance.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.label)) },
                                        onClick = {
                                            appearance = option
                                            preferences.edit {
                                                putString("appearance", option.name)
                                            }
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = {
                        terminal.runCommand("showcase")
                        state.showKeyboard()
                    }) { Text(stringResource(R.string.run_example)) }
                    TextButton(onClick = { state.paste(); state.showKeyboard() }) {
                        Text(stringResource(R.string.paste))
                    }
                    TextButton(onClick = {
                        terminal.runCommand("clear")
                        state.showKeyboard()
                    }) {
                        Text(stringResource(R.string.clear))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    GhosttyTerminal(
                        terminal.session,
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        theme = theme,
                        darkColorScheme = dark,
                    )
                    GhosttyScrollIndicator(
                        state,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp),
                        )
                    GhosttyJumpToBottomButton(
                        state,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        )
                }
                GhosttyExtraKeysBar(state)
            }
        }
        GhosttyDialogs(state)
    }
}
