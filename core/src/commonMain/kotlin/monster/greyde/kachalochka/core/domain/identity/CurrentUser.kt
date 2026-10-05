package monster.greyde.kachalochka.core.domain.identity

/** Who owns the rows written now; null until the device has signed in. */
interface CurrentUser {
    suspend fun id(): UserId?

    /** False while a managed child is active: its profile and measures are the child's alone. */
    suspend fun writesPrivateRows(): Boolean
}
