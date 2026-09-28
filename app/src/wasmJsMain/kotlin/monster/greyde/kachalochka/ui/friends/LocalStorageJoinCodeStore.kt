package monster.greyde.kachalochka.ui.friends

import kotlinx.browser.localStorage
import monster.greyde.kachalochka.core.domain.friends.inviteCodeOf

/** A browser that refuses site storage throws on every access; the invite is then lost. */
class LocalStorageJoinCodeStore : JoinCodeStore {
    private val key = "kachalochka.joinCode"

    override fun code(): String? =
        runCatching { localStorage.getItem(key) }.getOrNull()?.let(::inviteCodeOf)

    override fun save(code: String) {
        runCatching { localStorage.setItem(key, code) }
    }

    override fun clear() {
        runCatching { localStorage.removeItem(key) }
    }
}
