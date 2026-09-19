package monster.greyde.kachalochka

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.theme.KachalochkaTheme
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.timer.Ticker
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import kotlin.time.Clock

fun fakeGymModule(gym: FakeGym) =
    module {
        single<Clock> { gym.clock }
        single<Ticker> { gym.ticker }
        single<UtcOffset> { gym.utcOffset }
        single<MachineRepository> { gym.machines }
        single<VisitRepository> { gym.visits }
        single<WorkoutSetRepository> { gym.sets }
        single<CurrentUser> { gym.currentUser }
    }

@Composable
fun TestKoin(
    gym: FakeGym,
    content: @Composable () -> Unit,
) {
    KoinApplication(
        configuration =
            koinConfiguration {
                allowOverride(true)
                modules(appModule, platformModule(), fakeGymModule(gym))
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
