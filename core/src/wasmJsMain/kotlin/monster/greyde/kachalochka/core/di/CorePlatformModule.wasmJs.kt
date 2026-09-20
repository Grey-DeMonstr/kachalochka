package monster.greyde.kachalochka.core.di

import monster.greyde.kachalochka.core.data.gym.RemoteMachineRepository
import monster.greyde.kachalochka.core.data.gym.RemoteVisitRepository
import monster.greyde.kachalochka.core.data.gym.RemoteWorkoutSetRepository
import monster.greyde.kachalochka.core.data.identity.AccountStorage
import monster.greyde.kachalochka.core.data.identity.LocalStorageAccountStorage
import monster.greyde.kachalochka.core.data.identity.NoOwnerlessRows
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.profile.RemoteProfileRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun corePlatformModule(): Module =
    module {
        single<ProfileRepository> { RemoteProfileRepository(get()) }
        single<MachineRepository> { RemoteMachineRepository(get()) }
        single<VisitRepository> { RemoteVisitRepository(get()) }
        single<WorkoutSetRepository> { RemoteWorkoutSetRepository(get()) }
        single<AccountStorage> { LocalStorageAccountStorage() }
        single<OwnerlessRows> { NoOwnerlessRows }
    }
