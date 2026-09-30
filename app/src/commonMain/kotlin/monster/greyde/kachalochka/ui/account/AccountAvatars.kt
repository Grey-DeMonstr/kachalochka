package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository

/**
 * The photo each signed-in account chose, from its profile. One per process, so every top bar
 * shows the same; a profile another account cannot read keeps what was read before.
 */
class AccountAvatars(
    private val accounts: Accounts,
    private val profiles: ProfileRepository,
) {
    private val chosen = MutableStateFlow<Map<UserId, PhotoId>>(emptyMap())
    val photos: StateFlow<Map<UserId, PhotoId>> = chosen

    suspend fun refresh() {
        val before = chosen.value
        chosen.value =
            accounts.accounts.value
                .mapNotNull { account ->
                    val id = account.userId
                    val photo =
                        runCatching { profiles.forOwner(id)?.avatarPhoto }.getOrElse { before[id] }
                    photo?.let { id to it }
                }.toMap()
    }
}
