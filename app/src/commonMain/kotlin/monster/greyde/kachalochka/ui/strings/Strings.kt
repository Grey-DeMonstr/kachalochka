package monster.greyde.kachalochka.ui.strings

/** Every text the app shows, in one language. User data is never translated. */
interface Strings {
    // Common
    val appName: String
    val back: String
    val cancel: String
    val delete: String
    val save: String
    val add: String
    val create: String
    val more: String
    val done: String
    val reorder: String
    val drag: String
    val offline: String
    val retry: String
    val loading: String
    val settings: String
    val name: String
    val unitExample: String
    val noVisit: String
    val photo: String

    // Units, modes and counts
    val kg: String
    val cm: String
    val customUnitFallback: String
    val modeTotal: String
    val modePerSide: String
    val modeCounterweight: String
    val perSideShared: String

    fun sets(n: Int): String

    fun machines(n: Int): String

    fun members(n: Int): String

    fun daysAgo(days: Int): String

    /** [month] 1..12, as in "12 ноября". */
    fun dayMonth(
        day: Int,
        month: Int,
    ): String

    fun monthTitle(month: Int): String

    /** [dayOfWeek] 1 (Monday)..7. */
    fun weekday(dayOfWeek: Int): String

    fun weekdayShort(dayOfWeek: Int): String

    fun weekdayShared(dayOfWeek: Int): String

    // Accounts and sign-in
    val recordingAs: String
    val addAccount: String

    fun signOutOf(name: String): String

    val signInFailed: String
    val signInWithGoogle: String
    val signInUnavailable: String

    // Home
    val visits: String
    val machinesSection: String
    val measurements: String
    val plans: String
    val statistics: String
    val friends: String

    fun version(name: String): String

    val todayTitle: String
    val noSetsYet: String
    val startVisit: String
    val continueVisit: String
    val locked: String

    // Visit
    val visit: String

    val share: String
    val groupByTag: String
    val untaggedSection: String
    val planned: String
    val unplan: String
    val noPlansYet: String
    val newPlan: String
    val startPlan: String
    val untitledPlan: String
    val plan: String
    val planName: String
    val addMachine: String
    val savePlan: String
    val deletePlan: String
    val deletePlanTitle: String
    val deletePlanText: String
    val startPlanTitle: String
    val startPlanText: String
    val saveAsPlan: String
    val reps: String
    val comment: String
    val deleteSet: String
    val saveSet: String

    val newSet: String

    fun editOf(set: String): String

    val record: String

    // Calendar
    val addVisit: String
    val replace: String
    val chooseNewDay: String
    val move: String
    val deleteVisitTitle: String
    val replaceVisitTitle: String

    fun replaceVisitText(
        date: String,
        sets: String,
    ): String

    val previousMonth: String
    val nextMonth: String

    // Machines
    val machine: String
    val machines: String
    val noMachinesYet: String
    val friendsMachines: String
    val myMachines: String
    val friendMachine: String
    val setupNote: String
    val howWeightCounts: String
    val platformWeight: String
    val takeForMyself: String
    val platformAdded: String
    val platformBeside: String
    val linkTo: String
    val linkToMine: String
    val suggestedLinks: String
    val suggestedLink: String
    val merge: String
    val mergeTitle: String

    fun mergeText(
        kept: String,
        removed: String,
    ): String

    val mergeKeep: String
    val suggestedKeep: String

    fun lastSetOn(day: String): String

    fun mergeAdjust(
        removed: String,
        kept: String,
        shift: String,
    ): String

    val recalculateTitle: String

    fun recalculateText(
        sets: String,
        shift: String,
    ): String

    /** [shift] is the platform's change in the new unit, when there is one. */
    fun recalculateUnitText(
        sets: String,
        from: String,
        to: String,
        shift: String?,
    ): String

    fun mergeConvert(
        removed: String,
        from: String,
        to: String,
    ): String

    val recalculate: String
    val keepAsRecorded: String

    val unitName: String
    val unlinkTitle: String
    val unlinkText: String
    val unlink: String
    val newTag: String
    val choiceTotal: String
    val choicePerSide: String
    val choiceCounterweight: String
    val counterweightHint: String
    val addToRecord: String
    val platformIncludedHint: String
    val platformApartHint: String
    val customUnit: String
    val weightStep: String
    val unlinkFromFriends: String
    val basedOnExisting: String
    val photoNoteAndSetup: String
    val copyMachine: String
    val copyKeeps: String
    val recent: String
    val similar: String

