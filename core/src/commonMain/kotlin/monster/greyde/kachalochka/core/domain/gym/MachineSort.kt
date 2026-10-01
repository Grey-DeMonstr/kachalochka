package monster.greyde.kachalochka.core.domain.gym

/** How the machine lists are ordered; the account keeps its choice in its profile. */
enum class MachineSort { Recent, Name, Frequent }

/** Unused machines follow the used ones; ties fall back to recency, then to the name. */
fun machineOrder(
    sort: MachineSort,
    peaks: Map<MachineId, MachinePeaks>,
): Comparator<Machine> {
    val byName = compareBy<Machine> { it.name.lowercase() }
    val byRecency = compareByDescending<Machine> { peaks[it.id]?.lastAt }.then(byName)
    val byVisits = compareByDescending<Machine> { peaks[it.id]?.visits }
    return when (sort) {
        MachineSort.Recent -> byRecency
        MachineSort.Name -> byName
        MachineSort.Frequent -> byVisits.then(byRecency)
    }
}
