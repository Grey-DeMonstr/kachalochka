package monster.greyde.kachalochka.ui.measures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.CalendarMonth
import monster.greyde.kachalochka.core.domain.identity.CurrentUser
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.BodyInputs
import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureId
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.core.domain.measures.MeasureRepository
import monster.greyde.kachalochka.core.domain.measures.Measurement
import monster.greyde.kachalochka.core.domain.measures.MeasurementId
import monster.greyde.kachalochka.core.domain.measures.MeasurementRepository
import monster.greyde.kachalochka.core.domain.measures.bodyFat
import monster.greyde.kachalochka.core.domain.measures.missingInputs
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.ui.WriteGuard
import monster.greyde.kachalochka.ui.calendar.monthRank
import monster.greyde.kachalochka.ui.calendar.monthWeeks
import monster.greyde.kachalochka.ui.format.UtcOffset
import monster.greyde.kachalochka.ui.format.dayMonthLabel
import monster.greyde.kachalochka.ui.format.formatNumber
import monster.greyde.kachalochka.ui.format.monthTitle
import monster.greyde.kachalochka.ui.format.parseDecimal
import monster.greyde.kachalochka.ui.format.weekdayName
import kotlin.time.Clock
import kotlin.time.Instant

// A typo such as 18 cm or 1800 would otherwise yield a confident nonsense percent.
private const val EARLIEST_BIRTH_YEAR = 1900
private val heightRange = 50.0..250.0

/** [hint] is the measure's latest value before the form's day. */
data class MeasureFieldUi(
    val id: MeasureId,
    val name: String,
    val unit: String,
    val text: String,
    val hint: String?,
    val valid: Boolean,
    val kind: MeasureKind?,
)

data class MeasurementFormUi(
    val day: CalendarDay,
    val dayTitle: String,
    val fields: List<MeasureFieldUi>,
    val canSave: Boolean,
    val canDelete: Boolean,
    val picker: DayPickerUi?,
    val deleting: Boolean,
    val calculator: BodyFatSheetUi?,
) {
    val picking: Boolean get() = picker != null
}

