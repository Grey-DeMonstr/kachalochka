package monster.greyde.kachalochka.ui.strings

object EnStrings : Strings {
    override val appName = "Kachalochka"
    override val back = "Back"
    override val cancel = "Cancel"
    override val delete = "Delete"
    override val save = "Save"
    override val add = "Add"
    override val create = "Create"
    override val more = "More"
    override val done = "Done"
    override val reorder = "Reorder"
    override val drag = "Drag"
    override val offline = "No connection to the server"
    override val retry = "Retry"
    override val loading = "Loading…"
    override val settings = "Settings"
    override val name = "Name"
    override val unitExample = "Unit, e.g. cm"
    override val noVisit = "No visit"
    override val photo = "Photo"

    override val kg = "kg"
    override val cm = "cm"
    override val customUnitFallback = "u."
    override val modeTotal = "total"
    override val modePerSide = "per side"
    override val modeCounterweight = "gravitron"
    override val perSideShared = " each side,"

    override fun sets(n: Int) = "$n ${if (n == 1) "set" else "sets"}"

    override fun machines(n: Int) = "$n ${if (n == 1) "machine" else "machines"}"

    override fun members(n: Int) = "$n ${if (n == 1) "member" else "members"}"

    override fun daysAgo(days: Int) =
        when (days) {
            0 -> "today"
            1 -> "yesterday"
            else -> "$days days ago"
        }

