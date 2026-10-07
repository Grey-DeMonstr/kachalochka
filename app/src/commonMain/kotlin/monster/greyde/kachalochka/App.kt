package monster.greyde.kachalochka

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.friends.GroupId
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.PlanId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.navigation.CalendarRoute
import monster.greyde.kachalochka.navigation.ChildrenRoute
import monster.greyde.kachalochka.navigation.FriendCalendarRoute
import monster.greyde.kachalochka.navigation.FriendMachineRoute
import monster.greyde.kachalochka.navigation.FriendVisitRoute
import monster.greyde.kachalochka.navigation.FriendsRoute
import monster.greyde.kachalochka.navigation.GroupRoute
import monster.greyde.kachalochka.navigation.GuardiansRoute
import monster.greyde.kachalochka.navigation.HomeRoute
import monster.greyde.kachalochka.navigation.LinkChooserRoute
import monster.greyde.kachalochka.navigation.MachineFormRoute
import monster.greyde.kachalochka.navigation.MachineListRoute
import monster.greyde.kachalochka.navigation.MachinePickerRoute
import monster.greyde.kachalochka.navigation.MeasureRoute
import monster.greyde.kachalochka.navigation.MeasurementFormRoute
import monster.greyde.kachalochka.navigation.MeasuresRoute
import monster.greyde.kachalochka.navigation.PageFooter
import monster.greyde.kachalochka.navigation.PlanRoute
import monster.greyde.kachalochka.navigation.PlansRoute
import monster.greyde.kachalochka.navigation.SettingsRoute
import monster.greyde.kachalochka.navigation.StatisticsRoute
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.navigation.VisitRoute
import monster.greyde.kachalochka.navigation.screenEnter
import monster.greyde.kachalochka.navigation.screenExit
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.account.SignInScreen
import monster.greyde.kachalochka.ui.calendar.CalendarScreen
import monster.greyde.kachalochka.ui.family.ChildrenScreen
import monster.greyde.kachalochka.ui.family.GuardianInviteDialog
import monster.greyde.kachalochka.ui.family.GuardianProblemDialog
import monster.greyde.kachalochka.ui.family.GuardiansScreen
import monster.greyde.kachalochka.ui.family.PendingGuardian
import monster.greyde.kachalochka.ui.friends.FriendCalendarScreen
import monster.greyde.kachalochka.ui.friends.FriendVisitScreen
import monster.greyde.kachalochka.ui.friends.GroupScreen
import monster.greyde.kachalochka.ui.friends.GroupsScreen
import monster.greyde.kachalochka.ui.friends.InviteConfirmDialog
import monster.greyde.kachalochka.ui.friends.InviteMissingDialog
import monster.greyde.kachalochka.ui.friends.JoinOutcome
import monster.greyde.kachalochka.ui.friends.PendingJoin
import monster.greyde.kachalochka.ui.home.HomeScreen
import monster.greyde.kachalochka.ui.machine.FriendMachineScreen
import monster.greyde.kachalochka.ui.machine.LinkChooserScreen
import monster.greyde.kachalochka.ui.machine.MachineFormArgs
import monster.greyde.kachalochka.ui.machine.MachineFormScreen
import monster.greyde.kachalochka.ui.machine.MachineListScreen
import monster.greyde.kachalochka.ui.machine.MachinePickerScreen
import monster.greyde.kachalochka.ui.measures.MeasureScreen
import monster.greyde.kachalochka.ui.measures.MeasurementFormScreen
import monster.greyde.kachalochka.ui.measures.MeasuresScreen
import monster.greyde.kachalochka.ui.plans.PlanFormScreen
import monster.greyde.kachalochka.ui.plans.PlansScreen
import monster.greyde.kachalochka.ui.settings.SettingsScreen
import monster.greyde.kachalochka.ui.stats.StatisticsScreen
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.strings.LanguagePreference
import monster.greyde.kachalochka.ui.strings.SystemLanguage
import monster.greyde.kachalochka.ui.strings.strings
import monster.greyde.kachalochka.ui.theme.KachalochkaTheme
import monster.greyde.kachalochka.ui.theme.SystemBars
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.theme.resolvesToDark
import monster.greyde.kachalochka.ui.visit.VisitScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The saved-state key a visit or a plan reads a picked machine from. */
const val PICKED_MACHINE = "pickedMachine"

/** The friend's machine whose settings the link chooser hands to the machine form. */
private const val COPIED_SETTINGS = "copiedSettings"

