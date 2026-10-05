package monster.greyde.kachalochka.core.data.family

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountKind
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.identity.OwnedRowsPurge
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.identity.UserId

/**
 * Keeps the device's managed accounts in step with the guardian links of its Google accounts.
 * A family that cannot be read keeps its children; a child no family names any more leaves the
 * device with its rows, which the device could never sync again.
 */
class FamilyFollower(
    private val store: AccountStore,
    private val reads: FamilyReads,
    private val purge: OwnedRowsPurge,
) {
    private val lock = Mutex()

    /** True when a child joined, left or changed guardian. */
    suspend fun follow(): Boolean =
        lock.withLock {
            val listed = store.accounts.value
            val signedIn = listed.filterNot { it.isManaged }
            val signedInIds = signedIn.map { it.userId }.toSet()
            val before = listed.filter { it.isManaged }
            val children = signedIn.associate { it.userId to childrenOf(it.userId) }
            val after = linkedMapOf<UserId, Account>()
            for (guardian in signedIn) {
                children[guardian.userId]?.forEach { child ->
                    if (child.userId !in signedInIds && child.userId !in after) {
                        after[child.userId] = managed(child, guardian.userId)
                    }
                }
            }
            before
                .filter { it.userId !in after && it.guardianId?.let(children::get) == null }
                .forEach { after[it.userId] = it }
            store.setManaged(after.values.toList())
            before
                .map { it.userId }
                .filter { it !in after && it !in signedInIds }
                .forEach { purge.purge(it) }
            before.map { it.userId to it.guardianId }.toSet() !=
                after.values.map { it.userId to it.guardianId }.toSet()
        }

    // Null, unlike an empty list, says nothing about the family.
    private suspend fun childrenOf(owner: UserId): List<FamilyMember>? =
        try {
            reads.familyOf(owner).children
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: Exception) {
            null
        }

    private fun managed(
        child: FamilyMember,
        guardian: UserId,
    ) = Account(
        child.userId,
        "",
        child.displayName,
        child.avatar.picture,
        AccountKind.Managed(guardian, child.avatar.photo),
    )
}
