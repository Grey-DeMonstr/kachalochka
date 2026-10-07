package monster.greyde.kachalochka.ui.machine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.VisitId
import monster.greyde.kachalochka.core.domain.gym.WeightMode
import monster.greyde.kachalochka.core.domain.gym.WeightUnit
import monster.greyde.kachalochka.core.domain.gym.WorkoutSet
import monster.greyde.kachalochka.core.domain.gym.WorkoutSetId
import monster.greyde.kachalochka.core.domain.gym.linkedCopy
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.ME
import monster.greyde.kachalochka.ui.friends.OLEG
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MachineFormViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша")
    private val ivanFriend = Friend(ivan.account.userId, "Иван")
    private val mishaFriend = Friend(misha.account.userId, "Миша")

    private fun session(
        id: String,
        name: String,
    ) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

    private fun viewModel(args: MachineFormArgs) =
        MachineFormViewModel(
            args,
            gym.machines,
            gym.currentUser,
            gym.accounts,
            gym.clock,
            gym.friends,
            gym.machineLinks,
            gym.sync,
            gym.photos,
            gym.catalogue,
            gym.sets,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_form_offers_every_tag_of_the_account_s_machines_sorted() =
        runTest {
            gym.machines.upsert(Machine.new("Жим", null, t0).copy(tags = setOf("руки", "Грудь")))
            gym.machines.upsert(Machine.new("Присед", null, t0).copy(tags = setOf("Ноги")))
            gym.machines.upsert(
                Machine.new("Старый", null, t0).copy(tags = setOf("Спина"), deleted = true),
            )

            val vm = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }

            assertEquals(listOf("Грудь", "Ноги", "руки"), vm.state.value.shownTags)
            assertEquals(emptySet(), vm.state.value.tags)
        }

    @Test
    fun a_machine_created_from_a_tag_filter_starts_with_its_tags() =
        runTest {
            val vm =
                viewModel(MachineFormArgs(null, null, "Гакк", listOf("Руки"))).also { it.load() }
            var saved: MachineId? = null

            vm.save { saved = it }

            assertEquals(setOf("Руки"), gym.machines.byId(assertNotNull(saved))?.tags)
        }

    private fun formOn(
        on: FakeGym,
        args: MachineFormArgs,
    ) = MachineFormViewModel(
        args,
        on.machines,
        on.currentUser,
        on.accounts,
        on.clock,
        on.friends,
        on.machineLinks,
        on.sync,
        on.photos,
        on.catalogue,
        on.sets,
    )

    @Test
    fun friends_tags_the_account_lacks_are_offered_and_one_chosen_becomes_its_own() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            on.friends.machines +=
                Machine.new("Жим", OLEG.userId, t0).copy(tags = setOf("Плечи", "ноги"))
            on.machines.upsert(Machine.new("Присед", ME.userId, t0).copy(tags = setOf("Ноги")))
            val vm = formOn(on, MachineFormArgs(null, null, "Гакк")).also { it.load() }

            assertEquals(
                listOf("Плечи" to OLEG.userId),
                vm.state.value.offeredFriendTags
                    .map { it.tag to it.friend.userId },
            )

            vm.toggleTag("Плечи")
            var saved: MachineId? = null
            vm.save { saved = it }

            assertEquals(setOf("Плечи"), on.machines.byId(assertNotNull(saved))?.tags)
            assertEquals(emptyList(), vm.state.value.offeredFriendTags)
        }

    @Test
    fun chosen_and_added_tags_are_saved_with_the_machine() =
        runTest {
            gym.machines.upsert(Machine.new("Присед", null, t0).copy(tags = setOf("Ноги")))
            val vm = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }

            vm.toggleTag("Ноги")
            vm.typeNewTag("  Жим  ")
            vm.addNewTag()
            vm.typeNewTag("   ")
            vm.addNewTag()
            var saved: MachineId? = null
            vm.save { saved = it }

            assertEquals(setOf("Ноги", "Жим"), gym.machines.byId(assertNotNull(saved))?.tags)
            assertEquals(listOf("Жим", "Ноги"), vm.state.value.shownTags)
            assertEquals("", vm.state.value.newTag)
        }

    @Test
    fun a_typed_tag_that_exists_in_another_case_picks_the_existing_one() =
        runTest {
            gym.machines.upsert(Machine.new("Присед", null, t0).copy(tags = setOf("Ноги")))
            val vm = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }

            vm.typeNewTag(" ноги ")
            vm.addNewTag()

            assertEquals(setOf("Ноги"), vm.state.value.tags)
            assertEquals(listOf("Ноги"), vm.state.value.shownTags)
        }

    @Test
    fun a_saved_machine_opens_with_its_tags_chosen_and_a_tap_removes_one() =
        runTest {
            val press = Machine.new("Жим", null, t0).copy(tags = setOf("Грудь", "Руки"))
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(setOf("Грудь", "Руки"), vm.state.value.tags)
            vm.toggleTag("Руки")
            vm.save {}

            assertEquals(setOf("Грудь"), gym.machines.byId(press.id)?.tags)
        }

    @Test
    fun photos_taken_in_the_form_are_written_with_the_machine_it_saves() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }

            vm.addPhoto(byteArrayOf(7))
            gym.clock.current = t0 + 1.minutes
            vm.addPhoto(byteArrayOf(8))

            assertEquals(2, vm.photos.value.size)
            assertTrue(gym.photos.rows.isEmpty())
            var saved: MachineId? = null
            vm.save { saved = it }
            val photos = gym.photos.forMachine(assertNotNull(saved))
            assertEquals(2, photos.size)
            assertEquals(listOf(7.toByte()), gym.photos.bytes[photos.first().id]?.toList())
            assertEquals(t0, photos.first().takenAt)
            assertEquals(t0 + 1.minutes, photos.first().updatedAt)
        }

    @Test
    fun a_saved_machine_shows_its_photos_and_a_removal_waits_for_the_save() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0)
            val photo = Photo.new(press.id, null, t0)
            gym.machines.upsert(press)
            gym.photos.add(photo, byteArrayOf(1))
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            assertEquals(listOf(photo.id.value), vm.photos.value.map { it.key })

            vm.removePhoto(photo.id.value)

            assertEquals(emptyList(), vm.photos.value)
            assertEquals(listOf(photo), gym.photos.forMachine(press.id))
            gym.clock.current = t0 + 1.minutes
            vm.save {}
            assertEquals(emptyList(), gym.photos.forMachine(press.id))
            assertEquals(
                photo.copy(deleted = true, updatedAt = t0 + 1.minutes),
                gym.photos.rows[photo.id],
            )
        }

    /** Иван's press, linked to Миша's; each has a photo, Миша's the older. */
    private suspend fun pressWithFriendsPhoto(): Triple<Machine, Photo, Photo> {
        gym.withAccounts(ivan, active = ivan)
        gym.friends.group("Зал на Лесной", owner = mishaFriend, ivanFriend)
        val hers = Machine.new("Жим ногами", misha.account.userId, t0)
        val (press, link) = linkedCopy(hers, ivan.account.userId, t0)
        gym.machines.upsert(press)
        gym.machineLinks.upsert(link)
        gym.friends.machines += hers
        val mine = Photo.new(press.id, ivan.account.userId, t0)
        gym.photos.add(mine, byteArrayOf(1))
        val theirs = Photo.new(hers.id, misha.account.userId, t0 - 1.minutes)
        gym.friends.photos += theirs
        return Triple(press, mine, theirs)
    }

    @Test
    fun friends_photos_of_the_machine_follow_its_own_with_their_owner() =
        runTest {
            val (press, mine, theirs) = pressWithFriendsPhoto()

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(
                listOf(mine.id.value to null, theirs.id.value to "Миша"),
                vm.photos.value.map { it.key to it.owner?.displayName },
            )
            assertEquals(listOf(true, false), vm.photos.value.map { it.cover })
        }

    @Test
    fun a_friend_s_photo_made_the_cover_is_saved_with_the_machine() =
        runTest {
            val (press, _, theirs) = pressWithFriendsPhoto()
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            vm.makeCover(theirs.id.value)

            assertEquals(listOf(false, true), vm.photos.value.map { it.cover })
            assertEquals(null, gym.machines.byId(press.id)?.coverPhoto)
            vm.save {}
            assertEquals(theirs.id, gym.machines.byId(press.id)?.coverPhoto)
        }

    @Test
    fun a_friend_s_photo_cannot_be_removed() =
        runTest {
            val (press, _, theirs) = pressWithFriendsPhoto()
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            vm.removePhoto(theirs.id.value)

            assertEquals(2, vm.photos.value.size)
        }

    @Test
    fun offline_a_friend_s_photo_chosen_before_stays_the_cover() =
        runTest {
            val (press, _, theirs) = pressWithFriendsPhoto()
            gym.machines.upsert(press.copy(coverPhoto = theirs.id))
            gym.friends.offline = true
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            vm.save {}

            assertEquals(theirs.id, gym.machines.byId(press.id)?.coverPhoto)
        }

    @Test
    fun removing_the_chosen_cover_leaves_the_machine_its_usual_one() =
        runTest {
            val (press, mine, _) = pressWithFriendsPhoto()
            val later = Photo.new(press.id, ivan.account.userId, t0 + 1.minutes)
            gym.photos.add(later, byteArrayOf(2))
            gym.machines.upsert(press.copy(coverPhoto = later.id))
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            assertEquals(
                later.id.value,
                vm.photos.value
                    .single { it.cover }
                    .key,
            )

            vm.removePhoto(later.id.value)
            vm.save {}

            assertEquals(
                mine.id.value,
                vm.photos.value
                    .single { it.cover }
                    .key,
            )
            assertEquals(null, gym.machines.byId(press.id)?.coverPhoto)
        }

    @Test
    fun a_copied_machine_starts_without_the_source_s_photos() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0)
            gym.machines.upsert(press)
            gym.photos.add(Photo.new(press.id, null, t0), byteArrayOf(1))

            val vm = viewModel(MachineFormArgs(null, press.id, "Жим ногами 2")).also { it.load() }

            assertEquals(emptyList(), vm.photos.value)
        }

    @Test
    fun a_switch_drops_the_other_account_s_photos_and_keeps_the_ones_just_taken() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val press = Machine.new("Жим ногами", ivan.account.userId, t0)
            gym.machines.upsert(press)
            gym.photos.add(Photo.new(press.id, ivan.account.userId, t0), byteArrayOf(1))
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            vm.addPhoto(byteArrayOf(2))

            gym.accounts.switchTo(misha.account.userId)

            assertEquals(1, vm.photos.value.size)
            var saved: MachineId? = null
            vm.save { saved = it }
            val taken = gym.photos.forMachine(assertNotNull(saved)).single()
            assertEquals(misha.account.userId, taken.userId)
        }

    @Test
    fun a_new_machine_starts_from_the_typed_name_and_the_defaults() {
        val vm = viewModel(MachineFormArgs(null, null, "гакк")).also { it.load() }

        assertEquals(MachineFormState(name = "гакк"), vm.state.value)
        assertEquals(true, vm.state.value.canSave)
    }

    @Test
    fun loading_again_keeps_the_unsaved_edits() {
        val vm = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }
        vm.update { it.copy(setupNote = "Упоры на 3") }

        vm.load()

        assertEquals("Упоры на 3", vm.state.value.setupNote)
    }

    @Test
    fun a_blank_name_or_a_bad_platform_weight_cannot_be_saved() {
        assertEquals(false, MachineFormState(name = " ").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", platformWeight = "-5").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", platformWeight = "abc").canSave)
        assertEquals(25.5, MachineFormState(platformWeight = "25.5").platformWeightValue)
        assertEquals(0.0, MachineFormState(platformWeight = "").platformWeightValue)
    }

    @Test
    fun saving_a_new_machine_stores_every_field() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Гакк-машина")).also { it.load() }
            vm.update {
                it.copy(
                    setupNote = "Упоры на 3",
                    weightMode = WeightMode.PerSide,
                    platformWeight = "25",
                    platformIncluded = true,
                    unit = WeightUnit.Lb,
                    weightStep = "5",
                )
            }
            var saved: MachineId? = null

            vm.save { saved = it }

            val machine = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertEquals(
                listOf(
                    "Гакк-машина",
                    "Упоры на 3",
                    WeightMode.PerSide,
                    25.0,
                    true,
                    WeightUnit.Lb,
                    5.0,
                ),
                listOf(
                    machine.name,
                    machine.setupNote,
                    machine.weightMode,
                    machine.platformWeight,
                    machine.platformIncluded,
                    machine.unit,
                    machine.weightStep,
                ),
            )
        }

    @Test
    fun a_copy_keeps_the_settings_under_a_new_id_and_name() =
        runTest {
            val source =
                Machine
                    .new(
                        "Жим ногами",
                        null,
                        t0,
                    ).copy(setupNote = "Сиденье на 4", platformWeight = 20.0)
            gym.machines.upsert(source)
            val vm =
                viewModel(
                    MachineFormArgs(null, source.id, "Жим одной ногой"),
                ).also { it.load() }
            var saved: MachineId? = null

            vm.save { saved = it }

            val copy = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertNotEquals(source.id, copy.id)
            assertEquals("Жим одной ногой", copy.name)
            assertEquals("Сиденье на 4", copy.setupNote)
            assertEquals(20.0, copy.platformWeight)
        }

    @Test
    fun editing_an_existing_machine_keeps_its_id() =
        runTest {
            val source = Machine.new("Жим ногами", null, t0)
            gym.machines.upsert(source)
            gym.clock.current += 1.minutes
            val vm = viewModel(MachineFormArgs(source.id, null, "")).also { it.load() }
            assertEquals("Жим ногами", vm.state.value.name)

            vm.update { it.copy(weightStep = "10") }
            vm.save {}

            val saved = assertNotNull(gym.machines.byId(source.id))
            assertEquals(10.0, saved.weightStep)
            assertEquals(gym.clock.current, saved.updatedAt)
            assertEquals(1, gym.machines.all(null).size)
        }

    @Test
    fun a_friend_s_settings_copied_into_the_form_ask_to_recalculate_on_save() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs =
                Machine.new("Жим", OLEG.userId, t0).copy(
                    weightMode = WeightMode.PerSide,
                    platformWeight = 20.0,
                    weightStep = 5.0,
                )
            on.friends.machines += olegs
            val mine = Machine.new("Жим ногами", ME.userId, t0)
            on.machines.upsert(mine)
            on.sets.upsert(
                WorkoutSet(
                    WorkoutSetId.random(),
                    ME.userId,
                    VisitId.random(),
                    mine.id,
                    70.0,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                ),
            )
            val vm = formOn(on, MachineFormArgs(mine.id, null, "")).also { it.load() }

            vm.copySettings(olegs.id)

            val form = vm.state.value
            assertEquals(WeightMode.PerSide to "20", form.weightMode to form.platformWeight)
            assertEquals("5", form.weightStep)
            vm.save {}
            assertNotNull(vm.recalculation.value)
        }

    @Test
    fun copied_settings_take_the_friend_s_name_and_add_its_tags() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs =
                Machine.new("Жим платформой", OLEG.userId, t0).copy(tags = setOf("ноги", "Жим"))
            on.friends.machines += olegs
            val mine = Machine.new("Жим ногами", ME.userId, t0).copy(tags = setOf("Ноги"))
            on.machines.upsert(mine)
            val vm = formOn(on, MachineFormArgs(mine.id, null, "")).also { it.load() }

            vm.copySettings(olegs.id)

            assertEquals("Жим платформой", vm.state.value.name)
            assertEquals(setOf("Ноги", "Жим"), vm.state.value.tags)
        }

    @Test
    fun a_friend_s_unit_is_copied_with_its_name() =
        runTest {
            val on = signedInGym()
            on.friends.group("Зал на Лесной", owner = OLEG, ME)
            val olegs =
                Machine
                    .new("Блок", OLEG.userId, t0)
                    .copy(unit = WeightUnit.Custom, unitLabel = "плитка", weightStep = 1.0)
            on.friends.machines += olegs
            val mine = Machine.new("Блок", ME.userId, t0)
            on.machines.upsert(mine)
            val vm = formOn(on, MachineFormArgs(mine.id, null, "")).also { it.load() }

            vm.copySettings(olegs.id)

            assertEquals(
                WeightUnit.Custom to "плитка",
                vm.state.value.unit to vm.state.value.unitLabel,
            )
        }

    /** A machine with imported totals and two sets, its platform then typed beside the name. */
    private suspend fun platformTyped(): Triple<MachineFormViewModel, Machine, List<WorkoutSet>> {
        val press = Machine.new("Жим ногами", null, t0)
        gym.machines.upsert(press)
        val recorded =
            listOf(70.0, 90.0).map { weight ->
                val visit = VisitId.random()
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    visit,
                    press.id,
                    weight,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                )
            }
        recorded.forEach { gym.sets.upsert(it) }
        gym.clock.current += 1.minutes
        val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
        vm.update { it.copy(platformWeight = "25") }
        return Triple(vm, press, recorded)
    }

    @Test
    fun a_platform_change_over_recorded_sets_asks_to_recalculate_them() =
        runTest {
            val (vm, press, recorded) = platformTyped()
            var saved: MachineId? = null

            vm.save { saved = it }

            assertEquals(
                RecalculationUi(
                    "Платформа изменилась. Изменить вес записанных подходов (2 подхода) на " +
                        "−25 кг, чтобы итоговый вес остался прежним?",
                ),
                vm.recalculation.value,
            )
            assertEquals(0.0, gym.machines.byId(press.id)?.platformWeight)

            vm.recalculate()

            assertEquals(press.id, saved)
            assertNull(vm.recalculation.value)
            assertEquals(25.0, gym.machines.byId(press.id)?.platformWeight)
            assertEquals(
                recorded.map { it.copy(weight = it.weight - 25, updatedAt = gym.clock.current) },
                recorded.map { gym.sets.rows.getValue(it.id) },
            )
        }

    @Test
    fun the_recorded_sets_can_stay_as_they_are() =
        runTest {
            val (vm, press, recorded) = platformTyped()
            var saved: MachineId? = null

            vm.save { saved = it }
            vm.keepRecorded()

            assertEquals(press.id, saved)
            assertEquals(25.0, gym.machines.byId(press.id)?.platformWeight)
            assertEquals(recorded, recorded.map { gym.sets.rows.getValue(it.id) })
        }

    @Test
    fun a_dismissed_recalculation_writes_nothing() =
        runTest {
            val (vm, press, _) = platformTyped()
            var saved: MachineId? = null

            vm.save { saved = it }
            vm.dismissRecalculation()

            assertNull(saved)
            assertNull(vm.recalculation.value)
            assertEquals(0.0, gym.machines.byId(press.id)?.platformWeight)
        }

    @Test
    fun a_platform_added_to_the_record_saves_at_once() =
        runTest {
            val (vm, press, _) = platformTyped()
            vm.update { it.copy(platformIncluded = true) }
            var saved: MachineId? = null

            vm.save { saved = it }

            assertEquals(press.id, saved)
            assertNull(vm.recalculation.value)
        }

    /** A kilogram machine with a 20 kg platform and sets read off a 5 lb stack. */
    private suspend fun kgMachine(): Pair<MachineFormViewModel, List<WorkoutSet>> {
        val press =
            Machine.new("Жим ногами", null, t0).copy(platformWeight = 20.0, weightStep = 2.5)
        gym.machines.upsert(press)
        val recorded =
            listOf(22.7, 27.2).map { weight ->
                WorkoutSet(
                    WorkoutSetId.random(),
                    null,
                    VisitId.random(),
                    press.id,
                    weight,
                    10,
                    0,
                    t0,
                    t0,
                    false,
                )
            }
        recorded.forEach { gym.sets.upsert(it) }
        gym.clock.current += 1.minutes
        return viewModel(MachineFormArgs(press.id, null, "")).also { it.load() } to recorded
    }

    @Test
    fun choosing_pounds_converts_the_platform_and_guesses_the_step_from_the_sets() =
        runTest {
            val (vm, _) = kgMachine()

            vm.chooseUnit(WeightUnit.Lb)

            val form = vm.state.value
            assertEquals(
                Triple(WeightUnit.Lb, "45", "5"),
                Triple(form.unit, form.platformWeight, form.weightStep),
            )
        }

    @Test
    fun choosing_kilograms_converts_the_platform_and_the_step_to_a_tenth() =
        runTest {
            val press =
                Machine
                    .new("Жим ногами", null, t0)
                    .copy(unit = WeightUnit.Lb, platformWeight = 45.0, weightStep = 5.0)
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            vm.chooseUnit(WeightUnit.Kg)

            val form = vm.state.value
            assertEquals(
                Triple(WeightUnit.Kg, "20.4", "2.3"),
                Triple(form.unit, form.platformWeight, form.weightStep),
            )
        }

    @Test
    fun an_own_unit_keeps_the_numbers() =
        runTest {
            val (vm, _) = kgMachine()

            vm.chooseUnit(WeightUnit.Custom)

            val form = vm.state.value
            assertEquals(
                Triple(WeightUnit.Custom, "20", "2.5"),
                Triple(form.unit, form.platformWeight, form.weightStep),
            )
        }

    @Test
    fun a_unit_change_over_recorded_sets_offers_to_convert_them() =
        runTest {
            val (vm, recorded) = kgMachine()
            vm.chooseUnit(WeightUnit.Lb)

            vm.save {}

            assertEquals(
                RecalculationUi(
                    "Единица изменилась: кг → lb. Перевести вес записанных подходов " +
                        "(2 подхода) в новую единицу?",
                ),
                vm.recalculation.value,
            )

            vm.recalculate()

            assertEquals(
                recorded.zip(listOf(50.0, 60.0)).map { (set, weight) ->
                    set.copy(weight = weight, updatedAt = gym.clock.current)
                },
                recorded.map { gym.sets.rows.getValue(it.id) },
            )
        }

    @Test
    fun a_unit_and_platform_change_names_both_and_applies_both() =
        runTest {
            val (vm, recorded) = kgMachine()
            vm.chooseUnit(WeightUnit.Lb)
            vm.update { it.copy(platformWeight = "35") }

            vm.save {}

            assertEquals(
                RecalculationUi(
                    "Единица изменилась: кг → lb. Перевести вес записанных подходов " +
                        "(2 подхода) в новую единицу и изменить его на +10 lb под платформу?",
                ),
                vm.recalculation.value,
            )

            vm.recalculate()

            assertEquals(
                listOf(60.0, 70.0),
                recorded.map {
                    gym.sets.rows
                        .getValue(it.id)
                        .weight
                },
            )
        }

    @Test
    fun counting_per_side_offers_to_halve_the_recorded_sets() =
        runTest {
            val (vm, recorded) = kgMachine()
            vm.update { it.copy(weightMode = WeightMode.PerSide) }

            vm.save {}

            assertEquals(
                RecalculationUi(
                    "Вес теперь считается на сторону. Разделить вес записанных подходов " +
                        "(2 подхода) пополам?",
                ),
                vm.recalculation.value,
            )

            vm.recalculate()

            assertEquals(WeightMode.PerSide, gym.machines.byId(recorded[0].machineId)?.weightMode)
            assertEquals(
                recorded.zip(listOf(11.35, 13.6)).map { (set, weight) ->
                    set.copy(weight = weight, updatedAt = gym.clock.current)
                },
                recorded.map { gym.sets.rows.getValue(it.id) },
            )
        }

    @Test
    fun counting_in_total_again_offers_to_double_them_with_a_unit_change() =
        runTest {
            val (vm, recorded) = kgMachine()
            vm.update { it.copy(weightMode = WeightMode.PerSide) }
            vm.save {}
            vm.keepRecorded()
            val again =
                viewModel(
                    MachineFormArgs(recorded[0].machineId, null, ""),
                ).also { it.load() }
            again.update { it.copy(weightMode = WeightMode.Total) }
            again.chooseUnit(WeightUnit.Lb)

            again.save {}

            assertEquals(
                RecalculationUi(
                    "Вес теперь считается всего. Удвоить вес записанных подходов " +
                        "(2 подхода)?\n\nЕдиница изменилась: кг → lb. Перевести вес " +
                        "записанных подходов (2 подхода) в новую единицу?",
                ),
                again.recalculation.value,
            )

            again.recalculate()

            assertEquals(
                listOf(100.0, 120.0),
                recorded.map {
                    gym.sets.rows
                        .getValue(it.id)
                        .weight
                },
            )
        }

    @Test
    fun a_counterweight_saves_at_once() =
        runTest {
            val (vm, recorded) = kgMachine()
            vm.update { it.copy(weightMode = WeightMode.Counterweight) }
            var saved = false

            vm.save { saved = true }

            assertTrue(saved)
            assertNull(vm.recalculation.value)
            assertEquals(recorded, recorded.map { gym.sets.rows.getValue(it.id) })
        }

    @Test
    fun a_platform_change_without_recorded_sets_saves_at_once() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0)
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            vm.update { it.copy(platformWeight = "25") }
            var saved: MachineId? = null

            vm.save { saved = it }

            assertEquals(press.id, saved)
            assertNull(vm.recalculation.value)
        }

    @Test
    fun an_own_unit_needs_a_name() {
        val custom = MachineFormState(name = "Гравитрон", unit = WeightUnit.Custom)

        assertEquals(false, custom.canSave)
        assertEquals(false, custom.copy(unitLabel = "  ").canSave)
        assertEquals(true, custom.copy(unitLabel = "плитка").canSave)
    }

    @Test
    fun an_own_unit_is_saved_with_its_name_and_kilograms_without_one() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Гравитрон")).also { it.load() }
            vm.update { it.copy(unit = WeightUnit.Custom, unitLabel = " плитка ") }
            var saved: MachineId? = null

            vm.save { saved = it }

            val machine = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertEquals(WeightUnit.Custom to "плитка", machine.unit to machine.unitLabel)

            val again = viewModel(MachineFormArgs(machine.id, null, "")).also { it.load() }
            assertEquals("плитка", again.state.value.unitLabel)
            again.update { it.copy(unit = WeightUnit.Kg) }
            again.save {}

            val kilograms = assertNotNull(gym.machines.byId(machine.id))
            assertEquals(WeightUnit.Kg to "", kilograms.unit to kilograms.unitLabel)
        }

    @Test
    fun a_switch_while_editing_saves_a_copy_for_the_account_that_became_active() =
        runTest {
            gym.withAccounts(misha, ivan, active = misha)
            val hers = Machine.new("Жим ногами", misha.account.userId, t0)
            gym.machines.upsert(hers)
            val vm = viewModel(MachineFormArgs(hers.id, null, "")).also { it.load() }

            gym.accounts.switchTo(ivan.account.userId)
            vm.update { it.copy(weightStep = "10", setupNote = "Упоры на 3") }
            var saved: MachineId? = null
            vm.save { saved = it }

            val untouched = assertNotNull(gym.machines.byId(hers.id))
            assertEquals(2.5, untouched.weightStep)
            assertEquals("", untouched.setupNote)
            val mirrored = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertNotEquals(hers.id, mirrored.id)
            assertEquals(ivan.account.userId, mirrored.userId)
            assertEquals(10.0, mirrored.weightStep)
        }

    @Test
    fun the_weight_step_is_any_positive_decimal() {
        assertEquals(1.25, MachineFormState(name = "Гакк", weightStep = "1,25").weightStepValue)
        assertEquals(0.5, MachineFormState(name = "Гакк", weightStep = "0.5").weightStepValue)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "0").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "0,0001").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "").canSave)
        assertEquals(false, MachineFormState(name = "Гакк", weightStep = "-1").canSave)
    }

    @Test
    fun an_existing_step_loads_as_text() =
        runTest {
            val press = Machine.new("Жим ногами", null, t0).copy(weightStep = 1.25)
            gym.machines.upsert(press)

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals("1.25", vm.state.value.weightStep)
        }

    /** Иван's press, linked by Иван to a friend's machine. */
    private suspend fun linkedPress(): Pair<Machine, MachineLink> {
        val hers = Machine.new("Жим ногами", misha.account.userId, t0)
        val (press, link) = linkedCopy(hers, ivan.account.userId, t0)
        gym.machines.upsert(press)
        gym.machineLinks.upsert(link)
        return press to link
    }

    @Test
    fun only_an_own_saved_machine_can_be_linked() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val press = Machine.new("Жим ногами", ivan.account.userId, t0)
            gym.machines.upsert(press)

            val saved = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            val fresh = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }
            val copy = viewModel(MachineFormArgs(null, press.id, "Гакк")).also { it.load() }

            assertTrue(saved.linking.value.canLink)
            assertFalse(fresh.linking.value.canLink)
            assertFalse(copy.linking.value.canLink)
        }

    @Test
    fun the_form_names_every_friend_s_machine_that_is_the_same_by_owner_then_name() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val oleg = Friend(UserId("33333333-3333-4333-8333-333333333333"), "Олег")
            gym.friends.group("Зал на Лесной", owner = mishaFriend, ivanFriend, oleg)
            val hers = Machine.new("Платформа", misha.account.userId, t0)
            val (press, link) = linkedCopy(hers, ivan.account.userId, t0)
            val (his, hisLink) = linkedCopy(hers, oleg.userId, t0)
            val (hisOther, otherLink) = linkedCopy(his, oleg.userId, t0)
            gym.machines.upsert(press)
            gym.machineLinks.upsert(link)
            gym.friends.machines +=
                listOf(hers, his.copy(name = "Жим ногами"), hisOther.copy(name = "Жим"))
            gym.friends.links += listOf(hisLink, otherLink)

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(
                listOf(
                    LinkedMachineUi(hers.id, mishaFriend, "Платформа"),
                    LinkedMachineUi(hisOther.id, oleg, "Жим"),
                    LinkedMachineUi(his.id, oleg, "Жим ногами"),
                ),
                vm.linking.value.linkedWith,
            )
        }

    @Test
    fun unlinking_breaks_friends_links_then_deletes_the_own_ones() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val (linked, link) = linkedPress()
            val vm = viewModel(MachineFormArgs(linked.id, null, "")).also { it.load() }
            assertTrue(vm.linking.value.canUnlink)

            vm.askToUnlink()
            assertTrue(vm.linking.value.confirmingUnlink)
            gym.clock.current += 1.minutes
            vm.confirmUnlink()

            assertEquals(listOf(linked.id), gym.friends.broken)
            assertEquals(
                link.copy(deleted = true, updatedAt = gym.clock.current),
                gym.machineLinks.rows[link.id],
            )
            assertFalse(vm.linking.value.confirmingUnlink)
            assertFalse(vm.linking.value.canUnlink)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun offline_unlinking_says_so_and_writes_nothing() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val (linked, link) = linkedPress()
            val vm = viewModel(MachineFormArgs(linked.id, null, "")).also { it.load() }
            gym.friends.offline = true

            vm.askToUnlink()
            vm.confirmUnlink()

            assertEquals("Нет связи с сервером", vm.linking.value.error)
            assertEquals(link, gym.machineLinks.rows[link.id])
            assertFalse(vm.linking.value.confirmingUnlink)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun cancelling_keeps_the_link() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val (linked, link) = linkedPress()
            val vm = viewModel(MachineFormArgs(linked.id, null, "")).also { it.load() }

            vm.askToUnlink()
            vm.cancelUnlink()

            assertEquals(link, gym.machineLinks.rows[link.id])
            assertEquals(emptyList(), gym.friends.broken)
            assertFalse(vm.linking.value.confirmingUnlink)
        }

    @Test
    fun a_new_machine_or_one_without_an_account_offers_no_unlinking() =
        runTest {
            val anonymous = Machine.new("Жим ногами", null, t0)
            gym.machines.upsert(anonymous)

            val existing = viewModel(MachineFormArgs(anonymous.id, null, "")).also { it.load() }
            val fresh = viewModel(MachineFormArgs(null, null, "Гакк")).also { it.load() }

            assertFalse(existing.linking.value.canUnlink)
            assertFalse(fresh.linking.value.canUnlink)
        }

    /** Иван's original, which Миша linked her copy to. */
    private suspend fun copiedOriginal(): Machine {
        val original = Machine.new("Жим ногами", ivan.account.userId, t0)
        gym.machines.upsert(original)
        gym.friends.group("Зал на Лесной", owner = mishaFriend, ivanFriend)
        val (copy, link) = linkedCopy(original, misha.account.userId, t0)
        gym.friends.machines += copy
        gym.friends.links += link
        return original
    }

    @Test
    fun an_original_a_friend_linked_to_offers_unlinking_to_its_owner() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val original = copiedOriginal()

            val vm = viewModel(MachineFormArgs(original.id, null, "")).also { it.load() }

            assertTrue(vm.linking.value.canUnlink)
            assertEquals(
                listOf("Жим ногами" to "Миша"),
                vm.linking.value.linkedWith
                    .map { it.name to it.owner.displayName },
            )
        }

    @Test
    fun unlinking_an_original_breaks_the_friend_s_link_and_ends_the_offer() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val original = copiedOriginal()
            val vm = viewModel(MachineFormArgs(original.id, null, "")).also { it.load() }

            vm.askToUnlink()
            vm.confirmUnlink()

            assertEquals(listOf(original.id), gym.friends.broken)
            assertTrue(
                gym.friends.links
                    .single()
                    .deleted,
            )
            assertFalse(vm.linking.value.canUnlink)
            assertEquals(emptyList(), vm.linking.value.linkedWith)
        }

    @Test
    fun an_original_with_no_copies_offers_no_unlinking() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val original = Machine.new("Жим ногами", ivan.account.userId, t0)
            gym.machines.upsert(original)

            val vm = viewModel(MachineFormArgs(original.id, null, "")).also { it.load() }

            assertFalse(vm.linking.value.canUnlink)
        }

    @Test
    fun offline_an_unlinked_original_offers_no_unlinking() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val original = copiedOriginal()
            gym.friends.offline = true

            val vm = viewModel(MachineFormArgs(original.id, null, "")).also { it.load() }

            assertFalse(vm.linking.value.canUnlink)
            assertEquals(emptyList(), vm.linking.value.linkedWith)
        }

    @Test
    fun links_read_for_an_account_no_longer_active_never_show() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val original = copiedOriginal()
            val ivansRead = CompletableDeferred<Unit>()
            gym.friends.gate = ivansRead
            val vm = viewModel(MachineFormArgs(original.id, null, "")).also { it.load() }

            gym.friends.gate = null
            gym.accounts.switchTo(misha.account.userId)
            ivansRead.complete(Unit)

            assertFalse(vm.linking.value.canUnlink)
            assertFalse(vm.linking.value.canLink)
            assertEquals(emptyList(), vm.linking.value.linkedWith)
        }

    @Test
    fun coming_back_to_the_form_reads_the_links_again() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val original = Machine.new("Жим ногами", ivan.account.userId, t0)
            gym.machines.upsert(original)
            gym.friends.group("Зал на Лесной", owner = mishaFriend, ivanFriend)
            val hers = Machine.new("Жим ногами", misha.account.userId, t0)
            gym.friends.machines += hers
            val vm = viewModel(MachineFormArgs(original.id, null, "")).also { it.load() }
            vm.update { it.copy(setupNote = "Сиденье на 4") }

            gym.machineLinks.upsert(
                MachineLink(
                    MachineLinkId.random(),
                    ivan.account.userId,
                    original.id,
                    hers.id,
                    t0,
                    false,
                ),
            )
            vm.load()

            assertEquals(
                listOf("Жим ногами" to "Миша"),
                vm.linking.value.linkedWith
                    .map { it.name to it.owner.displayName },
            )
            assertEquals("Сиденье на 4", vm.state.value.setupNote)
        }
}
