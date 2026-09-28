package monster.greyde.kachalochka.ui.friends

/** Holds an invite's code across the Google round trip, which returns without the query. */
interface JoinCodeStore {
    fun code(): String?

    fun save(code: String)

    fun clear()
}

class InMemoryJoinCodeStore : JoinCodeStore {
    private var stored: String? = null

    override fun code(): String? = stored

    override fun save(code: String) {
        stored = code
    }

    override fun clear() {
        stored = null
    }
}