/** The machine whose page a visit opens once, from [VisitRoute.machineId]. */
private const val OPENED_MACHINE = "openedMachine"

/** Hands a picked machine to the visit or plan that opened the picker, and returns to it. */
private fun NavController.returnMachine(
    id: MachineId,
    toPlan: Boolean,
) {
    if (toPlan) {
        getBackStackEntry<PlanRoute>().savedStateHandle[PICKED_MACHINE] = id.value
        popBackStack<PlanRoute>(inclusive = false)
    } else {
        getBackStackEntry<VisitRoute>().savedStateHandle[PICKED_MACHINE] = id.value
        popBackStack<VisitRoute>(inclusive = false)
    }
}

@Composable
fun App() {
    val preference: ThemePreference = koinInject()
    val mode by preference.mode.collectAsState()
    val transitions: TransitionPreference = koinInject()
    val transitionMillis by transitions.millis.collectAsState()
    val languages: LanguagePreference = koinInject()
    val systemLanguage: SystemLanguage = koinInject()
    val language by languages.language.collectAsState()
    // Set while composing, so this very frame is already drawn in the chosen language.
    remember(language) { AppStrings.set(language.strings(systemLanguage.tag())) }
    val scope = rememberCoroutineScope()
    val signInRequired: SignInRequired = koinInject()
    val accountsViewModel: AccountsViewModel = koinViewModel()
    val accounts by accountsViewModel.state.collectAsState()

    val footer: PageFooter = koinInject()
    val systemBars: SystemBars = koinInject()
    val dark = mode.resolvesToDark(isSystemInDarkTheme())
    LaunchedEffect(dark) { systemBars.follow(dark) }

    KachalochkaTheme(mode) {
        if (signInRequired.value && accounts.activeId == null) {
            LaunchedEffect(Unit) { footer.show(true) }
            SignInScreen(
                onSignIn = accountsViewModel::addAccount,
                failure = accounts.failure,
            )
        } else {
            val navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            val onHome = entry?.destination?.hasRoute<HomeRoute>() ?: true
            LaunchedEffect(onHome) { footer.show(onHome) }
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
                enterTransition = { screenEnter(transitionMillis) },
                exitTransition = { screenExit(transitionMillis) },
                popEnterTransition = { screenEnter(transitionMillis) },
                popExitTransition = { screenExit(transitionMillis) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenVisit = { navController.navigate(VisitRoute(it.iso)) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenCalendar = { navController.navigate(CalendarRoute) },
                        onOpenMachines = { navController.navigate(MachineListRoute) },
                        onOpenFriends = { navController.navigate(FriendsRoute) },
                        onOpenMeasures = { navController.navigate(MeasuresRoute) },
                        onOpenPlans = { navController.navigate(PlansRoute) },
                        onOpenStatistics = { navController.navigate(StatisticsRoute()) },
                    )
                }
                composable<StatisticsRoute> { entry ->
                    val route = entry.toRoute<StatisticsRoute>()
                    StatisticsScreen(
                        initial = route.machineId?.let(::MachineId),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenVisit = { day, machine ->
                            navController.navigate(VisitRoute(day.iso, machine.value))
                        },
                    )
                }
                composable<PlansRoute> {
                    PlansScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenPlan = { navController.navigate(PlanRoute(it?.value)) },
                        onStarted = { day ->
                            // The started plan is gone, so back leads home, not to the list.
                            navController.navigate(VisitRoute(day.iso)) {
                                popUpTo<PlansRoute> { inclusive = true }
                            }
                        },
                    )
                }
                composable<PlanRoute> { entry ->
                    val route = entry.toRoute<PlanRoute>()
                    val picked by entry.savedStateHandle
                        .getStateFlow<String?>(PICKED_MACHINE, null)
                        .collectAsState()
                    PlanFormScreen(
                        planId = route.planId?.let(::PlanId),
                        pickedMachineId = picked?.let(::MachineId),
                        onPickedMachineConsumed = {
                            entry.savedStateHandle[PICKED_MACHINE] = null
                        },
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onAddMachine = { navController.navigate(MachinePickerRoute()) },
                        onDone = { navController.popBackStack() },
                        fromVisit = route.fromVisit?.let(CalendarDay::parse),
                    )
                }
                composable<MeasuresRoute> {
                    MeasuresScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onNewMeasurement = { navController.navigate(MeasurementFormRoute()) },
                        onOpenMeasure = { navController.navigate(MeasureRoute(it.value)) },
                    )
                }
                composable<MeasureRoute> { entry ->
                    val route = entry.toRoute<MeasureRoute>()
                    MeasureScreen(
                        measureId = MeasureId(route.measureId),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onGone = { navController.popBackStack() },
                    )
                }
                composable<MeasurementFormRoute> { entry ->
                    val route = entry.toRoute<MeasurementFormRoute>()
                    MeasurementFormScreen(
                        initialDay = route.day?.let(CalendarDay::parse),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
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
                        onOpenFriendMachine = { machine, owner ->
                            navController.navigate(FriendMachineRoute(machine.value, owner.value))
                        },
                    )
                }
                composable<FriendMachineRoute> { entry ->
                    val route = entry.toRoute<FriendMachineRoute>()
                    FriendMachineScreen(
                        machineId = MachineId(route.machineId),
                        ownerId = UserId(route.ownerId),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onTaken = { copy ->
                            navController.navigate(
                                MachineFormRoute(machineId = copy.value, fromList = true),
                            ) { popUpTo<FriendMachineRoute> { inclusive = true } }
                        },
                    )
                }
                composable<CalendarRoute> {
                    CalendarScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenVisit = { navController.navigate(VisitRoute(it.iso)) },
                        onOpenFriendVisit = { userId, name, day ->
                            navController.navigate(FriendVisitRoute(userId.value, name, day.iso))
                        },
                        onSaveAsPlan = { navController.navigate(PlanRoute(fromVisit = it.iso)) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenChildren = { navController.navigate(ChildrenRoute) },
                        onOpenGuardians = { navController.navigate(GuardiansRoute) },
                    )
                }
                composable<ChildrenRoute> {
                    ChildrenScreen(onBack = { navController.popBackStack() })
                }
                composable<GuardiansRoute> {
                    GuardiansScreen(onBack = { navController.popBackStack() })
                }
                composable<VisitRoute> { entry ->
                    val route = entry.toRoute<VisitRoute>()
                    val picked by entry.savedStateHandle
                        .getStateFlow<String?>(PICKED_MACHINE, null)
                        .collectAsState()
                    val opened by entry.savedStateHandle
                        .getStateFlow(OPENED_MACHINE, route.machineId)
                        .collectAsState()
                    VisitScreen(
                        day = CalendarDay.parse(route.day),
                        pickedMachineId = picked?.let(::MachineId),
                        onPickedMachineConsumed = {
                            entry.savedStateHandle[PICKED_MACHINE] = null
                        },
                        openedMachineId = opened?.let(::MachineId),
                        onOpenedMachineConsumed = {
                            entry.savedStateHandle[OPENED_MACHINE] = null
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
                        onOpenStatistics = { navController.navigate(StatisticsRoute(it.value)) },
                        onSaveAsPlan = {
                            navController.navigate(PlanRoute(fromVisit = route.day))
                        },
                    )
                }
                composable<MachinePickerRoute> { entry ->
                    val route = entry.toRoute<MachinePickerRoute>()
                    val forPlan = route.day == null
                    MachinePickerScreen(
                        day = route.day?.let(CalendarDay::parse),
                        selectedMachineId = route.selectedMachineId?.let(::MachineId),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onPicked = { navController.returnMachine(it, forPlan) },
                        onCreate = { name, tags ->
                            navController.navigate(
                                MachineFormRoute(name = name, forPlan = forPlan, tags = tags),
                            )
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
                    val copyFrom by entry.savedStateHandle
                        .getStateFlow<String?>(COPIED_SETTINGS, null)
                        .collectAsState()
                    MachineFormScreen(
                        copySettingsFrom = copyFrom?.let(::MachineId),
                        onCopyConsumed = { entry.savedStateHandle[COPIED_SETTINGS] = null },
                        args =
                            MachineFormArgs(
                                machineId = route.machineId?.let(::MachineId),
                                copyOf = route.copyOfId?.let(::MachineId),
                                name = route.name,
                                tags = route.tags,
                            ),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onSaved = {
                            if (route.fromList) {
                                navController.popBackStack()
                            } else {
                                navController.returnMachine(it, route.forPlan)
                            }
                        },
                        onLink = {
                            route.machineId?.let {
                                navController.navigate(LinkChooserRoute(it, route.fromList))
                            }
                        },
                        onOpenFriendMachine = { machine, owner ->
                            navController.navigate(FriendMachineRoute(machine.value, owner.value))
                        },
                        onOpenMachine = {
                            navController.navigate(
                                MachineFormRoute(machineId = it.value, fromList = true),
                            )
                        },
                        onOpenStatistics = { navController.navigate(StatisticsRoute(it.value)) },
                    )
                }
                composable<LinkChooserRoute> { entry ->
                    val route = entry.toRoute<LinkChooserRoute>()
                    LinkChooserScreen(
                        machineId = MachineId(route.machineId),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onMerged = { kept ->
                            // The visit's sheet may be on the removed machine, saved or not.
                            if (!route.fromList) {
                                navController
                                    .getBackStackEntry<VisitRoute>()
                                    .savedStateHandle[PICKED_MACHINE] = kept.value
                            }
                            navController.navigate(
                                MachineFormRoute(
                                    machineId = kept.value,
                                    fromList = route.fromList,
                                ),
                            ) { popUpTo<MachineFormRoute> { inclusive = true } }
                        },
                        onLinked = { copyFrom ->
                            copyFrom?.let {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(COPIED_SETTINGS, it.value)
                            }
                            navController.popBackStack()
                        },
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
                        onOpenMachine = {
                            navController.navigate(FriendMachineRoute(it.value, route.userId))
                        },
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
            // Close the screens a managed child may not use.
            LaunchedEffect(accounts.managedActive) {
                if (accounts.managedActive) navController.leaveChildHidden()
            }
            val pendingJoin: PendingJoin = koinInject()
            var inviteOffered by remember { mutableStateOf(false) }
            var inviteMissing by remember { mutableStateOf(false) }
            // A code saved while signed out goes to whichever account signs in next, so it asks.
            LaunchedEffect(accounts.activeId, accounts.managedActive) {
                inviteOffered =
                    accounts.activeId != null &&
                    !accounts.managedActive &&
                    pendingJoin.waiting
            }
            if (inviteOffered) {
                InviteConfirmDialog(
                    onJoin = {
                        inviteOffered = false
                        scope.launch {
                            when (val outcome = pendingJoin.consume()) {
                                is JoinOutcome.Joined ->
                                    navController.navigate(GroupRoute(outcome.group.value))
                                JoinOutcome.NotFound -> inviteMissing = true
                                null -> Unit
                            }
                        }
                    },
                    onCancel = {
                        inviteOffered = false
                        pendingJoin.decline()
                    },
                )
            }
            if (inviteMissing) InviteMissingDialog(onDismiss = { inviteMissing = false })
            val pendingGuardian: PendingGuardian = koinInject()
            var guardianOffered by remember { mutableStateOf(false) }
            var guardianProblem by remember { mutableStateOf<String?>(null) }
            // A parent's code kept while signed out goes to whichever account signs in next.
            LaunchedEffect(accounts.activeId, accounts.managedActive) {
                guardianOffered =
                    accounts.activeId != null &&
                    !accounts.managedActive &&
                    pendingGuardian.waiting
            }
            if (guardianOffered) {
                GuardianInviteDialog(
                    onAccept = {
                        guardianOffered = false
                        scope.launch {
                            when (pendingGuardian.consume()) {
                                is Acceptance.Linked -> navController.navigate(GuardiansRoute)
                                Acceptance.UnknownCode ->
                                    guardianProblem = AppStrings.current.guardianCodeUnknown
                                Acceptance.OwnCode ->
                                    guardianProblem = AppStrings.current.ownGuardianCode
                                null -> Unit
                            }
                        }
                    },
                    onCancel = {
                        guardianOffered = false
                        pendingGuardian.decline()
                    },
                )
            }
            guardianProblem?.let {
                GuardianProblemDialog(it, onDismiss = { guardianProblem = null })
            }
        }
    }
}

/** Closes every screen a managed child has no business on, with whatever was opened from it. */
private fun NavController.leaveChildHidden() {
    popBackStack<MeasuresRoute>(inclusive = true)
    popBackStack<MeasureRoute>(inclusive = true)
    popBackStack<MeasurementFormRoute>(inclusive = true)
    popBackStack<FriendsRoute>(inclusive = true)
    popBackStack<GroupRoute>(inclusive = true)
    popBackStack<FriendCalendarRoute>(inclusive = true)
    popBackStack<FriendVisitRoute>(inclusive = true)
    popBackStack<FriendMachineRoute>(inclusive = true)
    popBackStack<ChildrenRoute>(inclusive = true)
    popBackStack<GuardiansRoute>(inclusive = true)
}
