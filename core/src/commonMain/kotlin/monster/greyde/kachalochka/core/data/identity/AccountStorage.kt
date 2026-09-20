package monster.greyde.kachalochka.core.data.identity

/**
 * Reading is blocking so the avatar has its accounts on the first frame, as the theme mode does
 * (technical spec §10).
 */
interface AccountStorage {
    fun read(): String?

    suspend fun write(value: String)
}
