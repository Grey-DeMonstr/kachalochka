package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.domain.identity.UserId
import kotlin.time.Instant

@Serializable
private data class StoredAccount(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

@Serializable
private data class StoredAccounts(
    val accounts: List<StoredAccount> = emptyList(),
    val activeId: String? = null,
)

class PersistedAccountStore(
    private val storage: AccountStorage,
) : AccountStore {
    private val json = Json { ignoreUnknownKeys = true }

    // The sync worker writes refreshed tokens back while the UI adds and switches accounts.
    private val lock = Mutex()
    private val sessions: MutableList<AccountSession>
    private val accountState: MutableStateFlow<List<Account>>
    private val activeState: MutableStateFlow<UserId?>

    init {
        val stored = restore()
        sessions = stored.accounts.map(::toSession).toMutableList()
        accountState = MutableStateFlow(sessions.map { it.account })
        activeState =
            MutableStateFlow(
                stored.activeId
                    ?.takeIf { id -> sessions.any { it.account.userId.value == id } }
                    ?.let(::UserId),
            )
    }

    override val accounts: StateFlow<List<Account>> = accountState

    override val activeId: StateFlow<UserId?> = activeState

    override suspend fun add(session: AccountSession) {
        lock.withLock {
            sessions.removeAll { it.account.userId == session.account.userId }
            sessions.add(session)
            activeState.value = session.account.userId
            publish()
        }
    }

    override suspend fun switch(id: UserId) {
        lock.withLock {
            if (sessions.none { it.account.userId == id }) return
            activeState.value = id
            publish()
        }
    }

    override suspend fun remove(id: UserId) {
        lock.withLock {
            sessions.removeAll { it.account.userId == id }
            if (activeState.value == id) {
                activeState.value = sessions.firstOrNull()?.account?.userId
            }
            publish()
        }
    }

    override suspend fun replaceSession(session: AccountSession) {
        lock.withLock {
            val index = sessions.indexOfFirst { it.account.userId == session.account.userId }
            if (index < 0) return
            sessions[index] = session
            publish()
        }
    }

    override suspend fun deactivate() {
        lock.withLock {
            activeState.value = null
            publish()
        }
    }

    override suspend fun sessionOf(id: UserId): AccountSession? =
        lock.withLock { sessions.firstOrNull { it.account.userId == id } }

    // Runs under the caller's lock, which a Mutex would not let it take a second time.
    private suspend fun publish() {
        accountState.value = sessions.map { it.account }
        val stored = StoredAccounts(sessions.map(::toStored), activeState.value?.value)
        storage.write(json.encodeToString(stored))
    }

    // Storage a browser refuses, or a half-written file, must not take the launch with it.
    private fun restore(): StoredAccounts =
        runCatching { storage.read()?.let { json.decodeFromString<StoredAccounts>(it) } }
            .getOrNull() ?: StoredAccounts()

    private fun toSession(stored: StoredAccount) =
        AccountSession(
            Account(UserId(stored.userId), stored.email, stored.displayName),
            stored.accessToken,
            stored.refreshToken,
            Instant.fromEpochMilliseconds(stored.expiresAtMillis),
        )

    private fun toStored(session: AccountSession) =
        StoredAccount(
            session.account.userId.value,
            session.account.email,
            session.account.displayName,
            session.accessToken,
            session.refreshToken,
            session.expiresAt.toEpochMilliseconds(),
        )
}
