package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.navigation.CalendarRoute
import monster.greyde.kachalochka.navigation.FriendCalendarRoute
import monster.greyde.kachalochka.navigation.FriendVisitRoute
import monster.greyde.kachalochka.navigation.FriendsRoute
import monster.greyde.kachalochka.navigation.GroupRoute
import monster.greyde.kachalochka.navigation.HomeRoute
import monster.greyde.kachalochka.navigation.MachineFormRoute
import monster.greyde.kachalochka.navigation.MachineListRoute
import monster.greyde.kachalochka.navigation.MachinePickerRoute
import monster.greyde.kachalochka.navigation.SettingsRoute
import monster.greyde.kachalochka.navigation.VisitRoute
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.account.SignInScreen
import monster.greyde.kachalochka.ui.calendar.CalendarScreen
import monster.greyde.kachalochka.ui.friends.FriendCalendarScreen
import monster.greyde.kachalochka.ui.friends.FriendVisitScreen
import monster.greyde.kachalochka.ui.friends.GroupScreen
import monster.greyde.kachalochka.ui.friends.GroupsScreen
import monster.greyde.kachalochka.ui.friends.InviteMissingDialog
import monster.greyde.kachalochka.ui.friends.JoinOutcome
import monster.greyde.kachalochka.ui.friends.PendingJoin
import monster.greyde.kachalochka.ui.home.HomeScreen
import monster.greyde.kachalochka.ui.machine.MachineFormArgs
import monster.greyde.kachalochka.ui.machine.MachineFormScreen
import monster.greyde.kachalochka.ui.machine.MachineListScreen
import monster.greyde.kachalochka.ui.machine.MachinePickerScreen
import monster.greyde.kachalochka.ui.settings.SettingsScreen
import monster.greyde.kachalochka.ui.theme.KachalochkaTheme
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.visit.VisitScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    val signInRequired: SignInRequired = koinInject()
    val accountsViewModel: AccountsViewModel = koinViewModel()
    val accounts by accountsViewModel.state.collectAsState()

    KachalochkaTheme(mode) {
        if (signInRequired.value && accounts.activeId == null) {
            SignInScreen(
                onSignIn = accountsViewModel::addAccount,
                failure = accounts.failure,
            )
        } else {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenVisit = { navController.navigate(VisitRoute(it.iso)) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenCalendar = { navController.navigate(CalendarRoute) },
                        onOpenMachines = { navController.navigate(MachineListRoute) },
                        onOpenFriends = { navController.navigate(FriendsRoute) },
                    )
                }
                composable<MachineListRoute> {
                    MachineListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenMachine = {
                            navController.navigate(
                                MachineFormRoute(machineId = it.value, fromList = true),
                            )
                        },
                        onNewMachine = {
                            navController.navigate(MachineFormRoute(fromList = true))
                        },
                    )
                }
                composable<CalendarRoute> {
                    CalendarScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenVisit = { navController.navigate(VisitRoute(it.iso)) },
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
                        day = CalendarDay.parse(route.day),
                        pickedMachineId = picked?.let(::MachineId),
                        onPickedMachineConsumed = {
                            entry.savedStateHandle[PICKED_MACHINE] = null
                        },
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onPickMachine = {
                            navController.navigate(MachinePickerRoute(route.day, it?.value))
                        },
                        onOpenMachineSettings = {
                            navController.navigate(
                                MachineFormRoute(machineId = it.value),
                            )
                        },
                    )
                }
                composable<MachinePickerRoute> { entry ->
                    val route = entry.toRoute<MachinePickerRoute>()
                    MachinePickerScreen(
                        day = CalendarDay.parse(route.day),
                        selectedMachineId = route.selectedMachineId?.let(::MachineId),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onPicked = { navController.returnMachineToVisit(it) },
                        onCreate = {
                            navController.navigate(MachineFormRoute(name = it))
                        },
                        onCopy = { source, name ->
                            navController.navigate(
                                MachineFormRoute(copyOfId = source.value, name = name),
                            )
                        },
                    )
                }
                composable<MachineFormRoute> { entry ->
                    val route = entry.toRoute<MachineFormRoute>()
                    MachineFormScreen(
                        args =
                            MachineFormArgs(
                                machineId = route.machineId?.let(::MachineId),
                                copyOf = route.copyOfId?.let(::MachineId),
                                name = route.name,
                            ),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onSaved = {
                            if (route.fromList) {
                                navController.popBackStack()
                            } else {
                                navController.returnMachineToVisit(it)
                            }
                        },
                        inVisit = !route.fromList,
                    )
                }
                composable<FriendVisitRoute> { entry ->
                    val route = entry.toRoute<FriendVisitRoute>()
                    FriendVisitScreen(
                        member = UserId(route.userId),
                        name = route.name,
                        day = CalendarDay.parse(route.day),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                composable<FriendCalendarRoute> { entry ->
                    val route = entry.toRoute<FriendCalendarRoute>()
                    FriendCalendarScreen(
                        member = UserId(route.userId),
                        name = route.name,
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenVisit = {
                            navController.navigate(
                                FriendVisitRoute(route.userId, route.name, it.iso),
                            )
                        },
                    )
                }
                composable<GroupRoute> { entry ->
                    val route = entry.toRoute<GroupRoute>()
                    GroupScreen(
                        groupId = GroupId(route.groupId),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenMember = {
                            navController.navigate(
                                FriendCalendarRoute(it.userId.value, it.displayName),
                            )
                        },
                        onGone = { navController.popBackStack() },
                    )
                }
                composable<FriendsRoute> {
                    GroupsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenGroup = { navController.navigate(GroupRoute(it.value)) },
                    )
                }
            }
            val pendingJoin: PendingJoin = koinInject()
            var inviteMissing by remember { mutableStateOf(false) }
            LaunchedEffect(accounts.activeId) {
                if (accounts.activeId == null) return@LaunchedEffect
                when (val outcome = pendingJoin.consume()) {
                    is JoinOutcome.Joined ->
                        navController.navigate(GroupRoute(outcome.group.value))
                    JoinOutcome.NotFound -> inviteMissing = true
                    null -> Unit
                }
            }
            if (inviteMissing) InviteMissingDialog(onDismiss = { inviteMissing = false })
        }
    }
}
