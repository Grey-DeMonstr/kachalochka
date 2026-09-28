package monster.greyde.kachalochka.ui.friends

/** An invite's code, kept until an active account answers it. */
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
