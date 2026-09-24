package monster.greyde.kachalochka.core.data.sync

import monster.greyde.kachalochka.core.domain.identity.UserId

/** A pass handles one account at a time, and this is the one it is on. */
class SyncSession {
    var owner: UserId? = null
}
