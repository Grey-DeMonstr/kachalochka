package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.domain.gym.Machine

class InMemoryTagRowsTest : TagRowStoreContract() {
    private val rows = InMemoryTagRows()

    override val store: TagRowStore = rows

    override fun putMachine(machine: Machine) {
        rows.machines[machine.id] = machine
    }

    override fun putLink(link: MachineTag) {
        rows.links[link.id] = link
    }

    override fun tag(id: String): Tag? = rows.tags[id]

    override fun link(id: String): MachineTag? = rows.links[id]
}
