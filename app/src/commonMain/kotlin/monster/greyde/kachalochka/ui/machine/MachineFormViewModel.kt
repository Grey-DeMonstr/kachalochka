package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.roundWeight
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.friends.reading
import kotlin.time.Clock

data class MachineFormArgs(
    val machineId: MachineId?,
    val copyOf: MachineId?,
    val name: String,
)

data class UnlinkUi(
    val available: Boolean,
    val confirming: Boolean,
)

data class MachineFormState(
    val name: String = "",
    val setupNote: String = "",
    val weightMode: WeightMode = WeightMode.Total,
    val platformWeight: String = "0",
    val platformIncluded: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val unitLabel: String = "",
    val weightStep: String = "2,5",
) {
    val platformWeightValue: Double?
        get() =
            if (platformWeight.isBlank()) 0.0 else parseDecimal(platformWeight)?.takeIf { it >= 0 }

    val weightStepValue: Double?
        get() = parseDecimal(weightStep)?.let(::roundWeight)?.takeIf { it > 0 }

    val canSave: Boolean
        get() =
            name.isNotBlank() &&
                platformWeightValue != null &&
                weightStepValue != null &&
                (unit != WeightUnit.Custom || unitLabel.isNotBlank())

    companion object {
        const val UNIT_LABEL_LENGTH: Int = 12

        fun of(
            machine: Machine,
            name: String = machine.name,
        ) = MachineFormState(
            name = name,
            setupNote = machine.setupNote,
            weightMode = machine.weightMode,
            platformWeight = formatNumber(machine.platformWeight),
            platformIncluded = machine.platformIncluded,
            unit = machine.unit,
            unitLabel = machine.unitLabel,
            weightStep = formatNumber(machine.weightStep),
        )
    }
}

class MachineFormViewModel(
    private val args: MachineFormArgs,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val friends: FriendsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineFormState(name = args.name))
    val state: StateFlow<MachineFormState> = mutableState
    private val mutableUnlink = MutableStateFlow(UnlinkUi(available = false, confirming = false))
    val unlink: StateFlow<UnlinkUi> = mutableUnlink
    private val writes = WriteGuard(viewModelScope)

    private var existing: Machine? = null
    private var loaded = false

    /** A switch keeps the typed edits and drops the row under them, which it no longer owns. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect { active ->
                existing = existing?.takeIf { it.userId == active }
                refreshUnlink()
            }
        }
    }

    // The screen asks again whenever it re-enters composition; the form keeps its edits then.
    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val current = args.machineId?.let { machines.byId(it) }
            existing = current
            val source = current ?: args.copyOf?.let { machines.byId(it) }
            mutableState.value =
                when {
                    current != null -> MachineFormState.of(current)
                    source != null -> MachineFormState.of(source, name = args.name)
                    else -> MachineFormState(name = args.name)
                }
            refreshUnlink()
        }
    }

    /**
     * A linkId already on hand is proof enough and works offline. An original itself keeps none,
     * so whether a friend copied it — and the link can be broken from either side — needs asking
     * the group online; a failed read just leaves the menu off.
     */
    private suspend fun refreshUnlink() {
        val shown = existing
        if (shown == null || shown.userId != accounts.activeId.value) {
            mutableUnlink.value = mutableUnlink.value.copy(available = false)
            return
        }
        if (shown.linkId != null) {
            mutableUnlink.value = mutableUnlink.value.copy(available = true)
            return
        }
        val owner = shown.userId
        mutableUnlink.value = mutableUnlink.value.copy(available = false)
        if (owner == null) return
        val copiedByAFriend =
            reading { friends.groupMachines(owner) }
                .getOrDefault(emptyList())
                .any { it.machine.linkKey == shown.linkKey }
        if (existing?.id == shown.id) {
            mutableUnlink.value = mutableUnlink.value.copy(available = copiedByAFriend)
        }
    }

    fun askToUnlink() {
        if (mutableUnlink.value.available) {
            mutableUnlink.value = mutableUnlink.value.copy(confirming = true)
        }
    }

    fun cancelUnlink() {
        mutableUnlink.value = mutableUnlink.value.copy(confirming = false)
    }

    /** The confirmation is the deliberate step, so the key is written at once (spec §2.3). */
    fun confirmUnlink() {
        val shown = existing ?: return
        writes.launch {
            val owner = currentUser.id()
            val stored = machines.byId(shown.id)?.takeIf { it.userId == owner } ?: return@launch
            val unlinked = stored.copy(linkId = MachineId.random(), updatedAt = clock.now())
            machines.upsert(unlinked)
            existing = unlinked
            mutableUnlink.value = mutableUnlink.value.copy(confirming = false)
        }
    }

    fun update(change: (MachineFormState) -> MachineFormState) {
        mutableState.value = change(mutableState.value)
    }

    fun save(onSaved: (MachineId) -> Unit) {
        val form = mutableState.value
        val platformWeight = form.platformWeightValue ?: return
        val weightStep = form.weightStepValue ?: return
        if (!form.canSave) return
        writes.launch {
            val now = clock.now()
            val owner = currentUser.id()
            val base =
                existing?.takeIf { it.userId == owner }
                    ?: Machine.new(form.name.trim(), owner, now)
            val machine =
                base.copy(
                    name = form.name.trim(),
                    setupNote = form.setupNote.trim(),
                    weightMode = form.weightMode,
                    platformWeight = platformWeight,
                    platformIncluded = form.platformIncluded,
                    unit = form.unit,
                    unitLabel = if (form.unit == WeightUnit.Custom) form.unitLabel.trim() else "",
                    weightStep = weightStep,
                    updatedAt = now,
                )
            machines.upsert(machine)
            onSaved(machine.id)
        }
    }
}
