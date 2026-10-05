package monster.greyde.kachalochka.core.data.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import monster.greyde.kachalochka.core.domain.gym.PhotoId
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
    // Accounts stored before pictures have none until their next refresh.
    val pictureUrl: String? = null,
)

@Serializable
private data class StoredManaged(
    val userId: String,
    val displayName: String,
    val guardianId: String,
    val pictureUrl: String? = null,
    val avatarPhoto: String? = null,
)

@Serializable
private data class StoredAccounts(
    val accounts: List<StoredAccount> = emptyList(),
    val activeId: String? = null,
    // A store written without managed entries reads as none.
    val managed: List<StoredManaged> = emptyList(),
)

class PersistedAccountStore(
    private val storage: AccountStorage,
    private val watermarks: WatermarkReset = NoWatermarkReset,
) : AccountStore {
    private val json = Json { ignoreUnknownKeys = true }

    // The sync worker writes refreshed tokens back while the UI adds and switches accounts.
    private val lock = Mutex()
    private val sessions: MutableList<AccountSession>
    private val managed: MutableList<Account>
    private val accountState: MutableStateFlow<List<Account>>
    private val activeState: MutableStateFlow<UserId?>

    init {
        val stored = restore()
        sessions = stored.accounts.map(::toSession).toMutableList()
        managed = guarded(stored.managed.map(::toManaged)).toMutableList()
        accountState = MutableStateFlow(listed())
        val restoredActive = listed().map { it.userId }.firstOrNull { it.value == stored.activeId }
        activeState = MutableStateFlow(restoredActive)
    }

    override val accounts: StateFlow<List<Account>> = accountState

    override val activeId: StateFlow<UserId?> = activeState

    override suspend fun add(session: AccountSession) {
        lock.withLock {
            val id = session.account.userId
            sessions.removeAll { it.account.userId == id }
            sessions.add(session)
            managed.removeAll { it.userId == id }
            activeState.value = id
            publish()
        }
    }

    override suspend fun switch(id: UserId) {
        lock.withLock {
            if (!isListed(id)) return
            activeState.value = id
            publish()
        }
    }

    /** A child leaving with its guardian keeps its rows; signed in itself, it pulls in full. */
    override suspend fun remove(id: UserId) {
        val children =
            lock.withLock {
                sessions.removeAll { it.account.userId == id }
                val children = managed.filter { it.guardianId == id }.map { it.userId }
                managed.removeAll { it.userId == id || it.guardianId == id }
                val active = activeState.value
                if (active != null && !isListed(active)) {
                    activeState.value = sessions.firstOrNull()?.account?.userId
                }
                publish()
                children
            }
        children.forEach { watermarks.forget(it) }
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

    override suspend fun actingSessionOf(id: UserId): AccountSession? =
        lock.withLock {
            val acting = managed.firstOrNull { it.userId == id }?.guardianId ?: id
            sessions.firstOrNull { it.account.userId == acting }
        }

    override suspend fun setManaged(children: List<Account>) {
        lock.withLock {
            val active = activeState.value
            val actingForActive = managed.firstOrNull { it.userId == active }?.guardianId
            managed.clear()
            managed.addAll(guarded(children))
            if (active != null && !isListed(active)) {
                activeState.value = actingForActive ?: sessions.firstOrNull()?.account?.userId
            }
            publish()
        }
    }

    private fun listed(): List<Account> = sessions.map { it.account } + managed

    private fun isListed(id: UserId): Boolean =
        sessions.any { it.account.userId == id } || managed.any { it.userId == id }

    // A managed account acts through a stored session and never stands in for one.
    private fun guarded(children: List<Account>): List<Account> =
        children
            .filter { child ->
                val guardian = child.guardianId
                guardian != null &&
                    sessions.any { it.account.userId == guardian } &&
                    sessions.none { it.account.userId == child.userId }
            }.distinctBy { it.userId }

    // Runs under the caller's lock, which a Mutex would not let it take a second time.
    private suspend fun publish() {
        accountState.value = listed()
        val stored =
            StoredAccounts(
                sessions.map(::toStored),
                activeState.value?.value,
                managed.map(::toStoredManaged),
            )
        storage.write(json.encodeToString(stored))
    }

    // Storage a browser refuses, or a half-written file, must not take the launch with it.
    private fun restore(): StoredAccounts =
        runCatching { storage.read()?.let { json.decodeFromString<StoredAccounts>(it) } }
            .getOrNull() ?: StoredAccounts()

    private fun toSession(stored: StoredAccount) =
        AccountSession(
            Account(UserId(stored.userId), stored.email, stored.displayName, stored.pictureUrl),
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
            session.account.pictureUrl,
        )

    private fun toManaged(stored: StoredManaged) =
        Account(
            UserId(stored.userId),
            "",
            stored.displayName,
            stored.pictureUrl,
            AccountKind.Managed(UserId(stored.guardianId), stored.avatarPhoto?.let(::PhotoId)),
        )

    private fun toStoredManaged(account: Account) =
        StoredManaged(
            account.userId.value,
            account.displayName,
            checkNotNull(account.guardianId).value,
            account.pictureUrl,
            (account.kind as? AccountKind.Managed)?.avatarPhoto?.value,
        )
}
