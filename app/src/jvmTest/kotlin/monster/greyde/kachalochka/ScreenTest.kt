package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.data.family.FamilyFollower
import monster.greyde.kachalochka.core.data.gym.PhotoImages
import monster.greyde.kachalochka.core.data.identity.AccountDeletion
import monster.greyde.kachalochka.core.data.identity.AccountStorage
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.navigation.PageFooter
import monster.greyde.kachalochka.ui.account.AccountsViewModel
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.family.PARENT_CODE
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.JoinCodeStore
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.strings.EnStrings
import monster.greyde.kachalochka.ui.strings.RuStrings
import monster.greyde.kachalochka.ui.theme.KachalochkaTheme
import monster.greyde.kachalochka.ui.theme.SystemBars
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.timer.Ticker
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import kotlin.time.Clock

fun fakeGymModule(gym: FakeGym) =
    module {
        // The tests leave out corePlatformModule, which is where the real graph stores accounts.
        single<AccountStorage> { InMemoryAccountStorage() }
        single<Clock> { gym.clock }
        single<Ticker> { gym.ticker }
        single<UtcOffset> { gym.utcOffset }
        single<MachineRepository> { gym.machines }
        single<VisitRepository> { gym.visits }
        single<PlanRepository> { gym.plans }
        single<WorkoutSetRepository> { gym.sets }
        single<ProfileRepository> { gym.profiles }
        single<MachineLinkRepository> { gym.machineLinks }
        single<MeasureRepository> { gym.measures }
        single<MeasurementRepository> { gym.measurements }
        single<PhotoRepository> { gym.photos }
        single<PhotoImages> { gym.photoImages }
        single<PhotoCapture> { gym.photoCapture }
        single<CurrentUser> { gym.currentUser }
        single<Accounts> { gym.accounts }
        single<AccountDeletion> { gym.deletion }
        single<FriendsRepository> { gym.friends }
        single<FamilyRepository> { gym.family }
        single<FamilyFollower> { gym.follower }
        single<SupabaseCredentials> { gym.credentials }
        single<SyncTrigger> { gym.sync }
        single<InviteSharing> { gym.invites }
        single<TextSharing> { gym.texts }
        single<JoinCodeStore> { gym.joinCodes }
        single<JoinCodeStore>(named(PARENT_CODE)) { gym.parentCodes }
        single<PageFooter> { gym.footer }
        single<ThemePreference> { gym.themes }
        single<SystemBars> { gym.systemBars }
        viewModelOf(::AccountsViewModel)
    }

@Composable
fun TestKoin(
    gym: FakeGym,
    signInRequired: Boolean = false,
    content: @Composable () -> Unit,
) {
    KoinApplication(
        configuration =
            koinConfiguration {
                allowOverride(true)
                modules(
                    appModule,
                    platformModule(),
                    fakeGymModule(gym),
                    module { single { SignInRequired(signInRequired) } },
                )
            },
        content = content,
    )
}

@Serializable
object ScreenUnderTest

/** Hosts one screen in a single-destination NavHost, which gives it a ViewModelStoreOwner. */
@OptIn(ExperimentalTestApi::class)
fun runScreenTest(
    gym: FakeGym,
    screen: @Composable () -> Unit,
    assertions: ComposeUiTest.() -> Unit,
) = runNavigationUiTest(
    content = {
        TestKoin(gym) {
            KachalochkaTheme(ThemeMode.Dark) {
                val navController = rememberNavController()
                NavHost(navController, startDestination = ScreenUnderTest) {
                    composable<ScreenUnderTest> { screen() }
                }
            }
        }
    },
    assertions = assertions,
)

/**
 * [runScreenTest] with the app speaking English. Russian comes back before the test ends, while
 * the screen's view models can still react to it.
 */
@OptIn(ExperimentalTestApi::class)
fun runScreenTestInEnglish(
    gym: FakeGym,
    screen: @Composable () -> Unit,
    assertions: ComposeUiTest.() -> Unit,
) {
    AppStrings.set(EnStrings)
    try {
        runScreenTest(gym, screen) {
            try {
                assertions()
            } finally {
                AppStrings.set(RuStrings)
                waitForIdle()
            }
        }
    } finally {
        AppStrings.set(RuStrings)
    }
}
