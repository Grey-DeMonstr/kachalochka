package monster.greyde.kachalochka.ui.strings

object RuStrings : Strings {
    override val appName = "Качалочка"
    override val back = "Назад"
    override val cancel = "Отмена"
    override val delete = "Удалить"
    override val save = "Сохранить"
    override val add = "Добавить"
    override val create = "Создать"
    override val more = "Ещё"
    override val done = "Готово"
    override val reorder = "Порядок"
    override val drag = "Перетащить"
    override val offline = "Нет связи с сервером"
    override val retry = "Повторить"
    override val loading = "Загрузка…"
    override val settings = "Настройки"
    override val name = "Название"
    override val unitExample = "Единица, например см"
    override val noVisit = "Нет визита"
    override val photo = "Фото"

    override val kg = "кг"
    override val cm = "см"
    override val customUnitFallback = "ед."
    override val modeTotal = "всего"
    override val modePerSide = "на сторону"
    override val modeCounterweight = "гравитрон"
    override val perSideShared = " на каждую,"

    override fun sets(n: Int) = "$n ${plural(n, "подход", "подхода", "подходов")}"

    override fun machines(n: Int) = "$n ${plural(n, "упражнение", "упражнения", "упражнений")}"

    override fun members(n: Int) = "$n ${plural(n, "участник", "участника", "участников")}"

    override fun daysAgo(days: Int) =
        when (days) {
            0 -> "сегодня"
            1 -> "вчера"
            else -> "$days ${plural(days, "день", "дня", "дней")} назад"
        }

    private val monthsGenitive =
        listOf(
            "января",
            "февраля",
            "марта",
            "апреля",
            "мая",
            "июня",
            "июля",
            "августа",
            "сентября",
            "октября",
            "ноября",
            "декабря",
        )

    override fun dayMonth(
        day: Int,
        month: Int,
    ) = "$day ${monthsGenitive[month - 1]}"

    private val months =
        listOf(
            "Январь",
            "Февраль",
            "Март",
            "Апрель",
            "Май",
            "Июнь",
            "Июль",
            "Август",
            "Сентябрь",
            "Октябрь",
            "Ноябрь",
            "Декабрь",
        )

    override fun monthTitle(month: Int) = months[month - 1]

    private val weekdays =
        listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

    override fun weekday(dayOfWeek: Int) = weekdays[dayOfWeek - 1]

    override fun weekdayShort(dayOfWeek: Int) =
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[dayOfWeek - 1]

    override fun weekdayShared(dayOfWeek: Int) = weekdayShort(dayOfWeek).lowercase()

    override val recordingAs = "Пишем подходы в"
    override val addAccount = "Добавить аккаунт"

    override fun signOutOf(name: String) = "Выйти из аккаунта «$name»"

    override val signInFailed = "Не удалось войти. Попробуйте ещё раз"
    override val signInWithGoogle = "Войти через Google"
    override val signInUnavailable = "Вход недоступен — сборка не настроена"

    override val visits = "Визиты"
    override val machinesSection = "Упражнения"
    override val measurements = "Замеры"
    override val plans = "Планы"
    override val statistics = "Статистика"
    override val friends = "Друзья"

    override fun version(name: String) = "Версия $name"

    override val todayTitle = "Сегодня"
    override val noSetsYet = "Подходов пока нет"

    override val startVisit = "Начать"
    override val continueVisit = "Продолжить"
    override val locked = "Заблокировано"

    override val visit = "Визит"

    override val share = "Поделиться"
    override val groupByTag = "Группировать по тегам"
    override val untaggedSection = "Остальное"
    override val planned = "Запланировано"
    override val unplan = "Убрать"
    override val noPlansYet = "Планов пока нет"
    override val newPlan = "Новый план"
    override val startPlan = "Начать"
    override val untitledPlan = "Без названия"
    override val plan = "План"
    override val planName = "Название"
    override val addMachine = "Добавить упражнение"
    override val savePlan = "Сохранить план"
    override val deletePlan = "Удалить план"
    override val deletePlanTitle = "Удалить план?"
    override val deletePlanText = "План исчезнет из списка."
    override val startPlanTitle = "Начать план?"
    override val startPlanText =
        "Его упражнения добавятся в сегодняшний визит, а сам план исчезнет из списка."
    override val saveAsPlan = "Сохранить как план"
    override val reps = "повторы"
    override val comment = "Комментарий"
    override val deleteSet = "Удалить подход"
    override val saveSet = "Добавить"

    override val newSet = "Новый подход"

    override fun editOf(set: String) = "Правка: $set"

    override val record = "Рекорд"

