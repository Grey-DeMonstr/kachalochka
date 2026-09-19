package monster.greyde.kachalochka.core.data.identity

import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId

/** Rows stay unowned until sign-in stamps them (technical spec §4.3). */
object LocalCurrentUser : CurrentUser {
    override suspend fun id(): UserId? = null
}
