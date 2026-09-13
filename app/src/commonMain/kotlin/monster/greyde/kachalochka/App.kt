package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.navigation.HomeRoute
import monster.greyde.kachalochka.navigation.SettingsRoute
import monster.greyde.kachalochka.ui.home.HomeScreen
import monster.greyde.kachalochka.ui.settings.SettingsScreen
import monster.greyde.kachalochka.ui.theme.KachalochkaTheme
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.compose.koinInject

@Composable
fun App() {
    val preference: ThemePreference = koinInject()
    val mode by preference.mode.collectAsState()
    val scope = rememberCoroutineScope()

    KachalochkaTheme(mode) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    mode = mode,
                    onModeChange = { scope.launch { preference.set(it) } },
                )
            }
        }
    }
}
