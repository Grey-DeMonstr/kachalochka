package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.friends.FriendsRepository
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkRepository
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

data class LinkingUi(
    val canLink: Boolean = false,
    val linkedWith: List<String> = emptyList(),
    val canUnlink: Boolean = false,
    val confirmingUnlink: Boolean = false,
    val error: String? = null,
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

private const val OFFLINE = "Нет связи с сервером"

class MachineFormViewModel(
    private val args: MachineFormArgs,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val friends: FriendsRepository,
    private val machineLinks: MachineLinkRepository,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineFormState(name = args.name))
    val state: StateFlow<MachineFormState> = mutableState
    private val mutableLinking = MutableStateFlow(LinkingUi())
    val linking: StateFlow<LinkingUi> = mutableLinking
    private val writes = WriteGuard(viewModelScope)

    private var existing: Machine? = null
    private var loaded = false
    private var readingLinks: Job? = null

    /** A switch keeps the typed edits and drops the row under them, which it no longer owns. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect { active ->
                existing = existing?.takeIf { it.userId == active }
                refreshLinks()
            }
        }
    }

    /**
     * The screen asks again whenever it re-enters composition: the form keeps its edits then, and
     * the links are read again, since the chooser may have changed them.
     */
    fun load() {
        if (loaded) {
            refreshLinks()
            return
        }
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
            refreshLinks()
        }
    }

    private fun refreshLinks() {
        readingLinks?.cancel()
        readingLinks = viewModelScope.launch { readLinks() }
    }

    /**
     * Own links are on hand offline; friends' links and machines need the network, and without
     * it the form names no friends.
     */
    private suspend fun readLinks() {
        val shown = existing
        val owner = accounts.activeId.value
        if (shown == null || shown.userId != owner) {
            mutableLinking.value = LinkingUi(error = mutableLinking.value.error)
            return
        }
        val ownLinks = machineLinks.all(owner)
        val ownTouch = ownLinks.any { it.touches(shown.id) }
        mutableLinking.value =
            mutableLinking.value.let {
                it.copy(
                    canLink = true,
                    canUnlink = it.canUnlink || ownTouch,
                )
            }
        val group = owner?.let { loadGroupMachines(it, friends, machineLinks) }
        if (accounts.activeId.value != owner || existing?.id != shown.id) return
        val cluster = group?.clusters?.of(shown.id).orEmpty()
        mutableLinking.value =
            mutableLinking.value.copy(
                linkedWith =
                    group
                        ?.friends
                        .orEmpty()
                        .filter { it.machine.id in cluster }
                        .map { it.owner.displayName }
                        .distinct()
                        .sortedBy { it.lowercase() },
                canUnlink = (group?.links ?: ownLinks).any { it.touches(shown.id) },
            )
    }

    private fun MachineLink.touches(machine: MachineId) =
        machineId == machine || linkedMachineId == machine

    fun askToUnlink() {
        if (mutableLinking.value.canUnlink) {
            mutableLinking.value = mutableLinking.value.copy(confirmingUnlink = true, error = null)
        }
    }

    fun cancelUnlink() {
        mutableLinking.value = mutableLinking.value.copy(confirmingUnlink = false)
    }

    /**
     * Friends' links into the machine can be broken only on the server, so they go first and
     * nothing is written when it does not answer.
     */
    fun confirmUnlink() {
        val shown = existing ?: return
        writes.launch {
            val owner = currentUser.id()
            machines.byId(shown.id)?.takeIf { it.userId == owner } ?: return@launch
            val broken = owner?.let { reading { friends.breakLinks(shown.id) } }
            if (broken?.isFailure == true) {
                mutableLinking.value =
                    mutableLinking.value.copy(confirmingUnlink = false, error = OFFLINE)
                return@launch
            }
            val now = clock.now()
            machineLinks
                .all(owner)
                .filter { it.touches(shown.id) }
                .forEach { machineLinks.upsert(it.copy(deleted = true, updatedAt = now)) }
            sync.request()
            mutableLinking.value =
                mutableLinking.value.copy(confirmingUnlink = false, error = null)
            refreshLinks()
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
