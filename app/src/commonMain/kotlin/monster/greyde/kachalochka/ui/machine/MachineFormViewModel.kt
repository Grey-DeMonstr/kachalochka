package monster.greyde.kachalochka.ui.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineRepository
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.format.formatNumber
import kotlin.time.Clock

data class MachineFormArgs(
    val machineId: MachineId?,
    val copyOf: MachineId?,
    val name: String,
)

data class MachineFormState(
    val name: String = "",
    val setupNote: String = "",
    val weightMode: WeightMode = WeightMode.Total,
    val platformWeight: String = "0",
    val platformIncluded: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val weightStep: Double = 2.5,
) {
    val platformWeightValue: Double?
        get() =
            if (platformWeight.isBlank()) {
                0.0
            } else {
                platformWeight
                    .trim()
                    .replace(',', '.')
                    .toDoubleOrNull()
                    ?.takeIf { it >= 0 }
            }

    val canSave: Boolean get() = name.isNotBlank() && platformWeightValue != null

    companion object {
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
            weightStep = machine.weightStep,
        )
    }
}

class MachineFormViewModel(
    private val args: MachineFormArgs,
    private val machines: MachineRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MachineFormState(name = args.name))
    val state: StateFlow<MachineFormState> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var existing: Machine? = null
    private var loaded = false

    /** A switch keeps the typed edits and drops the row under them, which it no longer owns. */
    init {
        viewModelScope.launch {
            accounts.activeId.collect { active ->
                existing = existing?.takeIf { it.userId == active }
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
        }
    }

    fun update(change: (MachineFormState) -> MachineFormState) {
        mutableState.value = change(mutableState.value)
    }

    fun save(onSaved: (MachineId) -> Unit) {
        val form = mutableState.value
        val platformWeight = form.platformWeightValue ?: return
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
                    weightStep = form.weightStep,
                    updatedAt = now,
                )
            machines.upsert(machine)
            onSaved(machine.id)
        }
    }
}
