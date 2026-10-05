package monster.greyde.kachalochka.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.identity.AccountDeletion
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.gym.PhotoRepository
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.navigation.transitionMillisOrNull
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.friends.reading
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.AppStrings
import monster.greyde.kachalochka.ui.strings.LanguagePreference
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.theme.ThemePreference
import kotlin.time.Clock
import kotlin.time.Instant

const val NICKNAME_LENGTH = 40

/** Typed to confirm deleting the account, in any case. */
const val DELETE_WORD = "DELETE"

private val EARLIEST_BIRTH_DATE = CalendarDay(1900, 1, 1)

// A typo such as 18 cm or 1800 would otherwise yield a confident nonsense body fat percent.
private val heightRange = 50.0..250.0

/** [nickname] is null while nobody is signed in; an empty body field clears the stored value. */
data class ProfileUi(
    val nickname: String?,
    val placeholder: String,
    val sex: Sex?,
    val birthDate: String,
    val height: String,
    val birthDateValid: Boolean,
    val heightValid: Boolean,
    val canSave: Boolean,
    val weightUnit: PreferredWeightUnit = PreferredWeightUnit.Kg,
)

/**
 * The signed-in account's picture: [avatar] as saved, with a photo [chosen] or the chosen one
 * [removed] since, both waiting for "Применить".
 */
data class AvatarUi(
    val owner: UserId,
    val name: String,
    val avatar: Avatar,
    val chosen: ByteArray? = null,
    val removed: Boolean = false,
) {
    /** What the circle shows: a photo just taken, else the saved one, else Google's. */
    val shown: Avatar get() = if (removed || chosen != null) avatar.copy(photo = null) else avatar

    val canRemove: Boolean get() = chosen != null || (avatar.photo != null && !removed)
}

/** The settings kept on this device rather than in the profile. */
data class DeviceUi(
    val theme: ThemeMode,
    val transition: String,
    val language: AppLanguage,
)

/** Account deletion: offered while an account is signed in, under "Дополнительно". */
data class DeletionUi(
    val available: Boolean = false,
    val advancedOpen: Boolean = false,
    val confirming: Boolean = false,
    val word: String = "",
    val running: Boolean = false,
    val error: String? = null,
) {
    val canConfirm: Boolean get() = word.trim().equals(DELETE_WORD, ignoreCase = true)
}

