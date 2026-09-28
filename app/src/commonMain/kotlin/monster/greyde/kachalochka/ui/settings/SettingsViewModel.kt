package monster.greyde.kachalochka.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import kotlin.time.Clock

const val NICKNAME_LENGTH = 40

data class NicknameUi(
    val text: String,
    val placeholder: String,
    val canSave: Boolean,
)

class SettingsViewModel(
    private val profiles: ProfileRepository,
    private val accounts: Accounts,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableNickname = MutableStateFlow<NicknameUi?>(null)
    val nickname: StateFlow<NicknameUi?> = mutableNickname

    private var saved = ""

    /** The screen follows whoever is active, wherever the switch came from. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    private suspend fun load() {
        val owner = currentUser.id()
        if (owner == null) {
            mutableNickname.value = null
            return
        }
        val placeholder =
            accounts.accounts.value
                .firstOrNull { it.userId == owner }
                ?.displayName
                .orEmpty()
        saved = profiles.forOwner(owner)?.displayName.orEmpty()
        mutableNickname.value =
            NicknameUi(text = saved, placeholder = placeholder, canSave = false)
    }

    fun type(text: String) {
        val current = mutableNickname.value ?: return
        val typed = text.take(NICKNAME_LENGTH)
        mutableNickname.value = current.copy(text = typed, canSave = typed != saved)
    }

    fun save() {
        val current = mutableNickname.value ?: return
        viewModelScope.launch {
            val owner = currentUser.id() ?: return@launch
            val now = clock.now()
            val existing = profiles.forOwner(owner)
            val profile =
                existing?.copy(displayName = current.text, updatedAt = now)
                    ?: Profile.new(owner, now).copy(displayName = current.text)
            profiles.upsert(profile)
            saved = current.text
            mutableNickname.value = current.copy(canSave = false)
            sync.request()
        }
    }
}
