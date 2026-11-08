package monster.greyde.kachalochka.fakes

import monster.greyde.kachalochka.core.data.friends.FriendAnswers
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

class InMemoryFriendAnswers : FriendAnswers {
    private val kept = mutableMapOf<Pair<UserId, String>, String>()

    override suspend fun answer(
        account: UserId,
        request: String,
    ): String? = kept[account to request]

    override suspend fun keep(
        account: UserId,
        request: String,
        answer: String,
        at: Instant,
    ) {
        kept[account to request] = answer
    }

    override suspend fun keepOnly(listed: List<UserId>) {
        kept.keys.retainAll { it.first in listed }
    }
}
