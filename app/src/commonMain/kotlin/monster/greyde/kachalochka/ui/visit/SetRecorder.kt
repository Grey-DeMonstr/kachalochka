package monster.greyde.kachalochka.ui.visit

import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.ShownVisit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.dayVisit
import monster.greyde.kachalochka.core.domain.gym.nextPosition
import monster.greyde.kachalochka.core.domain.gym.recordingInstant
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.startedPlanned
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.timer.RestTimer
import kotlin.time.Clock

/**
 * Writes one account's [day]: the sets, which visit they land in, the visit's planned machines,
 * and whether the rest timer or a sync follows. Today's sets go with the sync worker's next
 * pass; another day's edit is a one-off, so it is pushed at once.
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
        val today = CalendarDay.of(now, utcOffset.at(now))
        val (target, targetSets) = shownOrNew(owner)
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

    /** Adds [machines] to the day's visit as planned, creating the visit when there is none. */
    suspend fun plan(
        owner: UserId?,
        machines: List<MachineId>,
    ) {
        val (visit, visitSets) = shownOrNew(owner)
        val live =
            this.machines
                .all(owner)
                .map { it.id }
                .toSet()
        val planned =
            startedPlanned(visit.planned, visitSets.map { it.machineId }, machines, live)
        if (planned != visit.planned) {
            visits.upsert(visit.copy(planned = planned, updatedAt = clock.now()))
        }
        pushIfPast()
    }

    suspend fun unplan(
        visit: VisitId,
        machine: MachineId,
    ) {
        val stored = visits.byId(visit) ?: return
        visits.upsert(stored.copy(planned = stored.planned - machine, updatedAt = clock.now()))
        pushIfPast()
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

    private suspend fun shownOrNew(owner: UserId?): ShownVisit {
        visits.shownOn(owner, day, sets, utcOffset::at)?.let { return it }
        val now = clock.now()
        val offset = utcOffset.at(now)
        val created = dayVisit(day, owner, CalendarDay.of(now, offset), offset, now)
        visits.upsert(created)
        return ShownVisit(created, emptyList())
    }

    private fun pushIfPast() {
        val now = clock.now()
        if (day != CalendarDay.of(now, utcOffset.at(now))) sync.request()
    }
}
