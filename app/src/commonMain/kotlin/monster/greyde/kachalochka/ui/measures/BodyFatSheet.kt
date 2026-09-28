package monster.greyde.kachalochka.ui.measures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import monster.greyde.kachalochka.core.domain.measures.BodyFatMethod
import monster.greyde.kachalochka.core.domain.measures.BodyInput
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.ui.components.AccentButton
import monster.greyde.kachalochka.ui.components.Choice
import monster.greyde.kachalochka.ui.components.ChoiceRow
import monster.greyde.kachalochka.ui.components.Rule
import monster.greyde.kachalochka.ui.icons.PhosphorIcons

data class BodyParamsUi(
    val sex: Sex?,
    val birthYear: String,
    val height: String,
    val canSave: Boolean,
)

/** [result] when the inputs suffice, else [missing] names what they lack. */
data class FatMethodUi(
    val method: BodyFatMethod,
    val name: String,
    val result: String?,
    val missing: String?,
)

/** While [params] is set the sheet asks for them and lists no methods. */
data class BodyFatSheetUi(
    val params: BodyParamsUi?,
    val body: String?,
    val methods: List<FatMethodUi>,
)

internal fun methodName(method: BodyFatMethod): String =
    when (method) {
        BodyFatMethod.Navy -> "ВМС США"
        BodyFatMethod.Ymca -> "YMCA"
        BodyFatMethod.Deurenberg -> "Дойренберг (ИМТ)"
    }

internal fun inputName(input: BodyInput): String =
    when (input) {
        BodyInput.Sex -> "пол"
        BodyInput.Age -> "год рождения"
        BodyInput.Height -> "рост"
        BodyInput.Weight -> "вес"
        BodyInput.Waist -> "талия"
        BodyInput.Neck -> "шея"
        BodyInput.Hips -> "бёдра"
    }

internal fun sexName(sex: Sex): String =
    when (sex) {
        Sex.Male -> "Мужчина"
        Sex.Female -> "Женщина"
    }

@Composable
internal fun BodyFatSheet(
    sheet: BodyFatSheetUi,
    onSex: (Sex) -> Unit,
    onBirthYear: (String) -> Unit,
    onHeight: (String) -> Unit,
    onSaveParams: () -> Unit,
    onEditParams: () -> Unit,
    onUse: (BodyFatMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .testTag("fat-sheet"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Процент жира", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                val params = sheet.params
                if (params != null) {
                    BodyParams(params, onSex, onBirthYear, onHeight, onSaveParams)
                } else {
                    sheet.body?.let { BodySummary(it, onEditParams) }
                    sheet.methods.forEach { FatMethodRow(it, onUse) }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).testTag("fat-sheet-cancel"),
                ) {
                    Text("Отмена")
                }
            }
        }
    }
}

@Composable
private fun BodyParams(
    params: BodyParamsUi,
    onSex: (Sex) -> Unit,
    onBirthYear: (String) -> Unit,
    onHeight: (String) -> Unit,
    onSave: () -> Unit,
) {
    val sexes = listOf(Sex.Male, Sex.Female)
    Text(
        "Формулам нужны пол, возраст и рост. Они сохранятся в профиле.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
    )
    ChoiceRow(
        listOf(Choice("Мужчина", "body-sex-male"), Choice("Женщина", "body-sex-female")),
        selected = sexes.indexOf(params.sex),
        onSelect = { onSex(sexes[it]) },
    )
    OutlinedTextField(
        value = params.birthYear,
        onValueChange = onBirthYear,
        label = { Text("Год рождения") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("body-birth-year"),
    )
    OutlinedTextField(
        value = params.height,
        onValueChange = onHeight,
        label = { Text("Рост, см") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("body-height"),
    )
    AccentButton(
        "Сохранить",
        PhosphorIcons.Check,
        onSave,
        Modifier.testTag("save-body-params"),
        height = 56.dp,
        enabled = params.canSave,
    )
}

@Composable
private fun BodySummary(
    body: String,
    onEdit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            body,
            Modifier.weight(1f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        TextButton(onClick = onEdit, modifier = Modifier.testTag("edit-body-params")) {
            Text("Изменить")
        }
    }
    Rule()
}

@Composable
private fun FatMethodRow(
    method: FatMethodUi,
    onUse: (BodyFatMethod) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = method.result != null) { onUse(method.method) }
            .testTag("fat-method-${method.method.name}")
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(method.name, Modifier.weight(1f), fontSize = 16.sp, color = colors.onSurface)
        if (method.result != null) {
            Text(
                method.result,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onPrimaryContainer,
            )
        } else {
            Text(
                method.missing ?: "Не рассчитать по этим значениям",
                fontSize = 13.sp,
                color = colors.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}
