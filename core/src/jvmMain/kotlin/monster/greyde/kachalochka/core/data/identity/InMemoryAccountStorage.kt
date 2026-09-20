package monster.greyde.kachalochka.core.data.identity

class InMemoryAccountStorage : AccountStorage {
    private var value: String? = null

    override fun read(): String? = value

    override suspend fun write(value: String) {
        this.value = value
    }
}
