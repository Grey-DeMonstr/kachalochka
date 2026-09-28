package monster.greyde.kachalochka.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.parseDecimal
import kotlin.time.Clock

const val NICKNAME_LENGTH = 40

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

class SettingsViewModel(
    private val profiles: ProfileRepository,
    private val accounts: Accounts,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableProfile = MutableStateFlow<ProfileUi?>(null)
    val profile: StateFlow<ProfileUi?> = mutableProfile

    private var saved: ProfileUi? = null

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    private suspend fun load() {
        val owner = currentUser.id()
        val stored = profiles.forOwner(owner)
        val placeholder =
            accounts.accounts.value
                .firstOrNull { it.userId == owner }
                ?.displayName
                .orEmpty()
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
    }

    fun type(text: String) =
        edit { shown -> shown.copy(nickname = shown.nickname?.let { text.take(NICKNAME_LENGTH) }) }

    fun chooseSex(sex: Sex) = edit { it.copy(sex = sex) }

    fun chooseWeightUnit(unit: PreferredWeightUnit) = edit { it.copy(weightUnit = unit) }

    fun typeBirthDate(text: String) = edit { it.copy(birthDate = text) }

    fun typeHeight(text: String) = edit { it.copy(height = text) }

    private fun edit(change: (ProfileUi) -> ProfileUi) {
        val edited = change(mutableProfile.value ?: return)
        val checked =
            edited.copy(
                birthDateValid =
                    edited.birthDate.isBlank() || birthDateOf(edited.birthDate) != null,
                heightValid = edited.height.isBlank() || heightOf(edited.height) != null,
            )
        mutableProfile.value =
            checked.copy(
                canSave =
                    checked.birthDateValid &&
                        checked.heightValid &&
                        checked.copy(canSave = false) != saved,
            )
    }

    fun save() {
        val shown = mutableProfile.value?.takeIf { it.canSave } ?: return
        viewModelScope.launch {
            val owner = currentUser.id()
            val now = clock.now()
            // Read afresh so a friend colour saved meanwhile survives.
            val existing = profiles.forOwner(owner) ?: Profile.new(owner, now)
            profiles.upsert(
                existing.copy(
                    displayName = shown.nickname ?: existing.displayName,
                    sex = shown.sex,
                    birthDate = birthDateOf(shown.birthDate),
                    heightCm = heightOf(shown.height),
                    weightUnit = shown.weightUnit,
                    updatedAt = now,
                ),
            )
            val settled = shown.copy(canSave = false)
            saved = settled
            mutableProfile.value = settled
            sync.request()
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
