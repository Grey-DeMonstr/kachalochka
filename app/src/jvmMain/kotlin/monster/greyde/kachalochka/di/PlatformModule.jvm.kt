package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.ui.account.SignInAvailable
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.account.UnavailableGoogleSignIn
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.core.module.Module
import org.koin.dsl.module

/** The screen tests stand in for Android here, so sign-in is bound as Android binds it. */
actual fun platformModule(): Module =
    module {
        single<ThemePreference> { InMemoryThemePreference() }
        single<GoogleSignIn> { UnavailableGoogleSignIn }
        single { SignInRequired(false) }
        single { SignInAvailable(get<SupabaseCredentials>().canSignInWithGoogleId) }
        single<SyncTrigger> { SyncTrigger {} }
    }
