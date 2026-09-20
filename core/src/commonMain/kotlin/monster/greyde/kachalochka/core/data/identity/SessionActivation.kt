package monster.greyde.kachalochka.core.data.identity

/** One session is live at a time, even though several are stored. */
interface SessionActivation {
    suspend fun activate(session: AccountSession)

    suspend fun clear()
}
