package monster.greyde.kachalochka.core.data.gym

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.di.coreModule
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class GymModuleTest {
    private val koin = startKoin { modules(coreModule, corePlatformModule()) }.koin

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun the_sql_module_binds_the_local_gym_repositories() {
        assertIs<LocalMachineRepository>(koin.get<MachineRepository>())
        assertIs<LocalVisitRepository>(koin.get<VisitRepository>())
        assertIs<LocalWorkoutSetRepository>(koin.get<WorkoutSetRepository>())
    }

    @Test
    fun rows_written_before_sign_in_have_no_owner() =
        runTest { assertNull(koin.get<CurrentUser>().id()) }
}
