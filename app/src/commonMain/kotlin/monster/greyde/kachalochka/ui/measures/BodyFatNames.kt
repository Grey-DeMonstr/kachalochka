package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.BodyInput
import monster.greyde.kachalochka.ui.strings.AppStrings

/** The short mark a method's row and every measure it reads carry. */
internal fun methodTag(method: BodyFatMethod): String =
    when (method) {
        BodyFatMethod.Navy -> "NAVY"
        BodyFatMethod.Ymca -> "YMCA"
        BodyFatMethod.Deurenberg -> "BMI"
    }

internal fun methodName(method: BodyFatMethod): String =
    when (method) {
        BodyFatMethod.Navy -> AppStrings.current.navy
        BodyFatMethod.Ymca -> "YMCA"
        BodyFatMethod.Deurenberg -> AppStrings.current.deurenberg
    }

internal fun inputName(input: BodyInput): String =
    when (input) {
        BodyInput.Sex -> AppStrings.current.inputSex
        BodyInput.Age -> AppStrings.current.inputAge
        BodyInput.Height -> AppStrings.current.inputHeight
        BodyInput.Weight -> AppStrings.current.inputWeight
        BodyInput.Waist -> AppStrings.current.inputWaist
        BodyInput.Neck -> AppStrings.current.inputNeck
        BodyInput.Hips -> AppStrings.current.inputHips
    }