    private val months =
        listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )

    override fun dayMonth(
        day: Int,
        month: Int,
    ) = "$day ${months[month - 1]}"

    override fun monthTitle(month: Int) = months[month - 1]

    private val weekdays =
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    override fun weekday(dayOfWeek: Int) = weekdays[dayOfWeek - 1]

    override fun weekdayShort(dayOfWeek: Int) = weekday(dayOfWeek).take(2)

    override fun weekdayShared(dayOfWeek: Int) = weekday(dayOfWeek).take(3)

    override val recordingAs = "Recording sets as"
    override val addAccount = "Add account"

    override fun signOutOf(name: String) = "Sign out of $name"

    override val signInFailed = "Couldn't sign in. Please try again"
    override val signInWithGoogle = "Sign in with Google"
    override val signInUnavailable = "Sign-in is unavailable: this build is not configured"

    override val visits = "Visits"
    override val machinesSection = "Machines"
    override val measurements = "Measurements"
    override val plans = "Plans"
    override val statistics = "Statistics"
    override val friends = "Friends"

    override fun version(name: String) = "Version $name"

    override val todayTitle = "Today"
    override val noSetsYet = "No sets yet"

    override val startVisit = "Start"
    override val continueVisit = "Continue"
    override val locked = "Locked"

    override val visit = "Visit"

    override val share = "Share"
    override val groupByTag = "Group by tag"
    override val planned = "Planned"
    override val unplan = "Remove"
    override val noPlansYet = "No plans yet"
    override val newPlan = "New plan"
    override val startPlan = "Start"
    override val untitledPlan = "Untitled"
    override val plan = "Plan"
    override val planName = "Name"
    override val addMachine = "Add a machine"
    override val savePlan = "Save plan"
    override val deletePlan = "Delete plan"
    override val deletePlanTitle = "Delete the plan?"
    override val deletePlanText = "The plan will disappear from the list."
    override val reps = "reps"
    override val comment = "Comment"
    override val deleteSet = "Delete set"
    override val saveSet = "Add"

    override fun saveAs(person: String) = "Add · $person"

    override val newSet = "New set"

    override fun editOf(set: String) = "Edit: $set"

    override val record = "Record"

    override val addVisit = "Add visit"
    override val replace = "Replace"
    override val chooseNewDay = "Choose the new day"
    override val move = "Move"
    override val deleteVisitTitle = "Delete the visit?"
    override val replaceVisitTitle = "Replace the visit?"

    override fun replaceVisitText(
        date: String,
        sets: String,
    ) = "$date already has a visit: $sets. It and its sets will disappear from the history " +
        "and statistics."

    override val previousMonth = "Previous month"
    override val nextMonth = "Next month"

    override val machine = "Machine"
    override val machines = "Machines"
    override val noMachinesYet = "No machines yet"
    override val friendsMachines = "Friends' machines"
    override val myMachines = "My machines"
    override val friendMachine = "Friend's machine"
    override val setupNote = "Comment"
    override val howWeightCounts = "How the weight counts"
    override val platformWeight = "Platform weight"
    override val takeForMyself = "Take for myself"
    override val platformAdded = "added to the record"
    override val platformBeside = "shown beside the name"
    override val linkTo = "Link to…"
    override val merge = "Merge"
    override val mergeTitle = "Merge the machines?"

    override fun mergeText(
        kept: String,
        removed: String,
    ) = "\"$kept\" stays and the sets of \"$removed\" move to it. This cannot be undone."

    override val unitName = "Unit name"
    override val unlinkTitle = "Unlink the machine?"
    override val unlinkText = "Friends' results on this machine will no longer show for you."
    override val unlink = "Unlink"
    override val linkedWith = "Linked with: "
    override val tags = "Tags"
    override val newTag = "New tag"
    override val friendsTags = "Friends' tags — become yours once chosen"
    override val choiceTotal = "Total"
    override val choicePerSide = "Per side"
    override val choiceCounterweight = "Gravitron"
    override val counterweightHint = "The weight counts as negative: the less, the better."
    override val addToRecord = "Add to the record"
    override val platformIncludedHint = "On: the platform weight is part of every record."
    override val platformApartHint =
        "Off: only the added weight is recorded, and the platform stands beside the name — " +
            "\"Leg press (+25 kg) 70 kg × 10\"."
    override val customUnit = "Own unit"
    override val weightStep = "Weight step"
    override val afterSaveHint = "Once saved, the machine appears in this visit."
    override val unlinkFromFriends = "Unlink from friends"
    override val basedOnExisting = "Based on an existing one"
    override val photoNoteAndSetup = "Photos, comment and weight setup"
    override val copyMachine = "Copy machine"
    override val copyKeeps = "The note and weight setup are kept"
    override val recent = "Recent"
    override val similar = "Similar"

    override fun createNamed(name: String) = "Create \"$name\""

    override fun withTags(tags: List<String>) = "Tagged " + tags.joinToString(", ") { "\"$it\"" }

    override val nothingFound = "Nothing found"
    override val alreadyInVisit = "Already in the visit"
    override val overallStats = "Overall"
    override val statsMonth = "Month"
    override val statsThreeMonths = "3 months"
    override val statsSixMonths = "6 months"
    override val statsYear = "Year"
    override val bestSetTitle = "Best set"
    override val counterweightTitle = "counterweight"
    override val allResults = "All results"
    override val noSetsInPeriod = "No sets in this period"
    override val machinesInPeriod = "Machines in the period"
    override val worst = "Worst"
    override val best = "Best"
    override val noChange = "No change"

    override fun sinceDay(day: String) = "since $day"

    override fun beforeDay(day: String) = "Before $day"

    override fun moreReps(n: Int) = "+$n reps"

    override fun fewerReps(n: Int) = "−$n reps"

    override val makeCover = "Make it the cover"
    override val coverPhoto = "Cover photo"
    override val sortName = "A–Z"
    override val sortFrequent = "Most used"

    override val addPhoto = "Add photo"
    override val takePhoto = "Take a photo"
    override val fromGallery = "From the gallery"
    override val googlePictureReturns = "Your Google picture comes back"

    override val joinTitle = "Join the group from the invitation?"
    override val joinText = "Group members will see your visits and machines."
    override val join = "Join"
    override val inviteNotFound = "Invitation not found"
    override val understood = "OK"
    override val group = "Group"
    override val membersSection = "Members"

    override fun inviteCode(code: String) = "Invite code: $code"

    override val invite = "Invite"
    override val deleteGroup = "Delete group"
    override val leaveGroup = "Leave group"
    override val owner = "owner"

    override fun colorName(n: Int) = "Colour $n"

    override val calendarColor = "Calendar colour"
    override val noGroupsYet = "No groups yet"
    override val createGroup = "Create group"
    override val joinByCode = "Join by code"
    override val newGroup = "New group"
    override val joinGroup = "Join a group"
    override val deleteGroupTitle = "Delete the group?"
    override val deleteGroupText = "Members will stop seeing each other's visits."
    override val leaveGroupTitle = "Leave the group?"
    override val leaveGroupText = "You will stop seeing the members' visits, and they yours."
    override val leave = "Leave"

    override fun inviteMessage(group: String) = "Join the group \"$group\" in Kachalochka"

    override fun codeLine(code: String) = "Code: $code"

    override val linkCopied = "Link copied"
    override val copied = "Copied"
    override val copyFailed = "Couldn't copy"

    override val measurement = "Measurement"
    override val deleteMeasurement = "Delete measurement"
    override val deleteMeasurementTitle = "Delete the measurement?"

    override fun deleteMeasurementText(day: String) =
        "$day: every value of this day will disappear."

    override val periodMonth = "1 mo"
    override val periodQuarter = "3 mo"
    override val periodHalfYear = "6 mo"
    override val periodYear = "Year"
    override val periodAll = "All"
    override val history = "History"
    override val editHistory = "Edit history"
    override val deleteMeasureTitle = "Delete the measure?"

    override fun deleteMeasureText(name: String) = "\"$name\" and all its values will disappear."

    override val noValues = "No values"
    override val noValuesInPeriod = "No values in this period"
    override val nameAndUnit = "Name and unit"
    override val deleteMeasure = "Delete measure"
    override val measure = "Measure"
    override val measuresSection = "Measures"
    override val newMeasurement = "New measurement"
    override val addMeasure = "Add measure"
    override val bodyFat = "Body fat"
    override val cannotCalculate = "Can't be calculated from these values"
    override val fillProfile = "Set your sex, birth date and height in Settings"
    override val newMeasure = "New measure"

    override fun needs(inputs: List<String>) = "Needs: ${inputs.joinToString(", ")}"

    override val navy = "US Navy"
    override val deurenberg = "Deurenberg"
    override val inputSex = "sex"
    override val inputAge = "birth date"
    override val inputHeight = "height"
    override val inputWeight = "weight"
    override val inputWaist = "waist"
    override val inputNeck = "neck"
    override val inputHips = "hips"
    override val measureWeight = "Weight"
    override val measureWaist = "Waist"
    override val measureChest = "Chest"
    override val measureHips = "Hips"
    override val measureBiceps = "Biceps"
    override val measureThigh = "Thigh"
    override val measureNeck = "Neck"
    override val hintWeight = "In the morning before eating, after the toilet, without clothes."
    override val hintWaist =
        "Tape level: for men at the navel, for women at the narrowest point. On a relaxed " +
            "breath out, without pulling the stomach in."
    override val hintChest =
        "Tape level across the fullest part of the chest and under the shoulder blades, arms " +
            "down, on a relaxed breath out."
    override val hintHips = "Feet together, tape level across the fullest part of the buttocks."
    override val hintBiceps = "Arm bent and flexed, tape across the highest point of the biceps."
    override val hintThigh =
        "Standing, weight on both legs, tape level just below the buttock crease."
    override val hintNeck =
        "Just below the Adam's apple, tape tilted slightly forward and down, neck relaxed."

    override val profile = "Profile"
    override val nickname = "Nickname"
    override val sex = "Sex"
    override val male = "Male"
    override val female = "Female"
    override val birthDate = "Birth date"
    override val datePlaceholder = "DD.MM.YYYY"
    override val heightCm = "Height, cm"
    override val bodyFieldsHint = "Sex, birth date and height are needed to estimate body fat."
    override val weightUnits = "Weight units"
    override val mixedUnits = "Mixed"
    override val mixedUnitsHint = "Mixed: every machine keeps its own unit."
    override val theme = "Theme"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val language = "Language"
    override val languageSystem = "System"
    override val transitions = "Screen transitions"

    override fun transitionsHint(max: Int) = "Milliseconds, up to $max; 0 switches instantly"

    override val apply = "Apply"
    override val applyTitle = "Apply the changes?"
    override val discard = "Don't apply"
    override val advanced = "Advanced"
    override val deleteAccount = "Delete account"
    override val deleteAccountTitle = "Delete the account?"

    override fun deleteAccountText(word: String) =
        "Your visits, machines with their photos, measurements, profile and groups will be " +
            "deleted from the server and from this device. This cannot be undone. To confirm, " +
            "type $word."
}
