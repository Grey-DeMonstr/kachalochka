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
import monster.greyde.kachalochka.core.domain.gym.visitMachines
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.machine.MachineCatalogue
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
    /**
     * Records a set for [owner] on [machine], the owner's own (see [MachineCatalogue.ownCopy]).
     */
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
                machine.id,
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

    /**
     * Adds [planMachines] to the day's visit as planned, creating the visit when there is none
     * and something is added.
     */
    suspend fun plan(
        owner: UserId?,
        planMachines: List<MachineId>,
    ) {
        val shown = visits.shownOn(owner, day, sets, utcOffset::at)
        val live =
            machines
                .all(owner)
                .map { it.id }
                .toSet()
        val planned =
            startedPlanned(
                shown?.visit?.planned.orEmpty(),
                shown?.sets.orEmpty().map { it.machineId },
                planMachines,
                live,
            )
        if (planned == shown?.visit?.planned.orEmpty()) return
        val visit = (shown ?: newVisit(owner)).visit
        visits.upsert(visit.copy(planned = planned, updatedAt = clock.now()))
        pushIfPast()
    }

    /** Plans the machines of [owner]'s visit on [from], in its order, as [plan] does. */
    suspend fun planFrom(
        owner: UserId?,
        from: CalendarDay,
    ) {
        val source = visits.shownOn(owner, from, sets, utcOffset::at) ?: return
        plan(owner, visitMachines(source.sets, source.visit.planned))
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

    private suspend fun shownOrNew(owner: UserId?): ShownVisit {
        visits.shownOn(owner, day, sets, utcOffset::at)?.let { return it }
        val created = newVisit(owner)
        visits.upsert(created.visit)
        return created
    }

    private fun newVisit(owner: UserId?): ShownVisit {
        val now = clock.now()
        val offset = utcOffset.at(now)
        return ShownVisit(
            dayVisit(day, owner, CalendarDay.of(now, offset), offset, now),
            emptyList(),
        )
    }

    private fun pushIfPast() {
        val now = clock.now()
        if (day != CalendarDay.of(now, utcOffset.at(now))) sync.request()
    }
}
