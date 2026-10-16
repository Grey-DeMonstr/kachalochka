package monster.greyde.kachalochka.ui.visit

import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineClusters
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.SetValues
import monster.greyde.kachalochka.core.domain.gym.ShownVisit
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.VisitRepository
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetRepository
import monster.greyde.kachalochka.core.domain.gym.dayVisit
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.gym.missingDirectLinks
import monster.greyde.kachalochka.core.domain.gym.nextPosition
import monster.greyde.kachalochka.core.domain.gym.ownStandIn
import monster.greyde.kachalochka.core.domain.gym.recordingInstant
import monster.greyde.kachalochka.core.domain.gym.shownOn
import monster.greyde.kachalochka.core.domain.gym.startedPlanned
import monster.greyde.kachalochka.core.domain.gym.visitMachines
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
    private val links: MachineLinkRepository,
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

    /**
     * The owner's machine standing for [shown] without writing anything: [shown] itself when it
     * is the owner's, else its [ownStandIn] in the cluster the two accounts' own links show.
     */
    suspend fun standIn(
        owner: UserId?,
        shown: Machine,
    ): Machine? {
        if (shown.userId == owner) return shown
        return ownStandIn(shown, machines.all(owner), MachineClusters(knownLinks(owner, shown)))
    }

    /**
     * The owner's own machine standing for [shown], which may be another account's: its
     * [standIn], else a new copy linked to [shown] and to the rest of its cluster (spec §4.5).
     */
    suspend fun ownCopy(
        owner: UserId?,
        shown: Machine,
    ): Machine {
        standIn(owner, shown)?.let { return it }
        val known = knownLinks(owner, shown)
        val now = clock.now()
        val tags = machines.all(owner).flatMap { it.tags }
        val (copy, link) = linkedCopy(shown, owner, now, tags)
        machines.upsert(copy)
        links.upsert(link)
        val cluster = MachineClusters(known).of(shown.id)
        missingDirectLinks(owner, listOf(copy.id), cluster, known + link, now)
            .forEach { links.upsert(it) }
        return copy
    }

    private suspend fun knownLinks(
        owner: UserId?,
        shown: Machine,
    ) = links.all(owner) + links.all(shown.userId)

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
