package monster.greyde.kachalochka.core.data.family

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountKind
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.LiveSession
import monster.greyde.kachalochka.core.data.identity.OwnedRowsPurge
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * Keeps the device's managed accounts in step with the guardian links of its Google accounts.
 * A family that cannot be read may still name any child, so while one is unread no child leaves
 * and none changes guardian away from it. Otherwise a child no family names any more leaves the
 * device with its rows, which the device could never sync again.
 */
class FamilyFollower(
    private val store: AccountStore,
    private val reads: FamilyReads,
    private val live: LiveSession,
    private val purge: OwnedRowsPurge,
) {
    private val lock = Mutex()

    /** True when a child or a trainee joined, left, or changed guardian or kind. */
    suspend fun follow(): Boolean = lock.withLock { followLocked() }

    /**
     * Follows, then runs [pass] before any other follow starts: one meanwhile could purge a child
     * whose rows [pass] then pulls back.
     */
    suspend fun <T> followThen(pass: suspend () -> T): T =
        lock.withLock {
            followLocked()
            pass()
        }

    private suspend fun followLocked(): Boolean {
        val listed = store.accounts.value
        val signedIn = listed.filterNot { it.isManaged }
        val signedInIds = signedIn.map { it.userId }.toSet()
        val before = listed.filter { it.isManaged }
        val families = signedIn.associate { it.userId to familyOf(it.userId) }
        val after = linkedMapOf<UserId, Account>()
        before
            .filter { it.guardianId?.let(families::get) == null }
            .forEach { after[it.userId] = it }
        // A child before a trainee: the same person as both acts with a child's rights.
        for (trainee in listOf(false, true)) {
            for (guardian in signedIn) {
                val family = families[guardian.userId] ?: continue
                for (member in if (trainee) family.trainees else family.children) {
                    if (member.userId !in signedInIds && member.userId !in after) {
                        after[member.userId] = managed(member, guardian.userId, trainee)
                    }
                }
            }
        }
        if (families.values.any { it == null }) {
            before.filter { it.userId !in after }.forEach { after[it.userId] = it }
        }
        // A child and a trainee sync different tables, so one who changes kind starts afresh.
        val rekinded =
            before.filter { old ->
                after[old.userId]?.let { it.isTrainee != old.isTrainee } == true
            }
        // A cancellation between a purge and the store would leave rows no account lists.
        return withContext(NonCancellable) {
            leave(before.filter { it.userId !in after }, signedInIds, after)
            rekinded.forEach { if (!purged(it.userId)) after[it.userId] = it }
            store.setManaged(after.values.toList())
            keepActingSessionLive()
            before.map(::linkOf).toSet() != after.values.map(::linkOf).toSet()
        }
    }

    /**
     * Purges each of [gone] while it is still listed, so a failed purge leaves it for the next
     * follow. An account signed in after the families of [read] were read has a family nobody
     * read, and keeps every child not yet purged.
     */
    private suspend fun leave(
        gone: List<Account>,
        read: Set<UserId>,
        after: MutableMap<UserId, Account>,
    ) {
        for (child in gone) {
            val signedInMeanwhile =
                store.accounts.value.any { !it.isManaged && it.userId !in read }
            if (signedInMeanwhile || !purged(child.userId)) after[child.userId] = child
        }
    }

    private suspend fun purged(owner: UserId): Boolean = catching { purge.purge(owner) }.isSuccess

    // A child acts through its guardian's session; one that failed to go live is retried here.
    private suspend fun keepActingSessionLive() {
        val active = store.activeId.value ?: return
        if (store.accounts.value.none { it.userId == active && it.isManaged }) return
        if (!putLive(active)) return
        // A switch made while this activation ran went live first, and this one replaced it.
        store.activeId.value
            ?.takeIf { it != active }
            ?.let { putLive(it) }
    }

    /** False when [id]'s acting session was live already or did not go live. */
    private suspend fun putLive(id: UserId): Boolean {
        val session = store.actingSessionOf(id) ?: return false
        if (live.liveId == session.account.userId) return false
        // The next follow retries; a refusal here must not keep the sync pass from running.
        return catching { live.activate(session) }.isSuccess
    }

    private fun linkOf(account: Account) =
        Triple(account.userId, account.guardianId, account.isTrainee)

    // Null, unlike an empty family, says nothing about the family.
    private suspend fun familyOf(owner: UserId): Family? =
        catching { reads.familyOf(owner) }.getOrNull()

    private fun managed(
        member: FamilyMember,
        guardian: UserId,
        trainee: Boolean,
    ) = Account(
        member.userId,
        "",
        member.displayName,
        member.avatar.picture,
        AccountKind.Managed(guardian, member.avatar.photo, trainee),
    )
}
