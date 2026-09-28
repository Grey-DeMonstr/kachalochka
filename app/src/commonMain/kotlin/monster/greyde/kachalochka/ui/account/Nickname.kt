package monster.greyde.kachalochka.ui.account

import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository

/** The name friends and shared visits see: the nickname, else the account's own name. */
class Nickname(
    private val profiles: ProfileRepository,
    private val accounts: Accounts,
) {
    suspend fun of(owner: UserId?): String {
        if (owner == null) return ""
        val nickname =
            profiles
                .forOwner(owner)
                ?.displayName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        if (nickname != null) return nickname
        return accounts.accounts.value
            .firstOrNull { it.userId == owner }
            ?.displayName
            .orEmpty()
    }
}
