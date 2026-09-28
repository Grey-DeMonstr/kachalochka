package monster.greyde.kachalochka.ui.measures

import monster.greyde.kachalochka.core.domain.measures.MeasureKind

/** How to take a predefined measure so that values from different days compare. */
internal fun howToMeasure(kind: MeasureKind): String =
    when (kind) {
        MeasureKind.Weight -> "Утром натощак, после туалета, без одежды."
        MeasureKind.Waist ->
            "Лента горизонтально: мужчинам — на уровне пупка, женщинам — в самом узком месте. " +
                "На спокойном выдохе, не втягивая живот."
        MeasureKind.Chest ->
            "Лента горизонтально через самую выступающую часть груди и под лопатками, руки " +
                "опущены, на спокойном выдохе."
        MeasureKind.Hips ->
            "Стопы вместе, лента горизонтально через самую выступающую часть ягодиц."
        MeasureKind.Biceps ->
            "Рука согнута и напряжена, лента через самую высокую точку бицепса."
        MeasureKind.Thigh ->
            "Стоя, вес на обеих ногах, лента горизонтально сразу под ягодичной складкой."
        MeasureKind.Neck ->
            "Сразу под кадыком, лента чуть наклонена вперёд и вниз, шея расслаблена."
        MeasureKind.BodyFat -> "Значение с умных весов или калипера."
    }
