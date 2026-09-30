package monster.greyde.kachalochka.ui.format

import monster.greyde.kachalochka.ui.strings.AppStrings

fun planTitle(
    name: String,
    machineNames: List<String>,
): String =
    name.trim().ifEmpty { machineNames.joinToString(", ") }.ifEmpty {
        AppStrings.current.untitledPlan
    }