class MeasurementFormViewModel(
    initialDay: CalendarDay?,
    private val measures: MeasureRepository,
    private val measurements: MeasurementRepository,
    private val profiles: ProfileRepository,
    private val currentUser: CurrentUser,
    private val accounts: Accounts,
    private val clock: Clock,
    private val utcOffset: UtcOffset,
    private val sync: SyncTrigger,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MeasurementFormUi?>(null)
    val state: StateFlow<MeasurementFormUi?> = mutableState
    private val writes = WriteGuard(viewModelScope)

    private var day: CalendarDay = initialDay ?: today()
    private var shown: List<Measure> = emptyList()

    /** The shown measures' values, newest day first. */
    private var values: List<Measurement> = emptyList()
    private var onDay: Map<MeasureId, Measurement> = emptyMap()
    private var texts: Map<MeasureId, String> = emptyMap()
    private var pickerMonth: CalendarMonth? = null
    private var deleting = false
    private var loading: Job? = null
    private var calculating = false
    private var profile: Profile? = null

    /** The body parameters being entered; while set the sheet asks for them. */
    private var draft: BodyParamsUi? = null

    /** Another account has other measures, so a switch starts the form over. */
    init {
        viewModelScope.launch { accounts.activeId.collect { load() } }
    }

    private fun load() {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                val owner = currentUser.id()
                shown = measures.all(owner)
                val ids = shown.map { it.id }.toSet()
                values = measurements.all(owner).filter { it.measureId in ids }
                showDay()
            }
    }

    /** Anything typed for the previous day is dropped. */
    private fun showDay() {
        onDay = values.filter { it.day == day }.associateBy { it.measureId }
        texts = onDay.mapValues { formatNumber(it.value.value) }
        deleting = false
        calculating = false
        draft = null
        publish()
    }

    fun type(
        id: MeasureId,
        text: String,
    ) {
        texts = texts + (id to text)
        publish()
    }

    fun pickDay() {
        pickerMonth = CalendarMonth.of(day)
        publish()
    }

    fun showMonth(direction: Int) {
        val next = pickerMonth?.plusMonths(direction) ?: return
        if (monthRank(next) > monthRank(CalendarMonth.of(today()))) return
        pickerMonth = next
        publish()
    }

    fun chooseDay(chosen: CalendarDay) {
        if (chosen > today()) return
        day = chosen
        pickerMonth = null
        showDay()
    }

    fun dismissPick() {
        pickerMonth = null
        publish()
    }

    fun save(onSaved: () -> Unit) {
        if (mutableState.value?.canSave != true) return
        writes.launch {
            val owner = currentUser.id()
            val now = clock.now()
            shown.forEach { measure ->
                val existing = onDay[measure.id]
                val value = typedValue(measure.id)
                if (value == existing?.value) return@forEach
                val row =
                    when {
                        value == null -> existing?.copy(deleted = true, updatedAt = now)
                        existing != null -> existing.copy(value = value, updatedAt = now)
                        else -> newMeasurement(owner, measure.id, value, now)
                    }
                row?.let { measurements.upsert(it) }
            }
            requestSyncIfPast()
            onSaved()
        }
    }

    fun askDelete() {
        if (onDay.isEmpty()) return
        deleting = true
        publish()
    }

    fun cancelDelete() {
        deleting = false
        publish()
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        if (!deleting) return
        writes.launch {
            val now = clock.now()
            onDay.values.forEach { measurements.upsert(it.copy(deleted = true, updatedAt = now)) }
            requestSyncIfPast()
            deleting = false
            onDeleted()
        }
    }

    fun openCalculator() {
        viewModelScope.launch {
            val stored = profiles.forOwner(currentUser.id())
            profile = stored
            draft = if (stored.hasBody()) null else paramsOf(stored)
            calculating = true
            publish()
        }
    }

    fun closeCalculator() {
        calculating = false
        draft = null
        publish()
    }

    fun editBodyParams() {
        draft = paramsOf(profile)
        publish()
    }

    fun chooseSex(sex: Sex) = editDraft { it.copy(sex = sex) }

    fun typeBirthYear(text: String) = editDraft { it.copy(birthYear = text) }

    fun typeHeight(text: String) = editDraft { it.copy(height = text) }

    fun saveBodyParams() {
        val params = draft ?: return
        val sex = params.sex ?: return
        val birthYear = birthYearOf(params.birthYear) ?: return
        val height = heightOf(params.height) ?: return
        writes.launch {
            val owner = currentUser.id()
            val now = clock.now()
            // Read afresh so a nickname or friend colour saved meanwhile survives.
            val saved =
                (profiles.forOwner(owner) ?: Profile.new(owner, now)).copy(
                    sex = sex,
                    birthYear = birthYear,
                    heightCm = height,
                    updatedAt = now,
                )
            profiles.upsert(saved)
            profile = saved
            draft = null
            publish()
            sync.request()
        }
    }

    /** Fills the fat field only; the form's own save writes it. */
    fun useResult(method: BodyFatMethod) {
        val field = shown.firstOrNull { it.kind == MeasureKind.BodyFat } ?: return
        val percent = bodyFat(method, bodyInputs()) ?: return
        texts = texts + (field.id to oneDecimal(percent))
        calculating = false
        publish()
    }

    private fun editDraft(change: (BodyParamsUi) -> BodyParamsUi) {
        val edited = change(draft ?: return)
        draft = edited.copy(canSave = paramsValid(edited))
        publish()
    }

    private fun paramsOf(stored: Profile?): BodyParamsUi {
        val params =
            BodyParamsUi(
                sex = stored?.sex,
                birthYear = stored?.birthYear?.toString().orEmpty(),
                height = stored?.heightCm?.let(::formatNumber).orEmpty(),
                canSave = false,
            )
        return params.copy(canSave = paramsValid(params))
    }

    private fun paramsValid(params: BodyParamsUi): Boolean =
        params.sex != null &&
            birthYearOf(params.birthYear) != null &&
            heightOf(params.height) != null

    private fun birthYearOf(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in EARLIEST_BIRTH_YEAR..today().year }

    private fun heightOf(text: String): Double? = parseDecimal(text)?.takeIf { it in heightRange }

    private fun Profile?.hasBody(): Boolean =
        this?.sex != null && birthYear != null && heightCm != null

    private fun bodyInputs(): BodyInputs =
        BodyInputs(
            sex = profile?.sex,
            age = profile?.birthYear?.let { day.year - it },
            heightCm = profile?.heightCm,
            weightKg = measured(MeasureKind.Weight),
            waistCm = measured(MeasureKind.Waist),
            neckCm = measured(MeasureKind.Neck),
            hipsCm = measured(MeasureKind.Hips),
        )

    /** The form's value of [kind], else the one its placeholder shows. */
    private fun measured(kind: MeasureKind): Double? {
        val ids = shown.filter { it.kind == kind }.map { it.id }
        return ids.firstNotNullOfOrNull(::typedValue)
            ?: values.firstOrNull { it.measureId in ids && it.day < day }?.value
    }

    private fun calculator(): BodyFatSheetUi {
        val params = draft
        if (params != null) return BodyFatSheetUi(params, body = null, methods = emptyList())
        val inputs = bodyInputs()
        return BodyFatSheetUi(
            params = null,
            body =
                profile?.let { stored ->
                    val sex = stored.sex ?: return@let null
                    val year = stored.birthYear ?: return@let null
                    val height = stored.heightCm ?: return@let null
                    "${sexName(sex)}, $year г. р., ${formatNumber(height)} см"
                },
            methods =
                BodyFatMethod.entries.map { method ->
                    val missing = missingInputs(method, inputs)
                    FatMethodUi(
                        method = method,
                        name = methodName(method),
                        result = bodyFat(method, inputs)?.let { "${oneDecimal(it)} %" },
                        missing =
                            missing
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(", ", prefix = "Нужно: ", transform = ::inputName),
                    )
                },
        )
    }

    /** A past day's edit is a one-off, so it is pushed at once, as a past visit's is. */
    private fun requestSyncIfPast() {
        if (day != today()) sync.request()
    }

    private fun newMeasurement(
        owner: UserId?,
        measure: MeasureId,
        value: Double,
        now: Instant,
    ) = Measurement(MeasurementId.random(), owner, measure, day, value, now, false)

    private fun typedValue(id: MeasureId): Double? =
        texts[id].orEmpty().takeIf { it.isNotBlank() }?.let(::parseDecimal)

    private fun valid(id: MeasureId): Boolean {
        val text = texts[id].orEmpty()
        return text.isBlank() || parseDecimal(text) != null
    }

    private fun changed(id: MeasureId): Boolean = !valid(id) || typedValue(id) != onDay[id]?.value

    private fun publish() {
        val today = today()
        val fields =
            shown.map { measure ->
                MeasureFieldUi(
                    id = measure.id,
                    name = measure.name,
                    unit = measure.unit,
                    text = texts[measure.id].orEmpty(),
                    hint =
                        values
                            .firstOrNull { it.measureId == measure.id && it.day < day }
                            ?.let { formatNumber(it.value) },
                    valid = valid(measure.id),
                    kind = measure.kind,
                )
            }
        val dayName = if (day == today) "Сегодня" else weekdayName(day.dayOfWeek)
        mutableState.value =
            MeasurementFormUi(
                day = day,
                dayTitle = "$dayName, ${dayMonthLabel(day, today.year)}",
                fields = fields,
                canSave = fields.all { it.valid } && shown.any { changed(it.id) },
                canDelete = onDay.isNotEmpty(),
                picker =
                    pickerMonth?.let { month ->
                        DayPickerUi(
                            monthTitle = monthTitle(month),
                            canShowNextMonth =
                                monthRank(month) < monthRank(CalendarMonth.of(today)),
                            weeks = monthWeeks(month, values.map { it.day }.toSet(), today, day),
                        )
                    },
                deleting = deleting,
                calculator = if (calculating) calculator() else null,
            )
    }

    private fun today(): CalendarDay {
        val now = clock.now()
        return CalendarDay.of(now, utcOffset.at(now))
    }
}
