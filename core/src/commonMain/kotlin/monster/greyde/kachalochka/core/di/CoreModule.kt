package monster.greyde.kachalochka.core.di

import io.github.jan.supabase.SupabaseClient
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.ActiveAccountUser
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.data.identity.SupabaseSessions
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.supabase.supabaseClient
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import org.koin.dsl.module

val coreModule =
    module {
        single { SupabaseCredentials.fromBuild() }
        single<SupabaseClient> { supabaseClient(get()) }
        single<AccountStore> { PersistedAccountStore(get()) }
        single<SessionActivation> { SupabaseSessions(get()) }
        single<CurrentUser> { ActiveAccountUser(get()) }
        single { Accounts(get(), get(), get(), get()) }
    }
