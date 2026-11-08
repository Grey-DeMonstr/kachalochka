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
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.ui.share.CardSharing
import monster.greyde.kachalochka.ui.share.ShareGroupingPreference
import monster.greyde.kachalochka.ui.share.TextSharing

/** [day] is the shared visit's, so the notice shows under that visit only. */
data class VisitShareNoticeUi(
    val day: CalendarDay,
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

/** Shares an own visit as text or as the picture of its card, wherever the visit is shown. */
class VisitShareViewModel(
    private val texts: TextSharing,
    private val cards: CardSharing,
    private val grouping: ShareGroupingPreference,
    accounts: Accounts,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VisitShareUi(canShareImage = cards.available))
    val state: StateFlow<VisitShareUi> = mutableState

    init {
        viewModelScope.launch {
            accounts.activeId.collect { mutableState.update { it.copy(notice = null) } }
        }
        viewModelScope.launch {
            grouping.groupByTag.collect { grouped ->
                mutableState.update { it.copy(groupByTag = grouped) }
            }
        }
    }

    fun offer(card: VisitCardUi) =
        mutableState.update { it.copy(offered = card, canGroupByTag = card.tags.isNotEmpty()) }

    fun cancel() = mutableState.update { it.copy(offered = null) }

    fun toggleGroupByTag() {
        val grouped = !state.value.groupByTag
        viewModelScope.launch { grouping.set(grouped) }
    }

    /** Hands the text over in the tap itself, from what the card read ahead. */
    fun shareText() {
        val current = state.value
        val card = current.offered ?: return
        val text = card.own?.shared?.text(current.groupByTag) ?: return
        cancel()
        viewModelScope.launch {
            val said = catching { texts.share(text) }.getOrNull() ?: return@launch
            mutableState.update { it.copy(notice = VisitShareNoticeUi(card.day, said)) }
        }
    }

    fun shareImage() = mutableState.update { it.copy(offered = null, picture = it.offered) }

    fun pictureDrawn(picture: ImageBitmap) {
        mutableState.update { it.copy(picture = null) }
        viewModelScope.launch { cards.share(picture) }
    }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }
}
