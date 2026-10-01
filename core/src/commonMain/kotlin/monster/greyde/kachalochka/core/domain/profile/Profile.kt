package monster.greyde.kachalochka.core.domain.profile

import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineSort
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

enum class Sex { Male, Female }

/** The unit gym weights are shown in; [Mixed] keeps each machine's own. */
enum class PreferredWeightUnit { Kg, Lb, Mixed }

data class Profile(
    val id: ProfileId,
    val userId: UserId?,
    val displayName: String?,
    val updatedAt: Instant,
    val deleted: Boolean,
    val friendColors: Map<UserId, Int> = emptyMap(),
    val sex: Sex? = null,
    val birthDate: CalendarDay? = null,
    val heightCm: Double? = null,
    val weightUnit: PreferredWeightUnit = PreferredWeightUnit.Kg,
    val groupByTag: Boolean = false,
    /** The photo chosen to stand for the owner instead of the Google picture. */
    val avatarPhoto: PhotoId? = null,
    val machineSort: MachineSort = MachineSort.Recent,
) {
    companion object {
        /** A signed-in owner's profile id is the owner's own, so devices converge on one row. */
        fun new(
            owner: UserId?,
            now: Instant,
        ): Profile =
            Profile(
                id = owner?.let { ProfileId(it.value) } ?: ProfileId.random(),
                userId = owner,
                displayName = null,
                updatedAt = now,
                deleted = false,
            )
    }
}

/** Whole years from [birth] to [day]. */
fun ageOn(
    birth: CalendarDay,
    day: CalendarDay,
): Int {
    val beforeBirthday =
        day.month < birth.month || (day.month == birth.month && day.day < birth.day)
    return day.year - birth.year - if (beforeBirthday) 1 else 0
}
