package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.navigation.HomeRoute
import monster.greyde.kachalochka.navigation.MachinePickerRoute
import monster.greyde.kachalochka.navigation.SettingsRoute
import monster.greyde.kachalochka.navigation.VisitRoute
import monster.greyde.kachalochka.ui.home.HomeScreen
import monster.greyde.kachalochka.ui.machine.MachinePickerScreen
import monster.greyde.kachalochka.ui.settings.SettingsScreen
import monster.greyde.kachalochka.ui.theme.KachalochkaTheme
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.visit.VisitScreen
import org.koin.compose.koinInject

const val PICKED_MACHINE = "pickedMachine"

private fun NavController.returnMachineToVisit(id: MachineId) {
    getBackStackEntry<VisitRoute>().savedStateHandle[PICKED_MACHINE] = id.value
    popBackStack<VisitRoute>(inclusive = false)
}

@Composable
fun App() {
    val preference: ThemePreference = koinInject()
    val mode by preference.mode.collectAsState()
    val scope = rememberCoroutineScope()

    KachalochkaTheme(mode) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenVisit = { navController.navigate(VisitRoute(it.value)) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    mode = mode,
                    onModeChange = { scope.launch { preference.set(it) } },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<VisitRoute> { entry ->
                val route = entry.toRoute<VisitRoute>()
                val picked by entry.savedStateHandle
                    .getStateFlow<String?>(PICKED_MACHINE, null)
                    .collectAsState()
                VisitScreen(
                    visitId = VisitId(route.visitId),
                    pickedMachineId = picked?.let(::MachineId),
                    onPickedMachineConsumed = { entry.savedStateHandle[PICKED_MACHINE] = null },
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onPickMachine = {
                        navController.navigate(MachinePickerRoute(route.visitId, it?.value))
                    },
                    onOpenMachineSettings = {},
                    onVisitEnded = { navController.popBackStack(HomeRoute, inclusive = false) },
                )
            }
            composable<MachinePickerRoute> { entry ->
                val route = entry.toRoute<MachinePickerRoute>()
                MachinePickerScreen(
                    visitId = VisitId(route.visitId),
                    selectedMachineId = route.selectedMachineId?.let(::MachineId),
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onPicked = { navController.returnMachineToVisit(it) },
                    onCreate = {},
                    onCopy = { _, _ -> },
                )
            }
        }
    }
}
