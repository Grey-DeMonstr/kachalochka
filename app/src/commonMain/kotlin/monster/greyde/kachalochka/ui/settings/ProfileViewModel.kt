package monster.greyde.kachalochka.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import monster.greyde.kachalochka.core.domain.measures.CYCLE_LENGTHS
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.parseDecimal
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

const val NICKNAME_LENGTH = 40

/** How long typing pauses before the typed fields are saved. */
val SAVE_DELAY = 1.seconds

private val EARLIEST_BIRTH_DATE = CalendarDay(1900, 1, 1)

// A typo such as 18 cm or 1800 would otherwise yield a confident nonsense body fat percent.
private val heightRange = 50.0..250.0

/**
 * [nickname] is null while nobody is signed in; an empty body field clears the stored value, and
 * an invalid one keeps it.
 */
data class ProfileUi(
    val nickname: String?,
    val placeholder: String,
    val sex: Sex?,
    val birthDate: String,
    val height: String,
    val birthDateValid: Boolean,
    val heightValid: Boolean,
    val weightUnit: PreferredWeightUnit = PreferredWeightUnit.Kg,
    val cycleLength: String = "",
    val cycleLengthValid: Boolean = true,
) {
    val showsCycleLength: Boolean get() = sex == Sex.Female
}

/** The signed-in account's picture: [avatar] as saved, with a photo [chosen] while it is saved. */
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

