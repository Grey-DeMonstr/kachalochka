package monster.greyde.kachalochka.core.data.gym

import monster.greyde.kachalochka.core.data.db.inMemoryDatabase
import monster.greyde.kachalochka.core.domain.gym.Machine

class SqlTagRowsTest : TagRowStoreContract() {
    private val database = inMemoryDatabase()

    override val store: TagRowStore = SqlTagRows(database)

    override fun putMachine(machine: Machine) {
        database.machineQueries.write(machine)
    }

    override fun putLink(link: MachineTag) {
        database.machineTagQueries.write(link)
    }

    override fun tag(id: String): Tag? = database.tagQueries.byId(id, ::tagOf).executeAsOneOrNull()

    override fun link(id: String): MachineTag? =
        database.machineTagQueries.byId(id, ::machineTagOf).executeAsOneOrNull()
}
