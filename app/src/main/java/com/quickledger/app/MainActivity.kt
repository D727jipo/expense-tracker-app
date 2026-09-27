package com.quickledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.quickledger.app.LedgerApp
import com.quickledger.app.ThemePrefs
import com.quickledger.app.ui.home.HomeScreen
import com.quickledger.app.ui.onboarding.PermissionScreen
import com.quickledger.app.ui.search.SearchScreen
import com.quickledger.app.ui.settings.SettingsScreen
import com.quickledger.app.ui.theme.LedgerTheme

private enum class Screen { HOME, SEARCH, SETTINGS }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var themeMode by mutableStateOf(LedgerApp.prefs(this).getInt(LedgerApp.KEY_THEME, ThemePrefs.MODE_SYSTEM))
        var onboarded by mutableStateOf(LedgerApp.prefs(this).getBoolean(LedgerApp.KEY_ONBOARDED, false))

        setContent {
            LedgerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialThemeColor()) {
                    if (!onboarded) {
                        PermissionScreen(onDone = {
                            LedgerApp.prefs(this).edit().putBoolean(LedgerApp.KEY_ONBOARDED, true).apply()
                            onboarded = true
                        })
                    } else {
                        LedgerAppNav(
                            onThemeChanged = {
                                themeMode = LedgerApp.prefs(this).getInt(LedgerApp.KEY_THEME, ThemePrefs.MODE_SYSTEM)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.foregroundActivityCount += 1
    }

    override fun onPause() {
        AppState.foregroundActivityCount -= 1
        super.onPause()
    }

    @Composable
    private fun MaterialThemeColor() = androidx.compose.material3.MaterialTheme.colorScheme.background
}

@Composable
fun LedgerAppNav(onThemeChanged: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    val toSearch = { screen = Screen.SEARCH }
    val toSettings = { screen = Screen.SETTINGS }
    val toHome = { screen = Screen.HOME }

    BackHandler(enabled = screen != Screen.HOME) { toHome() }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState != Screen.HOME && initialState == Screen.HOME
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 4 * dir } + fadeIn(tween(180)))
                .togetherWith(slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 4 * dir } + fadeOut(tween(120)))
        },
        label = "nav",
    ) { current ->
        when (current) {
            Screen.HOME -> HomeScreen(onOpenSearch = toSearch, onOpenSettings = toSettings)
            Screen.SEARCH -> SearchScreen(onBack = toHome)
            Screen.SETTINGS -> SettingsScreen(onBack = toHome, onThemeChanged = onThemeChanged)
        }
    }
}
