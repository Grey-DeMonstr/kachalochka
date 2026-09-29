package monster.greyde.kachalochka.di

import kotlinx.browser.window
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.navigation.LocalStorageTransitionPreference
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.report
import monster.greyde.kachalochka.sync.ServerSyncTrigger
import monster.greyde.kachalochka.sync.VisitStore
import monster.greyde.kachalochka.ui.account.RedirectGoogleSignIn
import monster.greyde.kachalochka.ui.account.SignInAvailable
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.friends.ClipboardInviteSharing
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.JoinCodeStore
import monster.greyde.kachalochka.ui.friends.LocalStorageJoinCodeStore
import monster.greyde.kachalochka.ui.photos.BrowserPhotoCapture
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.share.ClipboardTextSharing
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.strings.LanguagePreference
import monster.greyde.kachalochka.ui.strings.LocalStorageLanguagePreference
import monster.greyde.kachalochka.ui.strings.SystemLanguage
import monster.greyde.kachalochka.ui.theme.LocalStorageThemePreference
import monster.greyde.kachalochka.ui.theme.ThemePreference
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<ThemePreference> { LocalStorageThemePreference() }
        single<TransitionPreference> { LocalStorageTransitionPreference() }
        single<LanguagePreference> { LocalStorageLanguagePreference() }
        single { SystemLanguage { window.navigator.language } }
        single<GoogleSignIn> { RedirectGoogleSignIn(get()) }
        single { SignInRequired(true) }
        single { SignInAvailable(get<SupabaseCredentials>().isConfigured) }
        single<SyncTrigger> { ServerSyncTrigger() }
        single<InviteSharing> { ClipboardInviteSharing() }
        single<TextSharing> { ClipboardTextSharing() }
        single<JoinCodeStore> { LocalStorageJoinCodeStore() }
        single { VisitStore.Server }
        single<PhotoCapture> { BrowserPhotoCapture() }
        single<FailureLog> { FailureLog { report(it.toString()) } }
    }