class ProfileViewModel(
    private val profiles: ProfileRepository,
    private val accounts: Accounts,
    private val currentUser: CurrentUser,
    private val clock: DeviceClock,
    private val sync: SyncTrigger,
    private val photos: PhotoRepository,
    private val avatars: AccountAvatars,
) : ViewModel() {
    private val mutableProfile = MutableStateFlow<ProfileUi?>(null)
    val profile: StateFlow<ProfileUi?> = mutableProfile
    private var saved: ProfileUi? = null

    // Whose profile is shown: an edit is saved for them even if a switch lands meanwhile.
    private var profileOwner: UserId? = null

    private val mutableAvatar = MutableStateFlow<AvatarUi?>(null)
    val avatar: StateFlow<AvatarUi?> = mutableAvatar

    private var pending: Job? = null

    // A save reads the profile and writes it back, so two must not interleave.
    private val writing = Mutex()

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    private suspend fun load() {
        saveNow()
        val owner = currentUser.id()
        val account = accounts.accounts.value.firstOrNull { it.userId == owner }
        // A managed child's profile is the child's own.
        if (account?.isManaged == true) {
            profileOwner = null
            mutableAvatar.value = null
            saved = null
            mutableProfile.value = null
            return
        }
        val stored = profiles.forOwner(owner)
        if (owner != null && stored != null) accounts.rememberNickname(owner, stored.displayName)
        // Read again: the nickname just remembered names the avatar.
        mutableAvatar.value =
            accounts.accounts.value.firstOrNull { it.userId == owner }?.let {
                AvatarUi(
                    it.userId,
                    it.shownName,
                    Avatar(stored?.avatarPhoto, it.pictureUrl),
                )
            }
        val shown =
            ProfileUi(
                nickname = owner?.let { stored?.displayName.orEmpty() },
                placeholder = account?.displayName.orEmpty(),
                sex = stored?.sex,
                birthDate = stored?.birthDate?.let(::dateText).orEmpty(),
                height = stored?.heightCm?.let(::formatNumber).orEmpty(),
                birthDateValid = true,
                heightValid = true,
                weightUnit = stored?.weightUnit ?: PreferredWeightUnit.Kg,
                cycleLength = stored?.cycleLength?.toString().orEmpty(),
            )
        profileOwner = owner
        saved = shown
        mutableProfile.value = shown
    }

    fun type(text: String) =
        typed { shown ->
            shown.copy(nickname = shown.nickname?.let { text.take(NICKNAME_LENGTH) })
        }

    fun typeBirthDate(text: String) = typed { it.copy(birthDate = text) }

    fun typeHeight(text: String) = typed { it.copy(height = text) }

    fun typeCycleLength(text: String) = typed { it.copy(cycleLength = text) }

    fun chooseSex(sex: Sex) = chosen { it.copy(sex = sex) }

    fun chooseWeightUnit(unit: PreferredWeightUnit) = chosen { it.copy(weightUnit = unit) }

    /** Saves what is typed at once, since the screen closes. */
    fun leave() = saveNow()

    private fun typed(change: (ProfileUi) -> ProfileUi) {
        if (!edit(change)) return
        pending?.cancel()
        pending =
            viewModelScope.launch {
                delay(SAVE_DELAY)
                saveNow()
            }
    }

    private fun chosen(change: (ProfileUi) -> ProfileUi) {
        if (edit(change)) saveNow()
    }

    private fun edit(change: (ProfileUi) -> ProfileUi): Boolean {
        val edited = change(mutableProfile.value ?: return false)
        mutableProfile.value =
            edited.copy(
                birthDateValid =
                    edited.birthDate.isBlank() || birthDateOf(edited.birthDate) != null,
                heightValid = edited.height.isBlank() || heightOf(edited.height) != null,
                cycleLengthValid =
                    edited.cycleLength.isBlank() || cycleLengthOf(edited.cycleLength) != null,
            )
        return true
    }

    private fun saveNow() {
        pending?.cancel()
        pending = null
        val shown = mutableProfile.value ?: return
        if (shown == saved) return
        saved = shown
        val owner = profileOwner
        keep { saveProfile(owner) { existing -> edited(existing, shown) } }
    }

    fun chooseAvatar(jpeg: ByteArray) = saveAvatar { it.copy(chosen = jpeg, removed = false) }

    fun removeAvatar() = saveAvatar { it.copy(chosen = null, removed = it.avatar.photo != null) }

    private fun saveAvatar(change: (AvatarUi) -> AvatarUi) {
        val avatar = change(mutableAvatar.value ?: return)
        mutableAvatar.value = avatar
        val owner = profileOwner
        keep {
            var photo: PhotoId? = null
            saveProfile(owner) { existing ->
                photo = replaceAvatar(existing, avatar, clock.now())
                existing.copy(avatarPhoto = photo)
            }
            avatars.refresh()
            // The screen has moved on to another account's picture.
            if (profileOwner == owner) {
                mutableAvatar.value =
                    AvatarUi(avatar.owner, avatar.name, avatar.avatar.copy(photo = photo))
            }
        }
    }

    /** Leaving the screen must not stop a write halfway, so it finishes on its own. */
    private fun keep(write: suspend () -> Unit) {
        viewModelScope.launch { withContext(NonCancellable) { write() } }
    }

    /** A managed child's screen shows no profile, so [owner] is never one. */
    private suspend fun saveProfile(
        owner: UserId?,
        change: suspend (Profile) -> Profile,
    ) = writing.withLock {
        val now = clock.now()
        // Read afresh so a friend colour saved meanwhile survives.
        val existing = profiles.forOwner(owner) ?: Profile.new(owner, now)
        val edited = change(existing)
        profiles.upsert(edited.copy(updatedAt = now))
        owner?.let { accounts.rememberNickname(it, edited.displayName) }
        sync.request()
    }

    // A hidden or invalid field keeps what is stored.
    private fun edited(
        existing: Profile,
        shown: ProfileUi,
    ) = existing.copy(
        displayName = shown.nickname ?: existing.displayName,
        sex = shown.sex,
        birthDate = if (shown.birthDateValid) birthDateOf(shown.birthDate) else existing.birthDate,
        heightCm = if (shown.heightValid) heightOf(shown.height) else existing.heightCm,
        weightUnit = shown.weightUnit,
        cycleLength =
            if (shown.showsCycleLength && shown.cycleLengthValid) {
                cycleLengthOf(shown.cycleLength)
            } else {
                existing.cycleLength
            },
    )

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
        return date.takeIf { it >= EARLIEST_BIRTH_DATE && it <= clock.today() }
    }

    private fun heightOf(text: String): Double? = parseDecimal(text)?.takeIf { it in heightRange }

    private fun cycleLengthOf(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in CYCLE_LENGTHS }
}

private fun dateText(day: CalendarDay): String =
    listOf(day.day.toString().padStart(2, '0'), day.month.toString().padStart(2, '0'), day.year)
        .joinToString(".")
