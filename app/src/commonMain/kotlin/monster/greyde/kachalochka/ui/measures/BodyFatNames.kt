package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.BodyInput

/** The short mark a method's row and every measure it reads carry. */
internal fun methodTag(method: BodyFatMethod): String =
    when (method) {
        BodyFatMethod.Navy -> "NAVY"
        BodyFatMethod.Ymca -> "YMCA"
        BodyFatMethod.Deurenberg -> "BMI"
    }

internal fun methodName(method: BodyFatMethod): String =
    when (method) {
        BodyFatMethod.Navy -> "ВМС США"
        BodyFatMethod.Ymca -> "YMCA"
        BodyFatMethod.Deurenberg -> "Дойренберг"
    }

internal fun inputName(input: BodyInput): String =
    when (input) {
        BodyInput.Sex -> "пол"
        BodyInput.Age -> "дата рождения"
        BodyInput.Height -> "рост"
        BodyInput.Weight -> "вес"
        BodyInput.Waist -> "талия"
        BodyInput.Neck -> "шея"
        BodyInput.Hips -> "обхват бёдер"
    }
