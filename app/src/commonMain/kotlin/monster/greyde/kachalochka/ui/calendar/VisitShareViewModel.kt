package monster.greyde.kachalochka.ui.calendar

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.catching
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.ui.account.UnsavedChoices
import monster.greyde.kachalochka.ui.account.groupByTagChoice
import monster.greyde.kachalochka.ui.format.DeviceClock
import monster.greyde.kachalochka.ui.share.CardSharing
import monster.greyde.kachalochka.ui.share.TextSharing

/** [card] is the shared card's tag, so the notice shows under that card only. */
data class VisitShareNoticeUi(
    val card: String,
    val text: String,
)

data class VisitShareUi(
    /** The visit the popup offers to share; null while the popup is closed. */
    val offered: VisitCardUi? = null,
    val groupByTag: Boolean = false,
    /** A machine of the offered visit has tags, as the list asks for its own grouping. */
    val canGroupByTag: Boolean = false,
    val canShareImage: Boolean = false,
    /** The card drawn off screen for its picture; null while none is. */
    val picture: VisitCardUi? = null,
    val notice: VisitShareNoticeUi? = null,
)

/** Shares a visit as text or as the picture of its card, wherever the card is shown. */
class VisitShareViewModel(
    private val texts: TextSharing,
    private val cards: CardSharing,
    profiles: ProfileRepository,
    private val currentUser: CurrentUser,
    clock: DeviceClock,
    sync: SyncTrigger,
    unsaved: UnsavedChoices,
    accounts: Accounts,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VisitShareUi(canShareImage = cards.available))
    val state: StateFlow<VisitShareUi> = mutableState

    /** The account's own choice, which the visit list and the statistics share. */
    private val grouping =
        groupByTagChoice(profiles, currentUser, clock, sync, unsaved, viewModelScope)

    init {
        viewModelScope.launch {
            accounts.activeId.collect { mutableState.update { it.copy(notice = null) } }
        }
    }

    fun offer(card: VisitCardUi) {
        mutableState.update { it.copy(offered = card, canGroupByTag = card.tags.isNotEmpty()) }
        viewModelScope.launch {
            grouping.read(currentUser.id())
            mutableState.update { it.copy(groupByTag = grouping.current) }
        }
    }

    fun cancel() = mutableState.update { it.copy(offered = null) }

    fun toggleGroupByTag() {
        grouping.choose(!grouping.current)
        mutableState.update { it.copy(groupByTag = grouping.current) }
    }

    /** Hands the text over in the tap itself, from what the card read ahead. */
    fun shareText() {
        val current = state.value
        val card = current.offered ?: return
        val text = card.shared?.text(current.groupByTag) ?: return
        cancel()
        viewModelScope.launch {
            val said = catching { texts.share(text) }.getOrNull() ?: return@launch
            mutableState.update { it.copy(notice = VisitShareNoticeUi(card.tag, said)) }
        }
    }

    fun shareImage() = mutableState.update { it.copy(offered = null, picture = it.offered) }

    fun pictureDrawn(strips: List<ImageBitmap>) {
        mutableState.update { it.copy(picture = null) }
        viewModelScope.launch { cards.share(listOf(strips)) }
    }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }
}
