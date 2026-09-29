package monster.greyde.kachalochka.ui.visit

import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.dayVisit
import monster.greyde.kachalochka.core.domain.gym.nextPosition
import monster.greyde.kachalochka.core.domain.gym.recordingInstant
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.time.Clock

/**
 * Writes the sets of one account's [day]: which visit a set lands in, which machine it is stamped
 * with, its position and instant, and whether the rest timer or a sync follows. Today's sets go
 * with the sync worker's next pass; another day's edit is a one-off, so it is pushed at once.
 */
class SetRecorder(
    private val day: CalendarDay,
    private val visits: VisitRepository,
    private val machines: MachineRepository,
    private val sets: WorkoutSetRepository,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val restTimer: RestTimer,
    private val sync: SyncTrigger,
) {
    /** Records a set for [owner] on [machine], which may be another account's (spec §4.1). */
    suspend fun record(
        owner: UserId?,
        machine: Machine,
        values: SetValues,
        comment: String,
    ) {
        val now = clock.now()
        val offset = utcOffset.at(now)
        val today = CalendarDay.of(now, offset)
        val shown = visits.shownOn(owner, day, sets, utcOffset::at)
        val target =
            shown?.visit ?: dayVisit(day, owner, today, offset, now).also { visits.upsert(it) }
        val targetSets = shown?.sets.orEmpty()
        sets.upsert(
            WorkoutSet(
                WorkoutSetId.random(),
                owner,
                target.id,
                ownCopy(owner, machine).id,
                values.weight,
                values.reps,
                nextPosition(targetSets),
                recordingInstant(target, targetSets, today, now),
                now,
                false,
                comment,
            ),
        )
        if (day == today) restTimer.start() else sync.request()
    }

    suspend fun amend(
        set: WorkoutSet,
        values: SetValues,
        comment: String,
    ) {
        sets.upsert(
            set.copy(
                weight = values.weight,
                reps = values.reps,
                comment = comment,
                updatedAt = clock.now(),
            ),
        )
        pushIfPast()
    }

    suspend fun remove(set: WorkoutSet) {
        sets.upsert(set.copy(deleted = true, updatedAt = clock.now()))
        pushIfPast()
    }

    /** Writes the rows a move renumbered. */
    suspend fun reorder(changed: List<WorkoutSet>) {
        changed.forEach { sets.upsert(it) }
        if (changed.isNotEmpty()) pushIfPast()
    }

    /** The owner's own machine standing for [shown]: itself, one of the same name, or a mirror. */
    suspend fun ownCopy(
        owner: UserId?,
        shown: Machine,
    ): Machine {
        if (shown.userId == owner) return shown
        machines.named(owner, shown.name)?.let { return it }
        val mirrored = shown.copy(id = MachineId.random(), userId = owner, updatedAt = clock.now())
        machines.upsert(mirrored)
        return mirrored
    }

    private fun pushIfPast() {
        val now = clock.now()
        if (day != CalendarDay.of(now, utcOffset.at(now))) sync.request()
    }
}
