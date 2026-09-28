package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.navigation.InMemoryTransitionPreference
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.sync.VisitStore
import monster.greyde.kachalochka.ui.account.SignInAvailable
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.account.UnavailableGoogleSignIn
import monster.greyde.kachalochka.ui.friends.InMemoryJoinCodeStore
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.JoinCodeStore
import monster.greyde.kachalochka.ui.friends.UnavailableInviteSharing
import monster.greyde.kachalochka.ui.photos.NoPhotoCapture
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.share.UnavailableTextSharing
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.core.module.Module
import org.koin.dsl.module

/** The screen tests stand in for Android here, so sign-in is bound as Android binds it. */
actual fun platformModule(): Module =
    module {
        single<ThemePreference> { InMemoryThemePreference() }
        single<TransitionPreference> { InMemoryTransitionPreference() }
        single<GoogleSignIn> { UnavailableGoogleSignIn }
        single { SignInRequired(false) }
        single { SignInAvailable(get<SupabaseCredentials>().canSignInWithGoogleId) }
        single<SyncTrigger> { SyncTrigger {} }
        single<InviteSharing> { UnavailableInviteSharing }
        single<TextSharing> { UnavailableTextSharing }
        single<JoinCodeStore> { InMemoryJoinCodeStore() }
        single { VisitStore.Device }
        single<PhotoCapture> { NoPhotoCapture }
        single<FailureLog> { FailureLog {} }
    }