    override val addVisit = "Добавить визит"
    override val replace = "Заменить"
    override val chooseNewDay = "Выберите новый день"
    override val move = "Перенести"
    override val deleteVisitTitle = "Удалить визит?"
    override val replaceVisitTitle = "Заменить визит?"

    override fun replaceVisitText(
        date: String,
        sets: String,
    ) = "На $date уже есть визит: $sets. Он и его подходы пропадут из истории и статистики."

    override val previousMonth = "Предыдущий месяц"
    override val nextMonth = "Следующий месяц"

    override val machine = "Упражнение"
    override val machines = "Упражнения"
    override val noMachinesYet = "Упражнений пока нет"
    override val friendsMachines = "Упражнения друзей"
    override val myMachines = "Мои упражнения"
    override val friendMachine = "Упражнение друга"
    override val setupNote = "Комментарий"
    override val howWeightCounts = "Как считается вес"
    override val platformWeight = "Вес платформы"
    override val takeForMyself = "Взять себе"
    override val platformAdded = "прибавляется к записи"
    override val platformBeside = "рядом с названием"
    override val linkTo = "Привязать к…"
    override val linkToMine = "Привязать к моему"
    override val suggestedLinks = "Предложенные связи"
    override val suggestedLink = "Предложенная связь"
    override val merge = "Объединить"
    override val mergeTitle = "Объединить упражнения?"

    override fun mergeText(
        kept: String,
        removed: String,
    ) = "Останется «$kept», подходы «$removed» перейдут к нему. Это нельзя отменить."

    override val mergeKeep = "Оставить"
    override val suggestedKeep = "Рекомендуется"

    override fun lastSetOn(day: String) = "последний $day"

    override fun mergeAdjust(
        removed: String,
        kept: String,
        shift: String,
    ) = "Пересчитать подходы «$removed» под платформу «$kept»: $shift"

    override val recalculateTitle = "Пересчитать историю?"

    override fun recalculateText(
        sets: String,
        shift: String,
    ) = "Платформа изменилась. Изменить вес записанных подходов ($sets) на $shift, чтобы " +
        "итоговый вес остался прежним?"

    override fun recalculateUnitText(
        sets: String,
        from: String,
        to: String,
        shift: String?,
    ) = "Единица изменилась: $from → $to. Перевести вес записанных подходов ($sets) в новую " +
        "единицу" + shift?.let { " и изменить его на $it под платформу" }.orEmpty() + "?"

    override fun recalculatePerSideText(sets: String) =
        "Вес теперь считается на сторону. Разделить вес записанных подходов ($sets) пополам?"

    override fun recalculateTotalText(sets: String) =
        "Вес теперь считается всего. Удвоить вес записанных подходов ($sets)?"

    override fun mergeConvert(
        removed: String,
        from: String,
        to: String,
    ) = "Перевести подходы «$removed» из $from в $to"

    override val recalculate = "Пересчитать"
    override val keepAsRecorded = "Оставить как есть"

    override val unitName = "Название единицы"
    override val unlinkTitle = "Отвязать упражнение?"
    override val unlinkText = "Результаты друзей в этом упражнении перестанут показываться у вас."
    override val unlink = "Отвязать"
    override val newTag = "Новый тег"
    override val choiceTotal = "Всего"
    override val choicePerSide = "На сторону"
    override val choiceCounterweight = "Гравитрон"
    override val counterweightHint = "Вес считается отрицательным: чем меньше, тем лучше."
    override val addToRecord = "Прибавлять к записи"
    override val platformIncludedHint = "Включено: вес платформы входит в каждую запись."
    override val platformApartHint =
        "Выключено: записывается только навесной вес, а платформа стоит рядом с " +
            "названием — «Жим ногами (+25 кг) 70 кг × 10»."
    override val customUnit = "Своя единица"
    override val weightStep = "Шаг веса"
    override val unlinkFromFriends = "Отвязать от друзей"
    override val basedOnExisting = "На основе существующего"
    override val photoNoteAndSetup = "Фото, комментарий и настройка веса"
    override val copyMachine = "Скопировать упражнение"
    override val copyKeeps = "Заметка и настройка веса сохранятся"
    override val recent = "Недавние"
    override val similar = "Похожие"

    override fun createNamed(name: String) = "Создать «$name»"

    override fun withTags(tags: List<String>) =
        (if (tags.size == 1) "С тегом " else "С тегами ") + tags.joinToString(", ") { "«$it»" }

