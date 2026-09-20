package monster.greyde.kachalochka.core.di

import kotlinx.coroutines.Dispatchers
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.data.gym.LocalMachineRepository
import monster.greyde.kachalochka.core.data.gym.LocalVisitRepository
import monster.greyde.kachalochka.core.data.gym.LocalWorkoutSetRepository
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.identity.SqlOwnerlessRows
import monster.greyde.kachalochka.core.data.profile.LocalProfileRepository
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/** Everything above the driver is the same on both SQLDelight targets; only the driver differs. */
internal fun sqlModule(): Module =
    module {
        single { kachalochkaDatabase(get()) }
        single { OutboxDao(get()) }
        single<ProfileRepository> { LocalProfileRepository(get(), get(), Dispatchers.IO) }
        single<MachineRepository> { LocalMachineRepository(get(), get(), Dispatchers.IO) }
        single<VisitRepository> { LocalVisitRepository(get(), get(), Dispatchers.IO) }
        single<WorkoutSetRepository> { LocalWorkoutSetRepository(get(), get(), Dispatchers.IO) }
        single<OwnerlessRows> { SqlOwnerlessRows(get(), get(), Clock.System, Dispatchers.IO) }
    }
