package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.core.domain.measures.Measure
import monster.greyde.kachalochka.core.domain.measures.MeasureKind
import monster.greyde.kachalochka.ui.strings.AppStrings

/** How to take a predefined measure so that values from different days compare. */
internal fun howToMeasure(kind: MeasureKind): String? {
    val strings = AppStrings.current
    return when (kind) {
        MeasureKind.Weight -> strings.hintWeight
        MeasureKind.Waist -> strings.hintWaist
        MeasureKind.Chest -> strings.hintChest
        MeasureKind.Hips -> strings.hintHips
        MeasureKind.Biceps -> strings.hintBiceps
        MeasureKind.Thigh -> strings.hintThigh
        MeasureKind.Neck -> strings.hintNeck
        MeasureKind.BodyFat -> null
    }
}

/** A predefined measure is named in the app's language; the user's own keeps what they typed. */
internal fun Measure.shownName(): String {
    val strings = AppStrings.current
    return when (kind) {
        MeasureKind.Weight -> strings.measureWeight
        MeasureKind.Waist -> strings.measureWaist
        MeasureKind.Chest -> strings.measureChest
        MeasureKind.Hips -> strings.measureHips
        MeasureKind.Biceps -> strings.measureBiceps
        MeasureKind.Thigh -> strings.measureThigh
        MeasureKind.Neck -> strings.measureNeck
        MeasureKind.BodyFat, null -> displayName
    }
}

internal fun Measure.shownUnit(): String =
    when (kind) {
        MeasureKind.Weight -> AppStrings.current.kg
        MeasureKind.BodyFat, null -> displayUnit
        else -> AppStrings.current.cm
    }
