package monster.greyde.kachalochka.core.data.identity

import kotlinx.browser.localStorage

class LocalStorageAccountStorage : AccountStorage {
    private val key = "accounts"

    // A browser that refuses site storage throws on every access, so the accounts live as long
    // as the tab and no longer.
    override fun read(): String? = runCatching { localStorage.getItem(key) }.getOrNull()

    override suspend fun write(value: String) {
        runCatching { localStorage.setItem(key, value) }
    }
}