    override val nothingFound = "Ничего не найдено"
    override val alreadyInVisit = "Уже в визите"
    override val overallStats = "Общая"
    override val statsMonth = "Месяц"
    override val statsThreeMonths = "3 месяца"
    override val statsSixMonths = "6 месяцев"
    override val statsYear = "Год"
    override val allResults = "Все результаты"
    override val noSetsInPeriod = "Нет подходов за период"
    override val machinesInPeriod = "Упражнения за период"
    override val startSet = "Начальный"
    override val best = "Лучший"
    override val noChange = "Без изменений"
    override val clearSearch = "Очистить поиск"
    override val copySettings = "Скопировать настройки"
    override val newMachine = "Новое упражнение"
    override val improvementsOnly = "Только улучшения"
    override val noImprovements = "Нет улучшений за период"
    override val export = "Экспорт"

    override fun forPeriod(months: Int) =
        when (months) {
            1 -> "за месяц"
            12 -> "за год"
            else -> "за $months ${plural(months, "месяц", "месяца", "месяцев")}"
        }

    override fun sinceDay(day: String) = "с $day"

    override fun beforeDay(day: String) = "До $day"

    override fun moreReps(n: Int) = "+$n повт."

    override fun fewerReps(n: Int) = "−$n повт."

    override val makeCover = "Сделать основным"
    override val coverPhoto = "Основное фото"
    override val sortName = "А–Я"
    override val sortFrequent = "Частые"
    override val sortGrowth = "Рост"

    override val addPhoto = "Добавить фото"
    override val takePhoto = "Снять фото"
    override val fromGallery = "Из галереи"
    override val googlePictureReturns = "Вернётся фото из Google"

    override val joinTitle = "Вступить в группу по приглашению?"
    override val joinText = "Участники группы увидят ваши визиты и упражнения."
    override val join = "Вступить"
    override val inviteNotFound = "Приглашение не найдено"
    override val understood = "Понятно"
    override val group = "Группа"
    override val membersSection = "Участники"

    override fun inviteCode(code: String) = "Код приглашения: $code"

    override val invite = "Пригласить"
    override val deleteGroup = "Удалить группу"
    override val leaveGroup = "Выйти из группы"
    override val owner = "владелец"

    override fun colorName(n: Int) = "Цвет $n"

    override val calendarColor = "Цвет в календаре"
    override val noGroupsYet = "Групп пока нет"
    override val createGroup = "Создать группу"
    override val joinByCode = "Вступить по коду"
    override val newGroup = "Новая группа"
    override val joinGroup = "Вступить в группу"
    override val deleteGroupTitle = "Удалить группу?"
    override val deleteGroupText = "Участники перестанут видеть визиты друг друга."
    override val leaveGroupTitle = "Выйти из группы?"
    override val leaveGroupText = "Вы перестанете видеть визиты участников, а они — ваши."
    override val leave = "Выйти"

    override fun inviteMessage(group: String) = "Вступай в группу «$group» в Качалочке"

    override fun codeLine(code: String) = "Код: $code"

    override val linkCopied = "Ссылка скопирована"
    override val copied = "Скопировано"
    override val copyFailed = "Не удалось скопировать"

    override val children = "Дети"
    override val noChildren = "Детей пока нет"
    override val addChild = "Добавить ребёнка"

    override fun childCode(code: String) = "Код для ребёнка: $code"

    override val childCodeHint =
        "Ребёнок вводит этот код на своём устройстве: Настройки → Родители. Код работает один " +
            "раз в течение суток. Family Link может один раз попросить вас разрешить ребёнку " +
            "вход в Качалочку."
    override val removeLink = "Убрать"
    override val removeChildTitle = "Убрать ребёнка?"
    override val removeChildText =
        "Вы больше не сможете записывать тренировки ребёнка, и записанное для него пропадёт с " +
            "этого устройства. Уже синхронизированное останется у ребёнка."
    override val guardianInviteMessage = "Добавь меня родителем в Качалочке"
    override val guardians = "Родители"
    override val noGuardians = "Родителей пока нет"
    override val addGuardian = "Добавить родителя"
    override val guardianCode = "Код от родителя"
    override val guardianRights =
        "Родитель записывает и видит ваши визиты, упражнения, фото и планы. Ваш профиль, " +
            "замеры и группы он не видит."
    override val guardianCodeUnknown = "Код не найден или устарел"
    override val ownGuardianCode = "Это ваш собственный код"
    override val removeGuardianTitle = "Убрать родителя?"
    override val removeGuardianText =
        "Родитель больше не сможет записывать ваши тренировки и видеть их."
    override val guardianInviteTitle = "Добавить родителя по приглашению?"
    override val childAccount = "Ребёнок"

