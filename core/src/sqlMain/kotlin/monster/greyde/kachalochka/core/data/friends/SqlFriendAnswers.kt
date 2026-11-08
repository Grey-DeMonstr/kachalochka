package monster.greyde.kachalochka.core.data.friends

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.db.KachalochkaDatabase
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

class SqlFriendAnswers(
    private val database: KachalochkaDatabase,
    private val dispatcher: CoroutineDispatcher,
) : FriendAnswers {
    private val queries get() = database.friendAnswerQueries

    override suspend fun answer(
        account: UserId,
        request: String,
    ): String? =
        withContext(dispatcher) { queries.answer(account.value, request).executeAsOneOrNull() }

    override suspend fun keep(
        account: UserId,
        request: String,
        answer: String,
        at: Instant,
    ) = withContext(dispatcher) {
        database.transaction {
            queries.forgetReadBefore((at - KEPT_FOR).toEpochMilliseconds())
            queries.keep(account.value, request, answer, at.toEpochMilliseconds())
        }
    }

    override suspend fun keepOnly(listed: List<UserId>) {
        withContext(dispatcher) { queries.keepOnly(listed.map { it.value }) }
    }
}