class SettingsViewModel(
    private val profiles: ProfileRepository,
    private val accounts: Accounts,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
    private val accountDeletion: AccountDeletion,
    private val themes: ThemePreference,
    private val transitions: TransitionPreference,
    private val languages: LanguagePreference,
    private val photos: PhotoRepository,
    private val avatars: AccountAvatars,
) : ViewModel() {
    private val mutableProfile = MutableStateFlow<ProfileUi?>(null)
    val profile: StateFlow<ProfileUi?> = mutableProfile
    private var saved: ProfileUi? = null

    private val mutableAvatar = MutableStateFlow<AvatarUi?>(null)
    val avatar: StateFlow<AvatarUi?> = mutableAvatar
    private var savedAvatar: AvatarUi? = null

    private var savedDevice = storedDevice()
    private val mutableDevice = MutableStateFlow(savedDevice)
    val device: StateFlow<DeviceUi> = mutableDevice

    private val mutableCanApply = MutableStateFlow(false)
    val canApply: StateFlow<Boolean> = mutableCanApply

    private val mutableConfirmingLeave = MutableStateFlow(false)
    val confirmingLeave: StateFlow<Boolean> = mutableConfirmingLeave

    private val mutableDeletion = MutableStateFlow(DeletionUi())
    val deletion: StateFlow<DeletionUi> = mutableDeletion
    private val mutableFamily = MutableStateFlow(false)

    /** "Дети" and "Родители" are there for a signed-in account. */
    val familyAvailable: StateFlow<Boolean> = mutableFamily

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    private fun storedDevice() =
        DeviceUi(themes.mode.value, transitions.millis.value.toString(), languages.language.value)

    private suspend fun load() {
        val owner = currentUser.id()
        val account = accounts.accounts.value.firstOrNull { it.userId == owner }
        val ownsAccount = owner != null && account?.isManaged != true
        mutableDeletion.value = DeletionUi(available = ownsAccount)
        mutableFamily.value = ownsAccount
        // A managed child's profile is the child's own; only the device settings stay.
        if (account?.isManaged == true) {
            savedAvatar = null
            mutableAvatar.value = null
            saved = null
            mutableProfile.value = null
            refreshApply()
            return
        }
        val stored = profiles.forOwner(owner)
        val placeholder = account?.displayName.orEmpty()
        savedAvatar =
            account?.let {
                AvatarUi(it.userId, it.displayName, Avatar(stored?.avatarPhoto, it.pictureUrl))
            }
        mutableAvatar.value = savedAvatar
        val shown =
            ProfileUi(
                nickname = owner?.let { stored?.displayName.orEmpty() },
                placeholder = if (owner == null) "" else placeholder,
                sex = stored?.sex,
                birthDate = stored?.birthDate?.let(::dateText).orEmpty(),
                height = stored?.heightCm?.let(::formatNumber).orEmpty(),
                birthDateValid = true,
                heightValid = true,
                canSave = false,
                weightUnit = stored?.weightUnit ?: PreferredWeightUnit.Kg,
            )
        saved = shown
        mutableProfile.value = shown
        refreshApply()
    }

    fun type(text: String) =
        edit { shown -> shown.copy(nickname = shown.nickname?.let { text.take(NICKNAME_LENGTH) }) }

    fun chooseSex(sex: Sex) = edit { it.copy(sex = sex) }

    fun chooseWeightUnit(unit: PreferredWeightUnit) = edit { it.copy(weightUnit = unit) }

    fun typeBirthDate(text: String) = edit { it.copy(birthDate = text) }

    fun typeHeight(text: String) = edit { it.copy(height = text) }

    fun chooseAvatar(jpeg: ByteArray) = editAvatar { it.copy(chosen = jpeg, removed = false) }

    fun removeAvatar() = editAvatar { it.copy(chosen = null, removed = it.avatar.photo != null) }

    private fun editAvatar(change: (AvatarUi) -> AvatarUi) {
        mutableAvatar.value = change(mutableAvatar.value ?: return)
        refreshApply()
    }

    fun chooseTheme(mode: ThemeMode) = editDevice { it.copy(theme = mode) }

    fun chooseLanguage(language: AppLanguage) = editDevice { it.copy(language = language) }

    /** Text that is not a length in range is refused, so the field keeps its last valid value. */
    fun typeTransition(text: String) {
        if (transitionMillisOrNull(text) != null) editDevice { it.copy(transition = text) }
    }

    private fun edit(change: (ProfileUi) -> ProfileUi) {
        val edited = change(mutableProfile.value ?: return)
        val checked =
            edited.copy(
                birthDateValid =
                    edited.birthDate.isBlank() || birthDateOf(edited.birthDate) != null,
                heightValid = edited.height.isBlank() || heightOf(edited.height) != null,
            )
        mutableProfile.value =
            checked.copy(canSave = checked.valid && checked.copy(canSave = false) != saved)
        refreshApply()
    }

    private fun editDevice(change: (DeviceUi) -> DeviceUi) {
        mutableDevice.value = change(mutableDevice.value)
        refreshApply()
    }

    private val ProfileUi.valid: Boolean get() = birthDateValid && heightValid

    private fun profileChanged(): Boolean =
        mutableProfile.value?.let { it.copy(canSave = false) != saved?.copy(canSave = false) } ==
            true

    private fun avatarChanged(): Boolean = mutableAvatar.value != savedAvatar

    private fun changed(): Boolean =
        profileChanged() || avatarChanged() || mutableDevice.value != savedDevice

    private fun refreshApply() {
        mutableCanApply.value = changed() && mutableProfile.value?.valid != false
    }

    /** Leaving the screen must not stop the writes halfway, so they finish on their own. */
    fun apply() {
        if (!mutableCanApply.value) return
        val shown = mutableProfile.value
        val writeProfile = profileChanged()
        val avatar = mutableAvatar.value?.takeIf { avatarChanged() }
        val device = mutableDevice.value
        saved = shown?.copy(canSave = false)
        mutableProfile.value = saved
        savedDevice = device
        savedAvatar = mutableAvatar.value
        refreshApply()
        viewModelScope.launch {
            withContext(NonCancellable) {
                themes.set(device.theme)
                languages.set(device.language)
                transitionMillisOrNull(device.transition)?.let { transitions.set(it) }
                if ((writeProfile || avatar != null) && shown != null) {
                    saveProfile(shown.takeIf { writeProfile }, avatar)
                }
            }
        }
    }

    private suspend fun saveProfile(
        shown: ProfileUi?,
        avatar: AvatarUi?,
    ) {
        if (!currentUser.writesPrivateRows()) return
        val owner = currentUser.id()
        val now = clock.now()
        // Read afresh so a friend colour saved meanwhile survives.
        val existing = profiles.forOwner(owner) ?: Profile.new(owner, now)
        val edited =
            shown?.let {
                existing.copy(
                    displayName = it.nickname ?: existing.displayName,
                    sex = it.sex,
                    birthDate = birthDateOf(it.birthDate),
                    heightCm = heightOf(it.height),
                    weightUnit = it.weightUnit,
                )
            } ?: existing
        val photo =
            if (avatar !=
                null
            ) {
                replaceAvatar(existing, avatar, now)
            } else {
                existing.avatarPhoto
            }
        profiles.upsert(edited.copy(avatarPhoto = photo, updatedAt = now))
        sync.request()
        if (avatar != null) {
            avatars.refresh()
            mutableAvatar.value =
                AvatarUi(avatar.owner, avatar.name, avatar.avatar.copy(photo = photo))
            savedAvatar = mutableAvatar.value
            refreshApply()
        }
    }

    /** The profile's avatar photos give way to the one [avatar] chose, if any, which is returned. */
    private suspend fun replaceAvatar(
        profile: Profile,
        avatar: AvatarUi,
        now: Instant,
    ): PhotoId? {
        val holder = MachineId(profile.id.value)
        photos
            .forMachine(
                holder,
            ).forEach { photos.upsert(it.copy(deleted = true, updatedAt = now)) }
        val jpeg = avatar.chosen ?: return null
        val photo = Photo.new(holder, profile.userId, now)
        photos.add(photo, jpeg)
        return photo.id
    }

    /** True when the screen may close now; otherwise it asks about the unapplied changes. */
    fun requestLeave(): Boolean {
        if (!changed()) return true
        mutableConfirmingLeave.value = true
        return false
    }

    fun stay() {
        mutableConfirmingLeave.value = false
    }

    fun discard() {
        mutableConfirmingLeave.value = false
        mutableProfile.value = saved
        mutableAvatar.value = savedAvatar
        mutableDevice.value = savedDevice
        refreshApply()
    }

    fun toggleAdvanced() {
        mutableDeletion.value =
            mutableDeletion.value.copy(advancedOpen = !mutableDeletion.value.advancedOpen)
    }

    fun askToDelete() {
        if (!mutableDeletion.value.available || mutableDeletion.value.running) return
        mutableDeletion.value =
            mutableDeletion.value.copy(confirming = true, word = "", error = null)
    }

    fun typeDeleteWord(text: String) {
        mutableDeletion.value =
            mutableDeletion.value.copy(word = text.take(DELETE_WORD.length + 4))
    }

    fun cancelDelete() {
        mutableDeletion.value = mutableDeletion.value.copy(confirming = false, word = "")
    }

    /**
     * Signing the account out on success reloads the screen for whoever is active next. Leaving
     * the screen must not stop it halfway, between the server forgetting the account and the
     * device.
     */
    fun confirmDelete() {
        if (!mutableDeletion.value.canConfirm) return
        mutableDeletion.value = mutableDeletion.value.copy(confirming = false, word = "")
        viewModelScope.launch {
            val owner = currentUser.id() ?: return@launch
            mutableDeletion.value = mutableDeletion.value.copy(running = true)
            reading { withContext(NonCancellable) { accountDeletion.delete(owner) } }.onFailure {
                mutableDeletion.value =
                    DeletionUi(
                        available = true,
                        advancedOpen = true,
                        error = AppStrings.current.offline,
                    )
            }
        }
    }

    private fun birthDateOf(text: String): CalendarDay? {
        val parts = text.trim().split('.')
        if (parts.size != 3 || parts[2].length != 4) return null
        val (day, month, year) = parts.map { it.toIntOrNull() ?: return null }
        val date =
            try {
                CalendarDay(year, month, day)
            } catch (_: IllegalArgumentException) {
                return null
            }
        return date.takeIf { it >= EARLIEST_BIRTH_DATE && it <= today() }
    }

    private fun heightOf(text: String): Double? = parseDecimal(text)?.takeIf { it in heightRange }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }
}

private fun dateText(day: CalendarDay): String =
    listOf(day.day.toString().padStart(2, '0'), day.month.toString().padStart(2, '0'), day.year)
        .joinToString(".")
