package monster.greyde.kachalochka.core.domain.gym

/** Machines joined by live links, through any number of hops, are one physical machine. */
class MachineClusters(
    links: List<MachineLink>,
) {
    private val parent = mutableMapOf<MachineId, MachineId>()
    private val members: Map<MachineId, Set<MachineId>>

    init {
        links.filterNot { it.deleted }.forEach { union(it.machineId, it.linkedMachineId) }
        members =
            parent.keys
                .groupBy(::root)
                .values
                .flatMap { cluster ->
                    val set = cluster.toSet()
                    cluster.map { it to set }
                }.toMap()
    }

    fun of(machine: MachineId): Set<MachineId> = members[machine] ?: setOf(machine)

    fun sameMachine(
        a: MachineId,
        b: MachineId,
    ): Boolean = a == b || b in of(a)

    private fun root(machine: MachineId): MachineId {
        var current = machine
        while (true) {
            val up = parent.getValue(current)
            if (up == current) return current
            current = up
        }
    }

    private fun union(
        a: MachineId,
        b: MachineId,
    ) {
        parent.getOrPut(a) { a }
        parent.getOrPut(b) { b }
        val rootA = root(a)
        val rootB = root(b)
        if (rootA != rootB) parent[rootA] = rootB
    }
}