    override val measurement = "Замер"
    override val deleteMeasurement = "Удалить замер"
    override val deleteMeasurementTitle = "Удалить замер?"

    override fun deleteMeasurementText(day: String) = "$day: все значения этого дня пропадут."

    override val periodMonth = "1 мес"
    override val periodQuarter = "3 мес"
    override val periodHalfYear = "6 мес"
    override val periodYear = "Год"
    override val periodAll = "Всё"
    override val history = "История"
    override val editHistory = "Редактировать историю"
    override val deleteMeasureTitle = "Удалить показатель?"

    override fun deleteMeasureText(name: String) = "«$name» и все его значения пропадут."

    override val noValues = "Нет значений"
    override val noValuesInPeriod = "За этот период значений нет"
    override val nameAndUnit = "Название и единица"
    override val deleteMeasure = "Удалить показатель"
    override val measure = "Показатель"
    override val measuresSection = "Показатели"
    override val newMeasurement = "Новый замер"
    override val addMeasure = "Добавить показатель"
    override val bodyFat = "Процент жира"
    override val cannotCalculate = "Не рассчитать по этим значениям"
    override val fillProfile = "Укажите пол, дату рождения и рост в настройках"
    override val newMeasure = "Новый показатель"

    override fun needs(inputs: List<String>) = "Нужно: ${inputs.joinToString(", ")}"

    override val navy = "ВМС США"
    override val deurenberg = "Дойренберг"
    override val inputSex = "пол"
    override val inputAge = "дата рождения"
    override val inputHeight = "рост"
    override val inputWeight = "вес"
    override val inputWaist = "талия"
    override val inputNeck = "шея"
    override val inputHips = "обхват бёдер"
    override val measureWeight = "Вес"
    override val measureWaist = "Талия"
    override val measureChest = "Грудь"
    override val measureHips = "Обхват бёдер"
    override val measureBiceps = "Бицепс"
    override val measureThigh = "Окружность бедра"
    override val measureNeck = "Шея"
    override val hintWeight = "Утром натощак, после туалета, без одежды."
    override val hintWaist =
        "Лента горизонтально: мужчинам — на уровне пупка, женщинам — в самом узком месте. " +
            "На спокойном выдохе, не втягивая живот."
    override val hintChest =
        "Лента горизонтально через самую выступающую часть груди и под лопатками, руки " +
            "опущены, на спокойном выдохе."
    override val hintHips =
        "Стопы вместе, лента горизонтально через самую выступающую часть ягодиц."
    override val hintBiceps = "Рука согнута и напряжена, лента через самую высокую точку бицепса."
    override val hintThigh =
        "Стоя, вес на обеих ногах, лента горизонтально сразу под ягодичной складкой."
    override val hintNeck =
        "Сразу под кадыком, лента чуть наклонена вперёд и вниз, шея расслаблена."

    override val profile = "Профиль"
    override val nickname = "Ник"
    override val sex = "Пол"
    override val male = "Мужской"
    override val female = "Женский"
    override val birthDate = "Дата рождения"
    override val datePlaceholder = "ДД.ММ.ГГГГ"
    override val heightCm = "Рост, см"
    override val bodyFieldsHint = "Пол, дата рождения и рост нужны для расчёта процента жира."
    override val weightUnits = "Единицы веса"
    override val mixedUnits = "Смешанные"
    override val mixedUnitsHint = "Смешанные — у каждого упражнения свои единицы."
    override val theme = "Тема"
    override val themeSystem = "Системная"
    override val themeLight = "Светлая"
    override val themeDark = "Тёмная"
    override val language = "Язык"
    override val languageSystem = "Системный"
    override val transitions = "Анимация переходов"

    override fun transitionsHint(max: Int) = "Миллисекунды, до $max; 0 — без анимации"

    override val apply = "Применить"
    override val applyTitle = "Применить изменения?"
    override val discard = "Не применять"
    override val advanced = "Дополнительно"
    override val deleteAccount = "Удалить аккаунт"
    override val deleteAccountTitle = "Удалить аккаунт?"

    override fun deleteAccountText(word: String) =
        "Ваши визиты, упражнения с фото, замеры, профиль и группы будут удалены " +
            "с сервера и с этого устройства. Это нельзя отменить. " +
            "Чтобы подтвердить, введите $word."

    private fun plural(
        n: Int,
        one: String,
        few: String,
        many: String,
    ): String {
        val lastTwo = n % 100
        val last = n % 10
        return when {
            lastTwo in 11..14 -> many
            last == 1 -> one
            last in 2..4 -> few
            else -> many
        }
    }
}