    fun createNamed(name: String): String

    fun withTags(tags: List<String>): String

    val nothingFound: String
    val alreadyInVisit: String
    val overallStats: String
    val statsMonth: String
    val statsThreeMonths: String
    val statsSixMonths: String
    val statsYear: String
    val allResults: String
    val noSetsInPeriod: String
    val machinesInPeriod: String
    val startSet: String
    val best: String
    val noChange: String
    val clearSearch: String
    val copySettings: String
    val newMachine: String
    val improvementsOnly: String
    val noImprovements: String
    val export: String

    /** "за месяц", the period an export covers. */
    fun forPeriod(months: Int): String

    fun sinceDay(day: String): String

    fun beforeDay(day: String): String

    fun moreReps(n: Int): String

    fun fewerReps(n: Int): String

    val makeCover: String
    val coverPhoto: String
    val sortName: String
    val sortFrequent: String
    val sortGrowth: String

    // Photos
    val addPhoto: String
    val takePhoto: String
    val fromGallery: String
    val googlePictureReturns: String

    // Friends
    val joinTitle: String
    val joinText: String
    val join: String
    val inviteNotFound: String
    val understood: String
    val group: String
    val membersSection: String

    fun inviteCode(code: String): String

    val invite: String
    val deleteGroup: String
    val leaveGroup: String
    val owner: String

    fun colorName(n: Int): String

    val calendarColor: String
    val noGroupsYet: String
    val createGroup: String
    val joinByCode: String
    val newGroup: String
    val joinGroup: String
    val deleteGroupTitle: String
    val deleteGroupText: String
    val leaveGroupTitle: String
    val leaveGroupText: String
    val leave: String

    fun inviteMessage(group: String): String

    fun codeLine(code: String): String

    val linkCopied: String
    val copied: String
    val copyFailed: String

    // Family
    val children: String
    val noChildren: String
    val addChild: String

    fun childCode(code: String): String

    val childCodeHint: String
    val removeLink: String
    val removeChildTitle: String
    val removeChildText: String
    val guardianInviteMessage: String
    val guardians: String
    val noGuardians: String
    val addGuardian: String
    val guardianCode: String
    val guardianRights: String
    val guardianCodeUnknown: String
    val ownGuardianCode: String
    val removeGuardianTitle: String
    val removeGuardianText: String
    val guardianInviteTitle: String
    val childAccount: String

    // Measures
    val measurement: String
    val deleteMeasurement: String
    val deleteMeasurementTitle: String

    fun deleteMeasurementText(day: String): String

    val periodMonth: String
    val periodQuarter: String
    val periodHalfYear: String
    val periodYear: String
    val periodAll: String
    val history: String
    val editHistory: String
    val deleteMeasureTitle: String

    fun deleteMeasureText(name: String): String

    val noValues: String
    val noValuesInPeriod: String
    val nameAndUnit: String
    val deleteMeasure: String
    val measure: String
    val measuresSection: String
    val newMeasurement: String
    val addMeasure: String
    val bodyFat: String
    val cannotCalculate: String
    val fillProfile: String
    val newMeasure: String

    fun needs(inputs: List<String>): String

    val navy: String
    val deurenberg: String
    val inputSex: String
    val inputAge: String
    val inputHeight: String
    val inputWeight: String
    val inputWaist: String
    val inputNeck: String
    val inputHips: String
    val measureWeight: String
    val measureWaist: String
    val measureChest: String
    val measureHips: String
    val measureBiceps: String
    val measureThigh: String
    val measureNeck: String
    val hintWeight: String
    val hintWaist: String
    val hintChest: String
    val hintHips: String
    val hintBiceps: String
    val hintThigh: String
    val hintNeck: String

    // Settings
    val profile: String
    val nickname: String
    val sex: String
    val male: String
    val female: String
    val birthDate: String
    val datePlaceholder: String
    val heightCm: String
    val bodyFieldsHint: String
    val weightUnits: String
    val mixedUnits: String
    val mixedUnitsHint: String
    val theme: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val language: String
    val languageSystem: String
    val transitions: String

    fun transitionsHint(max: Int): String

    val apply: String
    val applyTitle: String
    val discard: String
    val advanced: String
    val deleteAccount: String
    val deleteAccountTitle: String

    fun deleteAccountText(word: String): String
}
