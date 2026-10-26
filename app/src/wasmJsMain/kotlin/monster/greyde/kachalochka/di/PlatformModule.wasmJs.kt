package monster.greyde.kachalochka.di

import kotlinx.browser.window
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.navigation.DocumentFooter
import monster.greyde.kachalochka.navigation.LocalStorageTransitionPreference
import monster.greyde.kachalochka.navigation.PageFooter
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.report
import monster.greyde.kachalochka.sync.ServerSyncTrigger
import monster.greyde.kachalochka.sync.VisitStore
import monster.greyde.kachalochka.ui.account.RedirectGoogleSignIn
import monster.greyde.kachalochka.ui.account.SignInAvailable
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.calendar.LocalStorageVisitsModePreference
import monster.greyde.kachalochka.ui.calendar.VisitsModePreference
import monster.greyde.kachalochka.ui.family.PARENT_CODE
import monster.greyde.kachalochka.ui.family.TRAINER_CODE
import monster.greyde.kachalochka.ui.friends.ClipboardInviteSharing
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.JoinCodeStore
import monster.greyde.kachalochka.ui.friends.LocalStorageJoinCodeStore
import monster.greyde.kachalochka.ui.photos.BrowserPhotoCapture
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.share.BrowserPhotoSharing
import monster.greyde.kachalochka.ui.share.BrowserTextCopying
import monster.greyde.kachalochka.ui.share.ClipboardTextSharing
import monster.greyde.kachalochka.ui.share.PhotoSharing
import monster.greyde.kachalochka.ui.share.TextCopying
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.strings.LanguagePreference
import monster.greyde.kachalochka.ui.strings.LocalStorageLanguagePreference
import monster.greyde.kachalochka.ui.strings.SystemLanguage
import monster.greyde.kachalochka.ui.theme.LocalStorageThemePreference
import monster.greyde.kachalochka.ui.theme.SystemBars
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.timer.FixedRestSound
import monster.greyde.kachalochka.ui.timer.LocalStorageRestSoundPreference
import monster.greyde.kachalochka.ui.timer.RestAlarm
import monster.greyde.kachalochka.ui.timer.RestSoundChooser
import monster.greyde.kachalochka.ui.timer.RestSoundPreference
import monster.greyde.kachalochka.ui.timer.WebAudioRestAlarm
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<ThemePreference> { LocalStorageThemePreference() }
        single<TransitionPreference> { LocalStorageTransitionPreference() }
        single<VisitsModePreference> { LocalStorageVisitsModePreference() }
        single<LanguagePreference> { LocalStorageLanguagePreference() }
        single<RestSoundPreference> { LocalStorageRestSoundPreference() }
        single<RestAlarm> { WebAudioRestAlarm(get()) }
        single<RestSoundChooser> { FixedRestSound }
        single { SystemLanguage { window.navigator.language } }
        single<GoogleSignIn> { RedirectGoogleSignIn(get()) }
        single { SignInRequired(true) }
        single { SignInAvailable(get<SupabaseCredentials>().isConfigured) }
        single<SyncTrigger> { ServerSyncTrigger() }
        single<InviteSharing> { ClipboardInviteSharing() }
        single<TextSharing> { ClipboardTextSharing() }
        single<TextCopying> { BrowserTextCopying() }
        single<PhotoSharing> { BrowserPhotoSharing() }
        single<JoinCodeStore> { LocalStorageJoinCodeStore() }
        single<JoinCodeStore>(named(PARENT_CODE)) {
            LocalStorageJoinCodeStore("kachalochka.parentCode")
        }
        single<JoinCodeStore>(named(TRAINER_CODE)) {
            LocalStorageJoinCodeStore("kachalochka.trainerCode")
        }
        single { VisitStore.Server }
        single<PhotoCapture> { BrowserPhotoCapture() }
        single<FailureLog> { FailureLog { report(it.toString()) } }
        single<PageFooter> { DocumentFooter() }
        single<SystemBars> { SystemBars {} }
    }
