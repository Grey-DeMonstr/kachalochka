package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.fakeGymModule
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.sync.VisitNormalization
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.account.Nickname
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.family.PARENT_CODE
import monster.greyde.kachalochka.ui.family.PendingLink
import monster.greyde.kachalochka.ui.family.TRAINER_CODE
import monster.greyde.kachalochka.ui.friends.FriendColorStore
import monster.greyde.kachalochka.ui.friends.PendingJoin
import monster.greyde.kachalochka.ui.machine.LinkUpkeep
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
import monster.greyde.kachalochka.ui.machine.MachineReads
import monster.greyde.kachalochka.ui.photos.PhotoLoaders
import monster.greyde.kachalochka.ui.plans.Placement
import monster.greyde.kachalochka.ui.timer.RestSoundScheduler
import monster.greyde.kachalochka.ui.timer.RestTimer
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class AppModuleTest {
    private val koin =
        koinApplication {
            allowOverride(true)
            modules(appModule, platformModule(), fakeGymModule(FakeGym()))
        }.koin

    /** A module keeps its singletons until its Koin closes; open, they leak into other tests. */
    @AfterTest
    fun tearDown() = koin.close()

    @Test
    fun the_graph_resolves_every_app_singleton() {
        assertNotNull(koin.get<RestTimer>())
        assertNotNull(koin.get<RestSoundScheduler>())
        assertNotNull(koin.get<VisitNormalization>())
        assertNotNull(koin.get<PendingJoin>())
        assertNotNull(koin.get<PendingLink>(named(PARENT_CODE)))
        assertNotNull(koin.get<PendingLink>(named(TRAINER_CODE)))
        assertNotNull(koin.get<Nickname>())
        assertNotNull(koin.get<FriendColorStore>())
        assertNotNull(koin.get<PhotoLoaders>())
        assertNotNull(koin.get<AccountAvatars>())
        assertNotNull(koin.get<MachineCatalogue>())
        assertNotNull(koin.get<MachineReads>())
        assertNotNull(koin.get<LinkUpkeep>())
        assertNotNull(koin.get<Placement>())
        assertNotNull(koin.get<UnsavedChoices>())
    }
}
