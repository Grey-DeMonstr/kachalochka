package monster.greyde.kachalochka.core.domain.identity

/** Who owns the rows written now; null until the device has signed in. */
interface CurrentUser {
    suspend fun id(): UserId?
}
