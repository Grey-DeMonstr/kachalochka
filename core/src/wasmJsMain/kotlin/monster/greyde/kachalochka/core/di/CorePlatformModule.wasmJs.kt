package monster.greyde.kachalochka.core.di

import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import monster.greyde.kachalochka.core.data.friends.SupabaseFriendsRepository
import monster.greyde.kachalochka.core.data.gym.NoPhotoFiles
import monster.greyde.kachalochka.core.data.gym.PhotoImages
import monster.greyde.kachalochka.core.data.gym.RemoteMachineLinkRepository
import monster.greyde.kachalochka.core.data.gym.RemoteMachineRepository
import monster.greyde.kachalochka.core.data.gym.RemotePhotoRepository
import monster.greyde.kachalochka.core.data.gym.RemoteVisitRepository
import monster.greyde.kachalochka.core.data.gym.RemoteWorkoutSetRepository
import monster.greyde.kachalochka.core.data.gym.StoragePhotoImages
import monster.greyde.kachalochka.core.data.identity.AccountServer
import monster.greyde.kachalochka.core.data.identity.AccountStorage
import monster.greyde.kachalochka.core.data.identity.LocalStorageAccountStorage
import monster.greyde.kachalochka.core.data.identity.NoOwnedRowsPurge
import monster.greyde.kachalochka.core.data.identity.NoOwnerlessRows
import monster.greyde.kachalochka.core.data.identity.OwnedRowsPurge
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.identity.SupabaseAccountServer
import monster.greyde.kachalochka.core.data.measures.RemoteMeasureRepository
import monster.greyde.kachalochka.core.data.measures.RemoteMeasurementRepository
import monster.greyde.kachalochka.core.data.profile.RemoteProfileRepository
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

actual fun corePlatformModule(): Module =
    module {
        single<ProfileRepository> { RemoteProfileRepository(get()) }
        single<MachineRepository> { RemoteMachineRepository(get()) }
        single<VisitRepository> { RemoteVisitRepository(get()) }
        single<WorkoutSetRepository> { RemoteWorkoutSetRepository(get()) }
        single<MachineLinkRepository> { RemoteMachineLinkRepository(get()) }
        single<MeasureRepository> { RemoteMeasureRepository(get()) }
        single<PhotoRepository> { RemotePhotoRepository(get()) }
        single<PhotoImages> {
            StoragePhotoImages(inject(), NoPhotoFiles, { false }, Dispatchers.Default)
        }
        single<MeasurementRepository> { RemoteMeasurementRepository(get()) }
        single<FriendsRepository> { SupabaseFriendsRepository(inject(), Clock.System) }
        single<AccountStorage> { LocalStorageAccountStorage() }
        single<OwnerlessRows> { NoOwnerlessRows }
        single<OwnedRowsPurge> { NoOwnedRowsPurge }
        // The web's client holds one session, the active account's, which is the one deleted.
        single<AccountServer> { SupabaseAccountServer { get<SupabaseClient>() } }
    }
