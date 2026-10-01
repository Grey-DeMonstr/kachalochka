package monster.greyde.kachalochka.ui.machine

import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendMachine
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachinePeaks
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.setValue
import kotlin.time.Duration

/** A machine as every list draws it (frame 7w); a friend's has no [comment] nor [lastUsed]. */
data class MachineCardUi(
    val id: MachineId,
    val name: String,
    val photo: Photo?,
    val tags: List<String>,
    val comment: String,
    val lastUsed: String?,
    val record: String?,
    /** Drawn dimmed, noting that the visit already holds it. */
    val inVisit: Boolean = false,
)

/** One friend's machines, headed by the friend and, without a picture, their calendar colour. */
data class FriendSectionUi(
    val friend: Friend,
    val color: Int?,
    val cards: List<MachineCardUi>,
)

/** Builds [MachineCardUi]s from what a screen read, all in the viewer's unit. */
class MachineCards(
    private val shown: ShownMachines,
    ownPeaks: List<MachinePeaks>,
    friendPeaks: List<MachinePeaks>,
    private val preferred: PreferredWeightUnit,
    private val today: CalendarDay,
    private val offset: Duration,
) {
    private val own = ownPeaks.associateBy { it.machineId }
    private val theirs = friendPeaks.associateBy { it.machineId }

    fun own(machine: Machine): MachineCardUi {
        val peaks = own[machine.id]
        return card(machine, peaks).copy(
            comment = machine.setupNote.trim(),
            lastUsed =
                peaks?.let { dayMonthLabel(CalendarDay.of(it.lastAt, offset), today.year) },
        )
    }

    fun friend(machine: Machine): MachineCardUi = card(machine, theirs[machine.id])

    /** [offered] split by owner, the owners by name, each with the colour [colors] gives them. */
    fun friendSections(
        offered: List<FriendMachine>,
        colors: Map<UserId, Int>,
    ): List<FriendSectionUi> =
        offered
            .groupBy { it.owner.userId }
            .values
            .map { machines ->
                val owner = machines.first().owner
                FriendSectionUi(owner, colors[owner.userId], machines.map { friend(it.machine) })
            }.sortedBy { it.friend.displayName.lowercase() }

    private fun card(
        machine: Machine,
        peaks: MachinePeaks?,
    ) = MachineCardUi(
        id = machine.id,
        name = machine.name,
        photo = shown.cover(machine.id),
        tags = machine.tags.sortedBy { it.lowercase() },
        comment = "",
        lastUsed = null,
        record =
            peaks?.best(machine.weightMode)?.let {
                setValue(it.weight, it.reps, machine, preferred)
            },
    )
}
