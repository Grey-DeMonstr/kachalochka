package monster.greyde.kachalochka.core.domain.measures

import monster.greyde.kachalochka.core.domain.profile.Sex
import kotlin.math.log10

data class BodyInputs(
    val sex: Sex?,
    val age: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val waistCm: Double?,
    val neckCm: Double?,
    val hipsCm: Double?,
)

enum class BodyFatMethod { Navy, Ymca, Deurenberg }

enum class BodyInput { Sex, Age, Height, Weight, Waist, Neck, Hips }

private const val CM_PER_INCH = 2.54
private const val LB_PER_KG = 2.2046226218

// Inputs such as a neck wider than the waist give numbers no body has.
private val plausible = 2.0..70.0

/** Percent, or null when [inputs] lack what the method needs or give a meaningless result. */
fun bodyFat(
    method: BodyFatMethod,
    inputs: BodyInputs,
): Double? {
    val percent =
        when (method) {
            BodyFatMethod.Navy -> navy(inputs)
            BodyFatMethod.Ymca -> ymca(inputs)
            BodyFatMethod.Deurenberg -> deurenberg(inputs)
        }
    return percent?.takeIf { it.isFinite() && it in plausible }
}

/** What [method] needs that [inputs] lack. */
fun missingInputs(
    method: BodyFatMethod,
    inputs: BodyInputs,
): List<BodyInput> {
    val needed =
        when (method) {
            BodyFatMethod.Navy ->
                listOfNotNull(
                    BodyInput.Sex,
                    BodyInput.Height,
                    BodyInput.Waist,
                    BodyInput.Neck,
                    BodyInput.Hips.takeIf { inputs.sex == Sex.Female },
                )
            BodyFatMethod.Ymca -> listOf(BodyInput.Sex, BodyInput.Weight, BodyInput.Waist)
            BodyFatMethod.Deurenberg ->
                listOf(BodyInput.Sex, BodyInput.Age, BodyInput.Height, BodyInput.Weight)
        }
    return needed.filter { inputs.valueOf(it) == null }
}

/**
 * The methods that read [kind] for [sex]. Hips count for an unknown sex too, so they are kept until
 * the sex is known.
 */
fun methodsReading(
    kind: MeasureKind,
    sex: Sex?,
): List<BodyFatMethod> =
    when (kind) {
        MeasureKind.Weight -> listOf(BodyFatMethod.Ymca, BodyFatMethod.Deurenberg)
        MeasureKind.Waist -> listOf(BodyFatMethod.Navy, BodyFatMethod.Ymca)
        MeasureKind.Neck -> listOf(BodyFatMethod.Navy)
        MeasureKind.Hips -> if (sex == Sex.Male) emptyList() else listOf(BodyFatMethod.Navy)
        MeasureKind.Chest,
        MeasureKind.Biceps,
        MeasureKind.Thigh,
        MeasureKind.BodyFat,
        -> emptyList()
    }

/** A measure a method reads for [sex] cannot be deleted. */
fun isLocked(
    kind: MeasureKind,
    sex: Sex?,
): Boolean = methodsReading(kind, sex).isNotEmpty()

private fun BodyInputs.valueOf(input: BodyInput): Any? =
    when (input) {
        BodyInput.Sex -> sex
        BodyInput.Age -> age
        BodyInput.Height -> heightCm
        BodyInput.Weight -> weightKg
        BodyInput.Waist -> waistCm
        BodyInput.Neck -> neckCm
        BodyInput.Hips -> hipsCm
    }

private fun navy(i: BodyInputs): Double? {
    val h = i.heightCm ?: return null
    val waist = i.waistCm ?: return null
    val neck = i.neckCm ?: return null
    return when (i.sex) {
        Sex.Male ->
            (waist - neck).takeIf { it > 0 }?.let {
                495 / (1.0324 - 0.19077 * log10(it) + 0.15456 * log10(h)) - 450
            }
        Sex.Female -> {
            val hips = i.hipsCm ?: return null
            (waist + hips - neck).takeIf { it > 0 }?.let {
                495 / (1.29579 - 0.35004 * log10(it) + 0.22100 * log10(h)) - 450
            }
        }
        null -> null
    }
}

private fun ymca(i: BodyInputs): Double? {
    val sex = i.sex ?: return null
    val weightLb = (i.weightKg ?: return null) * LB_PER_KG
    val waistIn = (i.waistCm ?: return null) / CM_PER_INCH
    val base = if (sex == Sex.Male) -98.42 else -76.76
    return (base + 4.15 * waistIn - 0.082 * weightLb) / weightLb * 100
}

private fun deurenberg(i: BodyInputs): Double? {
    val sex = i.sex ?: return null
    val age = i.age ?: return null
    val h = (i.heightCm ?: return null) / 100
    val bmi = (i.weightKg ?: return null) / (h * h)
    return 1.20 * bmi + 0.23 * age - 10.8 * (if (sex == Sex.Male) 1 else 0) - 5.4
}
