package monster.greyde.kachalochka.di

import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.navigation.InMemoryTransitionPreference
import monster.greyde.kachalochka.navigation.PageFooter
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.sync.VisitStore
import monster.greyde.kachalochka.ui.account.SignInAvailable
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.account.UnavailableGoogleSignIn
import monster.greyde.kachalochka.ui.calendar.InMemoryVisitsModePreference
import monster.greyde.kachalochka.ui.calendar.VisitsModePreference
import monster.greyde.kachalochka.ui.family.PARENT_CODE
import monster.greyde.kachalochka.ui.family.TRAINER_CODE
import monster.greyde.kachalochka.ui.friends.InMemoryJoinCodeStore
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.JoinCodeStore
import monster.greyde.kachalochka.ui.friends.UnavailableInviteSharing
import monster.greyde.kachalochka.ui.photos.NoPhotoCapture
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.share.PhotoSharing
import monster.greyde.kachalochka.ui.share.TextCopying
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.share.UnavailablePhotoSharing
import monster.greyde.kachalochka.ui.share.UnavailableTextCopying
import monster.greyde.kachalochka.ui.share.UnavailableTextSharing
import monster.greyde.kachalochka.ui.strings.InMemoryLanguagePreference
import monster.greyde.kachalochka.ui.strings.LanguagePreference
import monster.greyde.kachalochka.ui.strings.SystemLanguage
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.SystemBars
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.timer.FixedRestSound
import monster.greyde.kachalochka.ui.timer.InMemoryRestSoundPreference
import monster.greyde.kachalochka.ui.timer.NoRestAlarm
import monster.greyde.kachalochka.ui.timer.RestAlarm
import monster.greyde.kachalochka.ui.timer.RestSoundChooser
import monster.greyde.kachalochka.ui.timer.RestSoundPreference
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The screen tests stand in for Android here, so sign-in is bound as Android binds it. */
actual fun platformModule(): Module =
    module {
        single<ThemePreference> { InMemoryThemePreference() }
        single<TransitionPreference> { InMemoryTransitionPreference() }
        single<VisitsModePreference> { InMemoryVisitsModePreference() }
        single<LanguagePreference> { InMemoryLanguagePreference() }
        single<RestSoundPreference> { InMemoryRestSoundPreference() }
        single<RestAlarm> { NoRestAlarm }
        single<RestSoundChooser> { FixedRestSound }
        // The host tests assert Russian text, whatever the machine running them speaks.
        single { SystemLanguage { "ru" } }
        single<GoogleSignIn> { UnavailableGoogleSignIn }
        single { SignInRequired(false) }
        single { SignInAvailable(get<SupabaseCredentials>().canSignInWithGoogleId) }
        single<SyncTrigger> { SyncTrigger {} }
        single<InviteSharing> { UnavailableInviteSharing }
        single<TextSharing> { UnavailableTextSharing }
        single<TextCopying> { UnavailableTextCopying }
        single<PhotoSharing> { UnavailablePhotoSharing }
        single<JoinCodeStore> { InMemoryJoinCodeStore() }
        single<JoinCodeStore>(named(PARENT_CODE)) { InMemoryJoinCodeStore() }
        single<JoinCodeStore>(named(TRAINER_CODE)) { InMemoryJoinCodeStore() }
        single { VisitStore.Device }
        single<PhotoCapture> { NoPhotoCapture }
        single<FailureLog> { FailureLog {} }
        single<PageFooter> { PageFooter {} }
        single<SystemBars> { SystemBars {} }
    }
