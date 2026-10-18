package cn.y10n.gotoapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private enum class Screen { HOME, APPS }

@Composable
fun App(vm: MainViewModel) {
    GotoTheme {
        var screen by remember { mutableStateOf(Screen.HOME) }
        when (screen) {
            Screen.HOME -> HomeScreen(
                vm = vm,
                onPickApps = { screen = Screen.APPS },
            )
            Screen.APPS -> AppsScreen(
                vm = vm,
                onBack = { screen = Screen.HOME },
            )
        }
    }
}
