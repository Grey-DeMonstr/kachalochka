package monster.greyde.kachalochka.core.di

import kotlinx.coroutines.Dispatchers
import monster.greyde.kachalochka.core.data.db.kachalochkaDatabase
import monster.greyde.kachalochka.core.data.family.SupabaseFamilyRepository
import monster.greyde.kachalochka.core.data.friends.SupabaseFriendsRepository
import monster.greyde.kachalochka.core.data.friends.TraineeFriends
import monster.greyde.kachalochka.core.data.gym.LocalMachineLinkRepository
import monster.greyde.kachalochka.core.data.gym.LocalMachineRepository
import monster.greyde.kachalochka.core.data.gym.LocalPhotoRepository
import monster.greyde.kachalochka.core.data.gym.LocalPlanRepository
import monster.greyde.kachalochka.core.data.gym.LocalVisitRepository
import monster.greyde.kachalochka.core.data.gym.LocalWorkoutSetRepository
import monster.greyde.kachalochka.core.data.gym.PhotoImages
import monster.greyde.kachalochka.core.data.gym.StoragePhotoImages
import monster.greyde.kachalochka.core.data.identity.AccountServer
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.AccountTokens
import monster.greyde.kachalochka.core.data.identity.OwnedRowsPurge
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.identity.SqlOwnedRowsPurge
import monster.greyde.kachalochka.core.data.identity.SqlOwnerlessRows
import monster.greyde.kachalochka.core.data.identity.SqlWatermarkReset
import monster.greyde.kachalochka.core.data.identity.SupabaseAccountServer
import monster.greyde.kachalochka.core.data.identity.WatermarkReset
import monster.greyde.kachalochka.core.data.measures.LocalMeasureRepository
import monster.greyde.kachalochka.core.data.measures.LocalMeasurementRepository
import monster.greyde.kachalochka.core.data.profile.LocalProfileRepository
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.supabase.activeAccountSupabaseClient
import monster.greyde.kachalochka.core.data.supabase.ownerSupabaseClient
import monster.greyde.kachalochka.core.data.supabase.syncSupabaseClient
import monster.greyde.kachalochka.core.data.sync.LocalSyncRows
import monster.greyde.kachalochka.core.data.sync.OutboxDao
import monster.greyde.kachalochka.core.data.sync.SupabaseSyncGateway
import monster.greyde.kachalochka.core.data.sync.SyncGateway
import monster.greyde.kachalochka.core.data.sync.SyncPass
import monster.greyde.kachalochka.core.data.sync.SyncSession
import monster.greyde.kachalochka.core.data.sync.SyncWatermarks
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.gym.PlanRepository
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

private val ACTIVE_ACCOUNT_CLIENT = named("activeAccountClient")

/** Everything above the driver is the same on both SQLDelight targets; only the driver differs. */
internal fun sqlModule(): Module =
    module {
        single { kachalochkaDatabase(get()) }
        singleOf(::OutboxDao)
        single<ProfileRepository> { LocalProfileRepository(get(), get(), Dispatchers.IO) }
        single<MachineRepository> { LocalMachineRepository(get(), get(), Dispatchers.IO) }
        single<PlanRepository> { LocalPlanRepository(get(), get(), Dispatchers.IO) }
        single<VisitRepository> { LocalVisitRepository(get(), get(), Dispatchers.IO) }
        single<WorkoutSetRepository> { LocalWorkoutSetRepository(get(), get(), Dispatchers.IO) }
        single<MachineLinkRepository> { LocalMachineLinkRepository(get(), get(), Dispatchers.IO) }
        single<MeasureRepository> { LocalMeasureRepository(get(), get(), Dispatchers.IO) }
        single<PhotoRepository> { LocalPhotoRepository(get(), get(), get(), Dispatchers.IO) }
        single<MeasurementRepository> {
            LocalMeasurementRepository(get(), get(), Dispatchers.IO)
        }
        single<OwnerlessRows> { SqlOwnerlessRows(get(), get(), Clock.System, Dispatchers.IO) }
        singleOf(::SyncWatermarks)
        single<WatermarkReset> { SqlWatermarkReset(get(), Dispatchers.IO) }
        singleOf(::LocalSyncRows)
        singleOf(::SyncSession)
        single<SyncGateway> {
            val credentials: SupabaseCredentials = get()
            val tokens: AccountTokens = get()
            val session: SyncSession = get()
            SupabaseSyncGateway(lazy { syncSupabaseClient(credentials, tokens, session) })
        }
        single { SyncPass(get(), get(), get(), get(), get(), get(), Dispatchers.IO) }
        single(ACTIVE_ACCOUNT_CLIENT) { activeAccountSupabaseClient(get(), get(), get()) }
        single<FriendsRepository> {
            val store: AccountStore = get()
            TraineeFriends(
                SupabaseFriendsRepository(inject(ACTIVE_ACCOUNT_CLIENT), Clock.System),
            ) {
                store.accounts.value
            }
        }
        single<FamilyRepository> { SupabaseFamilyRepository(inject(ACTIVE_ACCOUNT_CLIENT)) }
        single<OwnedRowsPurge> { SqlOwnedRowsPurge(get(), get(), Dispatchers.IO) }
        single<AccountServer> {
            val credentials: SupabaseCredentials = get()
            val tokens: AccountTokens = get()
            SupabaseAccountServer { owner -> ownerSupabaseClient(credentials, tokens, owner) }
        }
        single<PhotoImages> {
            val store: AccountStore = get()
            // An own photo pulled from another device is kept like the ones taken here.
            StoragePhotoImages(
                inject(ACTIVE_ACCOUNT_CLIENT),
                get(),
                { photo -> store.accounts.value.any { it.userId == photo.userId } },
                Dispatchers.IO,
            )
        }
    }
