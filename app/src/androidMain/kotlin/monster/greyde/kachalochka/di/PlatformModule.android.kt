package monster.greyde.kachalochka.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import monster.greyde.kachalochka.FailureLog
import monster.greyde.kachalochka.LastFailure
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.supabase.webAppUrlFromBuild
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.navigation.PageFooter
import monster.greyde.kachalochka.sync.VisitStore
import monster.greyde.kachalochka.sync.WorkManagerSyncTrigger
import monster.greyde.kachalochka.ui.account.ActivityHolder
import monster.greyde.kachalochka.ui.account.CredentialManagerGoogleSignIn
import monster.greyde.kachalochka.ui.account.SignInAvailable
import monster.greyde.kachalochka.ui.account.SignInRequired
import monster.greyde.kachalochka.ui.family.PARENT_CODE
import monster.greyde.kachalochka.ui.family.TRAINER_CODE
import monster.greyde.kachalochka.ui.friends.InMemoryJoinCodeStore
import monster.greyde.kachalochka.ui.friends.InviteSharing
import monster.greyde.kachalochka.ui.friends.JoinCodeStore
import monster.greyde.kachalochka.ui.friends.ShareSheetInviteSharing
import monster.greyde.kachalochka.ui.network.AndroidDeviceNetwork
import monster.greyde.kachalochka.ui.network.DeviceNetwork
import monster.greyde.kachalochka.ui.network.ProxyPreference
import monster.greyde.kachalochka.ui.photos.PhotoCapture
import monster.greyde.kachalochka.ui.photos.SystemPhotoCapture
import monster.greyde.kachalochka.ui.settings.DataStoreDeviceStore
import monster.greyde.kachalochka.ui.settings.DeviceStore
import monster.greyde.kachalochka.ui.share.CardSharing
import monster.greyde.kachalochka.ui.share.ClipboardTextCopying
import monster.greyde.kachalochka.ui.share.PhotoSharing
import monster.greyde.kachalochka.ui.share.ShareSheetCardSharing
import monster.greyde.kachalochka.ui.share.ShareSheetPhotoSharing
import monster.greyde.kachalochka.ui.share.ShareSheetTextSharing
import monster.greyde.kachalochka.ui.share.TextCopying
import monster.greyde.kachalochka.ui.share.TextSharing
import monster.greyde.kachalochka.ui.strings.SystemLanguage
import monster.greyde.kachalochka.ui.theme.EdgeToEdgeSystemBars
import monster.greyde.kachalochka.ui.theme.SystemBars
import monster.greyde.kachalochka.ui.timer.RestAlarm
import monster.greyde.kachalochka.ui.timer.RestSoundChooser
import monster.greyde.kachalochka.ui.timer.ServiceRestAlarm
import monster.greyde.kachalochka.ui.timer.SystemRestSoundChooser
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import kotlin.time.Clock

private const val LOG_TAG = "Kachalochka"

actual fun platformModule(): Module =
    module {
        single<DataStore<Preferences>> {
            val context: Context = androidContext()
            PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    context.filesDir
                        .resolve("theme.preferences_pb")
                        .absolutePath
                        .toPath()
                },
            )
        }
        // Built while Koin starts, so the stored settings are in hand before the first Activity.
        single<DeviceStore>(createdAtStart = true) { DataStoreDeviceStore(get()) }
        single<RestAlarm> { ServiceRestAlarm(androidContext()) }
        single<RestSoundChooser> { SystemRestSoundChooser(androidContext()) }
        single { SystemLanguage { Locale.getDefault().toLanguageTag() } }
        singleOf(::ActivityHolder)
        single { WorkManagerSyncTrigger(androidContext()) } bind SyncTrigger::class
        single { VisitStore.Device }
        single {
            LastFailure(Clock.System) { Log.w(LOG_TAG, "Background work failed", it) }
        } bind FailureLog::class
        // Replaces the core binding, so every client goes through the stored proxy. The address
        // stays unresolved: OkHttp looks it up later, and Android refuses it on the main thread.
        single {
            val setting = get<ProxyPreference>().setting.value
            val endpoint = setting.endpoint
            val proxy =
                endpoint?.let { (host, port) ->
                    Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
                }
            SupabaseCredentials.fromBuild().copy(proxy = proxy, proxyLogin = setting.proxyLogin)
        }
        single<DeviceNetwork> { AndroidDeviceNetwork(androidContext(), get(), get()) }
        single { SignInRequired(false) }
        single { SignInAvailable(get<SupabaseCredentials>().canSignInWithGoogleId) }
        single<TextSharing> { ShareSheetTextSharing(androidContext()) }
        single<TextCopying> { ClipboardTextCopying(androidContext()) }
        single<PhotoSharing> { ShareSheetPhotoSharing(androidContext()) }
        single<CardSharing> { ShareSheetCardSharing(androidContext()) }
        single<InviteSharing> {
            ShareSheetInviteSharing(get(), webAppUrlFromBuild().ifBlank { null })
        }
        single<JoinCodeStore> { InMemoryJoinCodeStore() }
        single<JoinCodeStore>(named(PARENT_CODE)) { InMemoryJoinCodeStore() }
        single<JoinCodeStore>(named(TRAINER_CODE)) { InMemoryJoinCodeStore() }
        single<PhotoCapture> { SystemPhotoCapture(androidContext()) }
        single<PageFooter> { PageFooter {} }
        single<SystemBars> {
            val activities: ActivityHolder = get()
            EdgeToEdgeSystemBars { activities.current }
        }
        single<GoogleSignIn> {
            val activities: ActivityHolder = get()
            CredentialManagerGoogleSignIn(
                inject(),
                { activities.current },
                get<SupabaseCredentials>().googleWebClientId,
            )
        }
    }
