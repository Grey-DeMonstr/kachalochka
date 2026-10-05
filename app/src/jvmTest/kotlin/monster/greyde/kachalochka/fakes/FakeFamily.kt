package monster.greyde.kachalochka.fakes

import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.domain.family.Acceptance
import monster.greyde.kachalochka.core.domain.family.Family
import monster.greyde.kachalochka.core.domain.family.FamilyMember
import monster.greyde.kachalochka.core.domain.family.FamilyRepository
import monster.greyde.kachalochka.core.domain.identity.UserId

/** Guardian links as the server keeps them, acting as the active account; fails [offline]. */
class FakeFamily(
    private val active: () -> Account?,
) : FamilyRepository {
    val people = linkedMapOf<UserId, FamilyMember>()

    /** Each link as (child, guardian). */
    val links = mutableListOf<Pair<UserId, UserId>>()

    /** Live codes and the guardian each belongs to. */
    val codes = linkedMapOf<String, UserId>()
    var nextCode = "PAPA2345"
    var offline = false

    fun link(
        child: FamilyMember,
        guardian: FamilyMember,
    ) {
        people[child.userId] = child
        people[guardian.userId] = guardian
        links += child.userId to guardian.userId
    }

    /** [guardian]'s live code, as `offer_guardianship` leaves it. */
    fun offered(
        code: String,
        guardian: FamilyMember,
    ) {
        people[guardian.userId] = guardian
        codes[code] = guardian.userId
    }

    fun linksOf(owner: UserId): Family =
        Family(
            links
                .filter { it.second == owner }
                .map { people.getValue(it.first) }
                .sortedBy { it.displayName.lowercase() },
            links
                .filter { it.first == owner }
                .map { people.getValue(it.second) }
                .sortedBy { it.displayName.lowercase() },
        )

    private fun acting(): Account = checkNotNull(active()) { "nobody is signed in" }

    private fun <T> online(read: () -> T): T {
        if (offline) error("no connection")
        return read()
    }

    override suspend fun family(): Family = online { linksOf(acting().userId) }

    override suspend fun offer(): String =
        online {
            val mine = acting().userId
            codes.values.removeAll { it == mine }
            codes[nextCode] = mine
            nextCode
        }

    override suspend fun accept(code: String): Acceptance =
        online {
            val typed = code.trim().uppercase()
            val me = acting()
            when (val guardian = codes[typed]) {
                null -> Acceptance.UnknownCode
                me.userId -> Acceptance.OwnCode
                else -> {
                    codes.remove(typed)
                    people.getOrPut(me.userId) { FamilyMember(me.userId, me.displayName) }
                    if ((me.userId to guardian) !in links) links += me.userId to guardian
                    Acceptance.Linked(guardian)
                }
            }
        }

    override suspend fun end(
        child: UserId,
        guardian: UserId,
    ) = online {
        if (acting().userId == child || acting().userId == guardian) {
            links.remove(child to guardian)
        }
        Unit
    }
}
