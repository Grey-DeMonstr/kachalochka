package monster.greyde.kachalochka.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.core.domain.profile.ageOn
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.LanguagePreference
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.timer.RestSound
import monster.greyde.kachalochka.ui.timer.RestSoundPreference
import kotlin.math.abs

/** The lengths a screen transition is offered in. */
enum class TransitionPreset(
    val millis: Int,
) {
    None(0),
    Fast(150),
    Normal(300),
    Smooth(500),
    ;

    companion object {
        /** A length stored before the presets shows as the closest of them. */
        fun of(millis: Int): TransitionPreset = entries.minBy { abs(it.millis - millis) }
    }
}

/** The profile in one line; [name] is empty while nobody is signed in. */
data class ProfileCardUi(
    val owner: UserId?,
    val name: String,
    val avatar: Avatar = Avatar(),
    val sex: Sex? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightUnit: PreferredWeightUnit = PreferredWeightUnit.Kg,
)

/** How many children and parents the account has, null until read or while offline. */
data class PeopleUi(
    val children: Int?,
    val guardians: Int?,
)

/** What the hub leads to; each part is null where the active account has no such screen. */
data class HubUi(
    val card: ProfileCardUi? = null,
    val people: PeopleUi? = null,
    val email: String? = null,
)

/** The settings kept on this device rather than in the profile. */
data class DeviceUi(
    val theme: ThemeMode,
    val transition: TransitionPreset,
    val language: AppLanguage,
    val restSound: RestSound,
)

class SettingsViewModel(
    private val profiles: ProfileRepository,
    private val accounts: Accounts,
    private val currentUser: CurrentUser,
    private val clock: DeviceClock,
    private val family: FamilyRepository,
    private val themes: ThemePreference,
    private val transitions: TransitionPreference,
    private val languages: LanguagePreference,
    private val restSounds: RestSoundPreference,
) : ViewModel() {
    private val mutableHub = MutableStateFlow(HubUi())
    val hub: StateFlow<HubUi> = mutableHub
    private var shownOwner: UserId? = null

    private val mutableDevice =
        MutableStateFlow(
            DeviceUi(
                themes.mode.value,
                TransitionPreset.of(transitions.millis.value),
                languages.language.value,
                restSounds.sound.value,
            ),
        )
    val device: StateFlow<DeviceUi> = mutableDevice

    /** The hub follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    /** Reads again what the screens opened from the hub may have changed. */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val owner = currentUser.id()
        val account = accounts.accounts.value.firstOrNull { it.userId == owner }
        if (account?.isManaged == true) {
            mutableHub.value = HubUi()
            return
        }
        val stored = profiles.forOwner(owner)
        val card =
            ProfileCardUi(
                owner = owner,
                name =
                    stored?.displayName?.takeIf { account != null && it.isNotBlank() }
                        ?: account?.shownName.orEmpty(),
                avatar = Avatar(stored?.avatarPhoto, account?.pictureUrl),
                sex = stored?.sex,
                age = stored?.birthDate?.let { ageOn(it, clock.today()) },
                heightCm = stored?.heightCm,
                weightUnit = stored?.weightUnit ?: PreferredWeightUnit.Kg,
            )
        // A refresh keeps the counts shown until the server answers again.
        val shown = mutableHub.value.people?.takeIf { shownOwner == owner }
        shownOwner = owner
        mutableHub.value =
            HubUi(card, account?.let { shown ?: PeopleUi(null, null) }, account?.email)
        if (account == null) return
        val people =
            catching { family.family() }
                .map { PeopleUi(it.children.size, it.guardians.size) }
                .getOrDefault(PeopleUi(null, null))
        // Another account may have become active while the server answered.
        if (currentUser.id() == owner) mutableHub.update { it.copy(people = people) }
    }

    fun chooseTheme(mode: ThemeMode) {
        mutableDevice.update { it.copy(theme = mode) }
        keep { themes.set(mode) }
    }

    fun chooseLanguage(language: AppLanguage) {
        mutableDevice.update { it.copy(language = language) }
        keep { languages.set(language) }
    }

    fun chooseTransition(preset: TransitionPreset) {
        mutableDevice.update { it.copy(transition = preset) }
        keep { transitions.set(preset.millis) }
    }

    fun chooseRestSound(enabled: Boolean) = keepSound { it.copy(enabled = enabled) }

    fun pickRestSound(uri: String?) = keepSound { it.copy(uri = uri) }

    private fun keepSound(change: (RestSound) -> RestSound) {
        val sound = change(mutableDevice.value.restSound)
        mutableDevice.update { it.copy(restSound = sound) }
        keep { restSounds.set(sound) }
    }

    /** Leaving the screen must not stop a write halfway, so it finishes on its own. */
    private fun keep(write: suspend () -> Unit) {
        viewModelScope.launch { withContext(NonCancellable) { write() } }
    }
}
