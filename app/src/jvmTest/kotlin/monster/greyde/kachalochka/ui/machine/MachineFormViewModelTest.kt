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
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.DayBest
import monster.greyde.kachalochka.core.domain.gym.Machine
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.gym.MachineLink
import monster.greyde.kachalochka.core.domain.gym.MachineLinkId
import monster.greyde.kachalochka.core.domain.gym.Photo
import monster.greyde.kachalochka.core.domain.gym.Visit
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class MachineFormViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Ivan")
    private val misha = session("22222222-2222-4222-8222-222222222222", "Misha")
    private val ivanFriend = Friend(ivan.account.userId, "Ivan")
    private val mishaFriend = Friend(misha.account.userId, "Misha")

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
            gym.machineLinks,
            gym.photos,
            gym.catalogue,
            gym.machineReads,
            gym.sets,
            gym.gymWrites,
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
            gym.machines.upsert(Machine.new("Press", null, t0).copy(tags = setOf("arms", "Chest")))
            gym.machines.upsert(Machine.new("Squat", null, t0).copy(tags = setOf("Legs")))
            gym.machines.upsert(
                Machine.new("Old", null, t0).copy(tags = setOf("Back"), deleted = true),
            )

            val vm = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }

            assertEquals(listOf("arms", "chest", "legs"), vm.state.value.shownTags)
            assertEquals(emptySet(), vm.state.value.tags)
        }

    @Test
    fun a_machine_created_from_a_tag_filter_starts_with_its_tags() =
        runTest {
            val vm =
                viewModel(MachineFormArgs(null, null, "Hack", listOf("Arms"))).also { it.load() }
            var saved: MachineId? = null

            vm.save { saved = it }

            assertEquals(setOf("arms"), gym.machines.byId(assertNotNull(saved))?.tags)
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
        on.machineLinks,
        on.photos,
        on.catalogue,
        on.machineReads,
        on.sets,
        on.gymWrites,
    )

    @Test
    fun friends_tags_the_account_lacks_are_offered_and_one_chosen_becomes_its_own() =
        runTest {
            val on = signedInGym()
            on.friends.group("Forest Street Gym", owner = OLEG, ME)
            on.friends.machines +=
                Machine.new("Press", OLEG.userId, t0).copy(tags = setOf("Shoulders", "legs"))
            on.machines.upsert(Machine.new("Squat", ME.userId, t0).copy(tags = setOf("Legs")))
            val vm = formOn(on, MachineFormArgs(null, null, "Hack")).also { it.load() }

            assertEquals(
                listOf("shoulders" to OLEG.userId),
                vm.state.value.offeredFriendTags
                    .map { it.tag to it.friend.userId },
            )

            vm.toggleTag("shoulders")
            var saved: MachineId? = null
            vm.save { saved = it }

            assertEquals(setOf("shoulders"), on.machines.byId(assertNotNull(saved))?.tags)
            assertEquals(emptyList(), vm.state.value.offeredFriendTags)
        }

    /** Ivan's copy of Oleg's press, linked to it, in a group with Oleg. */
    private suspend fun FakeGym.myCopyOfOlegsPress(): Pair<Machine, Machine> {
        friends.group("Forest Street Gym", owner = OLEG, ME)
        val olegs = Machine.new("Press", OLEG.userId, t0).copy(tags = setOf("shoulders"))
        friends.machines += olegs
        val (copy, link) = linkedCopy(olegs, ME.userId, t0)
        machines.upsert(copy)
        machineLinks.upsert(link)
        return copy to olegs
    }

    @Test
    fun opening_a_linked_machine_reads_the_group_once() =
        runTest {
            val on = signedInGym()
            val (copy, olegs) = on.myCopyOfOlegsPress()

            val vm = formOn(on, MachineFormArgs(copy.id, null, "")).also { it.load() }

            assertEquals(
                listOf(olegs.id),
                vm.linking.value.linkedWith
                    .map { it.machineId },
            )
            assertEquals(1, on.friends.reads)
        }

    @Test
    fun links_read_before_show_at_once_while_the_group_is_read_again() =
        runTest {
            val on = signedInGym()
            val (copy, olegs) = on.myCopyOfOlegsPress()
            on.catalogue.group(ME.userId)
            val gate = CompletableDeferred<Unit>()
            on.friends.gate = gate

            val vm = formOn(on, MachineFormArgs(copy.id, null, "")).also { it.load() }

            assertEquals(
                listOf(olegs.id),
                vm.linking.value.linkedWith
                    .map { it.machineId },
            )
            gate.complete(Unit)
            assertEquals(
                listOf(olegs.id),
                vm.linking.value.linkedWith
                    .map { it.machineId },
            )
        }

    @Test
    fun shown_again_without_a_cached_read_the_form_keeps_its_links_while_it_reads() =
        runTest {
            val on = signedInGym()
            val (copy, olegs) = on.myCopyOfOlegsPress()
            val catalogue = on.catalogueOver(on.machineLinks)
            val vm =
                MachineFormViewModel(
                    MachineFormArgs(copy.id, null, ""),
                    on.machines,
                    on.currentUser,
                    on.accounts,
                    on.clock,
                    on.machineLinks,
                    on.photos,
                    catalogue,
                    on.machineReadsOver(catalogue),
                    on.sets,
                    on.gymWrites,
                ).also { it.load() }
            // Breaking the links of a machine on the server drops the cached read.
            assertTrue(catalogue.unlink(ME.userId, MachineId.random()))
            assertNull(catalogue.lastGroup(ME.userId))
            val gate = CompletableDeferred<Unit>()
            on.friends.gate = gate

            vm.load()

            assertEquals(
                listOf(olegs.id),
                vm.linking.value.linkedWith
                    .map { it.machineId },
            )
            gate.complete(Unit)
            assertEquals(
                listOf(olegs.id),
                vm.linking.value.linkedWith
                    .map { it.machineId },
            )
        }

    @Test
    fun linked_machines_suggest_the_tags_the_machine_lacks_apart_from_other_tags() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val oleg = Friend(UserId("33333333-3333-4333-8333-333333333333"), "Oleg")
            gym.friends.group("Forest Street Gym", owner = mishaFriend, ivanFriend, oleg)
            val hers =
                Machine
                    .new("Platform", misha.account.userId, t0)
                    .copy(tags = setOf("Quads", "LEGS", "back"))
            val (copy, link) = linkedCopy(hers, ivan.account.userId, t0)
            val press = copy.copy(tags = setOf("Legs"))
            gym.machines.upsert(press)
            gym.machineLinks.upsert(link)
            gym.machines.upsert(
                Machine.new("Row", ivan.account.userId, t0).copy(tags = setOf("Back")),
            )
            gym.friends.machines +=
                listOf(
                    hers,
                    Machine.new("Press", oleg.userId, t0).copy(tags = setOf("Shoulders", "quads")),
                )

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(listOf("back", "quads"), vm.state.value.suggestedTags)
            assertEquals(listOf("legs"), vm.state.value.shownTags)
            assertEquals(
                listOf("shoulders"),
                vm.state.value.offeredFriendTags
                    .map { it.tag },
            )

            vm.toggleTag("back")
            vm.save {}

            assertEquals(listOf("quads"), vm.state.value.suggestedTags)
            assertEquals(listOf("legs", "back"), vm.state.value.shownTags)
            assertEquals(setOf("legs", "back"), gym.machines.byId(press.id)?.tags)
        }

    @Test
    fun offline_only_the_own_linked_machines_suggest_tags() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.friends.offline = true
            val press = Machine.new("Leg press", ivan.account.userId, t0)
            val squat =
                Machine.new("Hack", ivan.account.userId, t0).copy(tags = setOf("Legs"))
            gym.machines.upsert(press)
            gym.machines.upsert(squat)
            gym.machineLinks.upsert(
                MachineLink(
                    MachineLinkId.random(),
                    ivan.account.userId,
                    press.id,
                    squat.id,
                    t0,
                    false,
                ),
            )

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(listOf("legs"), vm.state.value.suggestedTags)
            assertEquals(emptyList(), vm.state.value.shownTags)
        }

    @Test
    fun the_machine_s_own_tags_come_before_the_account_s_other_tags() =
        runTest {
            val press = Machine.new("Press", null, t0).copy(tags = setOf("Back"))
            gym.machines.upsert(press)
            gym.machines.upsert(
                Machine.new("Squat", null, t0).copy(tags = setOf("Legs", "Adductors")),
            )
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(listOf("back", "adductors", "legs"), vm.state.value.shownTags)
            assertEquals(emptyList(), vm.state.value.suggestedTags)
        }

    @Test
    fun tapping_chips_keeps_every_held_chip_in_its_place_and_drops_one_no_machine_holds() =
        runTest {
            val press = Machine.new("Press", null, t0).copy(tags = setOf("Back"))
            gym.machines.upsert(press)
            gym.machines.upsert(
                Machine.new("Squat", null, t0).copy(tags = setOf("Legs", "Adductors")),
            )
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            val order = listOf("back", "adductors", "legs")

            vm.toggleTag("legs")
            assertEquals(order, vm.state.value.shownTags)
            vm.toggleTag("legs")
            assertEquals(order, vm.state.value.shownTags)
            vm.toggleTag("back")
            assertEquals(listOf("adductors", "legs"), vm.state.value.shownTags)
        }

    @Test
    fun a_tag_a_new_machine_started_with_loses_its_chip_once_no_machine_would_hold_it() =
        runTest {
            gym.machines.upsert(Machine.new("Press", null, t0).copy(tags = setOf("chest")))
            val vm =
                viewModel(MachineFormArgs(null, null, "Hack", listOf("Arms", "chest")))
                    .also { it.load() }

            vm.toggleTag("arms")
            vm.toggleTag("chest")

            assertEquals(listOf("chest"), vm.state.value.shownTags)
            assertFalse(vm.state.value.chosen("arms"))
        }

    @Test
    fun signed_out_the_own_linked_machines_suggest_tags() =
        runTest {
            val press = Machine.new("Leg press", null, t0)
            val squat = Machine.new("Hack", null, t0).copy(tags = setOf("Legs"))
            gym.machines.upsert(press)
            gym.machines.upsert(squat)
            gym.machineLinks.upsert(
                MachineLink(MachineLinkId.random(), null, press.id, squat.id, t0, false),
            )

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(listOf("legs"), vm.state.value.suggestedTags)
        }

    @Test
    fun chosen_and_added_tags_are_saved_with_the_machine() =
        runTest {
            gym.machines.upsert(Machine.new("Squat", null, t0).copy(tags = setOf("Legs")))
            val vm = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }

            vm.toggleTag("Legs")
            vm.typeNewTag("  Press  ")
            vm.addNewTag()
            vm.typeNewTag("   ")
            vm.addNewTag()
            var saved: MachineId? = null
            vm.save { saved = it }

            assertEquals(setOf("legs", "press"), gym.machines.byId(assertNotNull(saved))?.tags)
            assertEquals(listOf("legs", "press"), vm.state.value.shownTags)
            assertEquals("", vm.state.value.newTag)
        }

    @Test
    fun a_typed_tag_is_kept_as_its_key_so_one_in_another_case_is_the_existing_one() =
        runTest {
            gym.machines.upsert(Machine.new("Squat", null, t0).copy(tags = setOf("Legs")))
            val vm = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }

            vm.typeNewTag(" LEGS ")
            vm.addNewTag()

            assertEquals(setOf("legs"), vm.state.value.tags)
            assertEquals(listOf("legs"), vm.state.value.shownTags)
        }

    @Test
    fun a_tag_in_two_letter_cases_is_one_chip_and_the_machine_keeps_it_once() =
        runTest {
            gym.machines.upsert(Machine.new("Squat", null, t0).copy(tags = setOf("Legs")))
            val press = Machine.new("Press", null, t0).copy(tags = setOf("legs", "LEGS "))
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(1, vm.state.value.shownTags.size)
            assertTrue(
                vm.state.value.chosen(
                    vm.state.value.shownTags
                        .single(),
                ),
            )
            vm.save {}
            assertEquals(
                1,
                gym.machines
                    .byId(press.id)
                    ?.tags
                    ?.size,
            )

            vm.toggleTag("Legs")

            assertEquals(emptySet(), vm.state.value.tags)
            assertFalse(vm.state.value.chosen("Legs"))
        }

    @Test
    fun a_saved_machine_opens_with_its_tags_chosen_and_a_tap_removes_one() =
        runTest {
            val press = Machine.new("Press", null, t0).copy(tags = setOf("Chest", "Arms"))
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(setOf("chest", "arms"), vm.state.value.tags)
            vm.toggleTag("arms")
            vm.save {}

            assertEquals(setOf("chest"), gym.machines.byId(press.id)?.tags)
        }

    @Test
    fun photos_taken_in_the_form_are_written_with_the_machine_it_saves() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }

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
            val press = Machine.new("Leg press", null, t0)
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

    /** Ivan's press, linked to Misha's; each has a photo, Misha's the older. */
    private suspend fun pressWithFriendsPhoto(): Triple<Machine, Photo, Photo> {
        gym.withAccounts(ivan, active = ivan)
        gym.friends.group("Forest Street Gym", owner = mishaFriend, ivanFriend)
        val hers = Machine.new("Leg press", misha.account.userId, t0)
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
                listOf(mine.id.value to null, theirs.id.value to "Misha"),
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
            val press = Machine.new("Leg press", null, t0)
            gym.machines.upsert(press)
            gym.photos.add(Photo.new(press.id, null, t0), byteArrayOf(1))

            val vm = viewModel(MachineFormArgs(null, press.id, "Leg press 2")).also { it.load() }

            assertEquals(emptyList(), vm.photos.value)
        }

    @Test
    fun a_switch_drops_the_other_account_s_photos_and_keeps_the_ones_just_taken() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val press = Machine.new("Leg press", ivan.account.userId, t0)
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
        val vm = viewModel(MachineFormArgs(null, null, "hack")).also { it.load() }

        assertEquals(MachineFormState(name = "hack"), vm.state.value)
        assertEquals(true, vm.state.value.canSave)
    }

    @Test
    fun loading_again_keeps_the_unsaved_edits() {
        val vm = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }
        vm.update { it.copy(setupNote = "Pads at 3") }

        vm.load()

        assertEquals("Pads at 3", vm.state.value.setupNote)
    }

    @Test
    fun a_blank_name_or_a_bad_platform_weight_cannot_be_saved() {
        assertEquals(false, MachineFormState(name = " ").canSave)
        assertEquals(false, MachineFormState(name = "Hack", platformWeight = "-5").canSave)
        assertEquals(false, MachineFormState(name = "Hack", platformWeight = "abc").canSave)
        assertEquals(25.5, MachineFormState(platformWeight = "25.5").platformWeightValue)
        assertEquals(0.0, MachineFormState(platformWeight = "").platformWeightValue)
    }

    @Test
    fun saving_a_new_machine_stores_every_field() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Hack machine")).also { it.load() }
            vm.update {
                it.copy(
                    setupNote = "Pads at 3",
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
                    "Hack machine",
                    "Pads at 3",
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
                        "Leg press",
                        null,
                        t0,
                    ).copy(setupNote = "Seat at 4", platformWeight = 20.0)
            gym.machines.upsert(source)
            val vm =
                viewModel(
                    MachineFormArgs(null, source.id, "Single-leg press"),
                ).also { it.load() }
            var saved: MachineId? = null

            vm.save { saved = it }

            val copy = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertNotEquals(source.id, copy.id)
            assertEquals("Single-leg press", copy.name)
            assertEquals("Seat at 4", copy.setupNote)
            assertEquals(20.0, copy.platformWeight)
        }

    @Test
    fun editing_an_existing_machine_keeps_its_id() =
        runTest {
            val source = Machine.new("Leg press", null, t0)
            gym.machines.upsert(source)
            gym.clock.current += 1.minutes
            val vm = viewModel(MachineFormArgs(source.id, null, "")).also { it.load() }
            assertEquals("Leg press", vm.state.value.name)

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
            on.friends.group("Forest Street Gym", owner = OLEG, ME)
            val olegs =
                Machine.new("Press", OLEG.userId, t0).copy(
                    weightMode = WeightMode.PerSide,
                    platformWeight = 20.0,
                    weightStep = 5.0,
                )
            on.friends.machines += olegs
            val mine = Machine.new("Leg press", ME.userId, t0)
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
            on.friends.group("Forest Street Gym", owner = OLEG, ME)
            val olegs =
                Machine.new("Platform press", OLEG.userId, t0).copy(tags = setOf("legs", "Press"))
            on.friends.machines += olegs
            val mine = Machine.new("Leg press", ME.userId, t0).copy(tags = setOf("Legs"))
            on.machines.upsert(mine)
            val vm = formOn(on, MachineFormArgs(mine.id, null, "")).also { it.load() }

            vm.copySettings(olegs.id)

            assertEquals("Platform press", vm.state.value.name)
            assertEquals(setOf("legs", "press"), vm.state.value.tags)
        }

    @Test
    fun a_friend_s_unit_is_copied_with_its_name() =
        runTest {
            val on = signedInGym()
            on.friends.group("Forest Street Gym", owner = OLEG, ME)
            val olegs =
                Machine
                    .new("Cable", OLEG.userId, t0)
                    .copy(unit = WeightUnit.Custom, unitLabel = "plate", weightStep = 1.0)
            on.friends.machines += olegs
            val mine = Machine.new("Cable", ME.userId, t0)
            on.machines.upsert(mine)
            val vm = formOn(on, MachineFormArgs(mine.id, null, "")).also { it.load() }

            vm.copySettings(olegs.id)

            assertEquals(
                WeightUnit.Custom to "plate",
                vm.state.value.unit to vm.state.value.unitLabel,
            )
        }

    /** A machine with imported totals and two sets, its platform then typed beside the name. */
    private suspend fun platformTyped(): Triple<MachineFormViewModel, Machine, List<WorkoutSet>> {
        val press = Machine.new("Leg press", null, t0)
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
                    "The platform changed. Change the weight of the recorded sets (2 sets) by " +
                        "−25 kg, so that their totals stay the same?",
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
            Machine.new("Leg press", null, t0).copy(platformWeight = 20.0, weightStep = 2.5)
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
                    .new("Leg press", null, t0)
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
                    "The unit changed: kg → lb. Convert the weight of the recorded sets " +
                        "(2 sets) to the new unit?",
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
                    "The unit changed: kg → lb. Convert the weight of the recorded sets " +
                        "(2 sets) to the new unit and change it by +10 lb for the platform?",
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
    fun saving_a_machine_as_a_gravitron_turns_its_prior_bests_around() =
        runTest {
            val press = Machine.new("Gravitron", null, t0)
            gym.machines.upsert(press)
            val days =
                listOf(
                    CalendarDay(2023, 11, 1),
                    CalendarDay(2023, 11, 2),
                    CalendarDay(2023, 11, 3),
                )
            val recorded =
                days.zip(listOf(30.0, 25.0, 27.0)).map { (day, weight) ->
                    val visit =
                        Visit(VisitId.random(), null, day, day.at(0L, Duration.ZERO), t0, false)
                    gym.visits.upsert(visit)
                    WorkoutSet(
                        WorkoutSetId.random(),
                        null,
                        visit.id,
                        press.id,
                        weight,
                        8,
                        0,
                        t0,
                        t0,
                        false,
                    ).also { gym.sets.upsert(it) }
                }
            gym.gymWrites.refreshAll(null)
            assertEquals(
                DayBest(30.0, 8, 1),
                gym.sets.rows
                    .getValue(recorded[2].id)
                    .priorBest,
            )
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            vm.update { it.copy(weightMode = WeightMode.Counterweight) }

            vm.save {}
            if (vm.recalculation.value != null) vm.keepRecorded()

            assertEquals(WeightMode.Counterweight, gym.machines.byId(press.id)?.weightMode)
            assertEquals(
                DayBest(30.0, 8, 1),
                gym.sets.rows
                    .getValue(recorded[1].id)
                    .priorBest,
            )
            assertEquals(
                DayBest(25.0, 8, 1),
                gym.sets.rows
                    .getValue(recorded[2].id)
                    .priorBest,
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
                    "The weight now counts per side. Halve the weight of the recorded sets " +
                        "(2 sets)?",
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
                    "The weight now counts in total. Double the weight of the recorded sets " +
                        "(2 sets)?\n\nThe unit changed: kg → lb. Convert the weight of the " +
                        "recorded sets (2 sets) to the new unit?",
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
            val press = Machine.new("Leg press", null, t0)
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
        val custom = MachineFormState(name = "Gravitron", unit = WeightUnit.Custom)

        assertEquals(false, custom.canSave)
        assertEquals(false, custom.copy(unitLabel = "  ").canSave)
        assertEquals(true, custom.copy(unitLabel = "plate").canSave)
    }

    @Test
    fun an_own_unit_is_saved_with_its_name_and_kilograms_without_one() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Gravitron")).also { it.load() }
            vm.update { it.copy(unit = WeightUnit.Custom, unitLabel = " plate ") }
            var saved: MachineId? = null

            vm.save { saved = it }

            val machine = assertNotNull(gym.machines.byId(assertNotNull(saved)))
            assertEquals(WeightUnit.Custom to "plate", machine.unit to machine.unitLabel)

            val again = viewModel(MachineFormArgs(machine.id, null, "")).also { it.load() }
            assertEquals("plate", again.state.value.unitLabel)
            again.update { it.copy(unit = WeightUnit.Kg) }
            again.save {}

            val kilograms = assertNotNull(gym.machines.byId(machine.id))
            assertEquals(WeightUnit.Kg to "", kilograms.unit to kilograms.unitLabel)
        }

    @Test
    fun a_switch_while_editing_saves_a_copy_for_the_account_that_became_active() =
        runTest {
            gym.withAccounts(misha, ivan, active = misha)
            val hers = Machine.new("Leg press", misha.account.userId, t0)
            gym.machines.upsert(hers)
            val vm = viewModel(MachineFormArgs(hers.id, null, "")).also { it.load() }

            gym.accounts.switchTo(ivan.account.userId)
            vm.update { it.copy(weightStep = "10", setupNote = "Pads at 3") }
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
        assertEquals(1.25, MachineFormState(name = "Hack", weightStep = "1,25").weightStepValue)
        assertEquals(0.5, MachineFormState(name = "Hack", weightStep = "0.5").weightStepValue)
        assertEquals(false, MachineFormState(name = "Hack", weightStep = "0").canSave)
        assertEquals(false, MachineFormState(name = "Hack", weightStep = "0,0001").canSave)
        assertEquals(false, MachineFormState(name = "Hack", weightStep = "").canSave)
        assertEquals(false, MachineFormState(name = "Hack", weightStep = "-1").canSave)
    }

    @Test
    fun an_existing_step_loads_as_text() =
        runTest {
            val press = Machine.new("Leg press", null, t0).copy(weightStep = 1.25)
            gym.machines.upsert(press)

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals("1.25", vm.state.value.weightStep)
        }

    /** Ivan's press, linked by Ivan to a friend's machine. */
    private suspend fun linkedPress(): Pair<Machine, MachineLink> {
        val hers = Machine.new("Leg press", misha.account.userId, t0)
        val (press, link) = linkedCopy(hers, ivan.account.userId, t0)
        gym.machines.upsert(press)
        gym.machineLinks.upsert(link)
        return press to link
    }

    @Test
    fun only_an_own_saved_machine_can_be_linked() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val press = Machine.new("Leg press", ivan.account.userId, t0)
            gym.machines.upsert(press)

            val saved = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            val fresh = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }
            val copy = viewModel(MachineFormArgs(null, press.id, "Hack")).also { it.load() }

            assertTrue(saved.linking.value.canLink)
            assertFalse(fresh.linking.value.canLink)
            assertFalse(copy.linking.value.canLink)
        }

    @Test
    fun the_form_names_every_friend_s_machine_that_is_the_same_by_owner_then_name() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val oleg = Friend(UserId("33333333-3333-4333-8333-333333333333"), "Oleg")
            gym.friends.group("Forest Street Gym", owner = mishaFriend, ivanFriend, oleg)
            val hers = Machine.new("Platform", misha.account.userId, t0)
            val (press, link) = linkedCopy(hers, ivan.account.userId, t0)
            val (his, hisLink) = linkedCopy(hers, oleg.userId, t0)
            val (hisOther, otherLink) = linkedCopy(his, oleg.userId, t0)
            gym.machines.upsert(press)
            gym.machineLinks.upsert(link)
            gym.friends.machines +=
                listOf(hers, his.copy(name = "Leg press"), hisOther.copy(name = "Incline press"))
            gym.friends.links += listOf(hisLink, otherLink)

            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            assertEquals(
                listOf(
                    LinkedMachineUi(hers.id, mishaFriend, "Platform"),
                    LinkedMachineUi(hisOther.id, oleg, "Incline press"),
                    LinkedMachineUi(his.id, oleg, "Leg press"),
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

            assertEquals("No connection to the server", vm.linking.value.error)
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

    private fun setOn(machine: Machine) =
        WorkoutSet(
            WorkoutSetId.random(),
            machine.userId,
            VisitId.random(),
            machine.id,
            50.0,
            10,
            0,
            t0,
            t0,
            false,
        )

    @Test
    fun a_machine_without_live_sets_is_deleted_after_a_confirmation() =
        runTest {
            val press = Machine.new("Leg press", null, t0)
            gym.machines.upsert(press)
            gym.sets.upsert(setOn(press).copy(deleted = true))
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            assertTrue(vm.canDelete.value)
            var deleted = 0

            vm.askToDelete()
            assertTrue(vm.confirmingDelete.value)
            vm.cancelDelete()
            assertFalse(vm.confirmingDelete.value)
            assertFalse(
                gym.machines.rows
                    .getValue(press.id)
                    .deleted,
            )

            vm.askToDelete()
            vm.confirmDelete { deleted++ }

            assertTrue(
                gym.machines.rows
                    .getValue(press.id)
                    .deleted,
            )
            assertFalse(vm.confirmingDelete.value)
            assertEquals(1, deleted)
        }

    @Test
    fun a_machine_with_a_live_set_or_a_new_one_offers_no_deletion() =
        runTest {
            val press = Machine.new("Leg press", null, t0)
            gym.machines.upsert(press)
            gym.sets.upsert(setOn(press))

            val recorded = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            val fresh = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }

            assertFalse(recorded.canDelete.value)
            assertFalse(fresh.canDelete.value)
        }

    @Test
    fun a_set_recorded_while_the_form_is_open_keeps_the_machine() =
        runTest {
            val press = Machine.new("Leg press", null, t0)
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            var deleted = 0

            vm.askToDelete()
            gym.sets.upsert(setOn(press))
            vm.confirmDelete { deleted++ }

            assertFalse(
                gym.machines.rows
                    .getValue(press.id)
                    .deleted,
            )
            assertFalse(vm.confirmingDelete.value)
            assertFalse(vm.canDelete.value)
            assertEquals(0, deleted)
        }

    @Test
    fun a_switch_to_another_account_ends_the_deletion_offer() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val press = Machine.new("Leg press", ivan.account.userId, t0)
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            assertTrue(vm.canDelete.value)

            gym.accounts.switchTo(misha.account.userId)

            assertFalse(vm.canDelete.value)
        }

    @Test
    fun a_new_machine_or_one_without_an_account_offers_no_unlinking() =
        runTest {
            val anonymous = Machine.new("Leg press", null, t0)
            gym.machines.upsert(anonymous)

            val existing = viewModel(MachineFormArgs(anonymous.id, null, "")).also { it.load() }
            val fresh = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }

            assertFalse(existing.linking.value.canUnlink)
            assertFalse(fresh.linking.value.canUnlink)
        }

    /** Ivan's original, which Misha linked her copy to. */
    private suspend fun copiedOriginal(): Machine {
        val original = Machine.new("Leg press", ivan.account.userId, t0)
        gym.machines.upsert(original)
        gym.friends.group("Forest Street Gym", owner = mishaFriend, ivanFriend)
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
                listOf("Leg press" to "Misha"),
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
            val original = Machine.new("Leg press", ivan.account.userId, t0)
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
            val original = Machine.new("Leg press", ivan.account.userId, t0)
            gym.machines.upsert(original)
            gym.friends.group("Forest Street Gym", owner = mishaFriend, ivanFriend)
            val hers = Machine.new("Leg press", misha.account.userId, t0)
            gym.friends.machines += hers
            val vm = viewModel(MachineFormArgs(original.id, null, "")).also { it.load() }
            vm.update { it.copy(setupNote = "Seat at 4") }

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
                listOf("Leg press" to "Misha"),
                vm.linking.value.linkedWith
                    .map { it.name to it.owner.displayName },
            )
            assertEquals("Seat at 4", vm.state.value.setupNote)
        }

    @Test
    fun leaving_an_untouched_form_asks_nothing() =
        runTest {
            val press = Machine.new("Leg press", null, t0).copy(tags = setOf("Legs"))
            gym.machines.upsert(press)
            val forms =
                listOf(
                    MachineFormArgs(press.id, null, ""),
                    MachineFormArgs(null, null, ""),
                    MachineFormArgs(null, null, "Hack", listOf("Legs")),
                    MachineFormArgs(null, press.id, "Leg press 2"),
                )

            forms.forEach { args ->
                val vm = viewModel(args).also { it.load() }
                assertTrue(vm.requestLeave(), "$args")
                assertFalse(vm.confirmingLeave.value)
            }
        }

    @Test
    fun leaving_with_an_edit_asks_and_staying_keeps_the_form() =
        runTest {
            val press = Machine.new("Leg press", null, t0)
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            vm.update { it.copy(setupNote = "Seat at 4") }

            assertFalse(vm.requestLeave())
            assertTrue(vm.confirmingLeave.value)
            vm.stay()
            assertFalse(vm.confirmingLeave.value)
            assertEquals("Seat at 4", vm.state.value.setupNote)
        }

    @Test
    fun an_edit_typed_back_to_the_start_is_no_change() =
        runTest {
            val vm = viewModel(MachineFormArgs(null, null, "Hack")).also { it.load() }

            vm.update { it.copy(name = "Hack machine") }
            vm.update { it.copy(name = "Hack") }
            vm.typeNewTag("Legs")

            assertTrue(vm.requestLeave())
        }

    @Test
    fun weights_compare_by_value_and_an_unreadable_one_by_its_text() =
        runTest {
            val press = Machine.new("Leg press", null, t0).copy(platformWeight = 20.0)
            gym.machines.upsert(press)
            val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }

            vm.update { it.copy(platformWeight = "20.0", weightStep = "2.50") }
            assertTrue(vm.requestLeave())

            vm.update { it.copy(weightStep = "x") }
            assertFalse(vm.requestLeave())
        }

    @Test
    fun every_kind_of_edit_counts() =
        runTest {
            val (press, mine, theirs) = pressWithFriendsPhoto()
            val edits: List<(MachineFormViewModel) -> Unit> =
                listOf(
                    { it.update { form -> form.copy(name = "Press") } },
                    { it.toggleTag("Legs") },
                    { it.update { form -> form.copy(weightMode = WeightMode.PerSide) } },
                    { it.update { form -> form.copy(platformWeight = "20") } },
                    { it.update { form -> form.copy(platformIncluded = true) } },
                    { it.chooseUnit(WeightUnit.Lb) },
                    { it.update { form -> form.copy(weightStep = "5") } },
                    { it.addPhoto(byteArrayOf(9)) },
                    { it.removePhoto(mine.id.value) },
                    { it.makeCover(theirs.id.value) },
                )

            edits.forEachIndexed { index, edit ->
                val vm = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
                edit(vm)
                assertFalse(vm.requestLeave(), "edit $index")
            }
        }

    @Test
    fun a_switch_alone_is_no_change_but_a_photo_taken_before_it_is() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val press = Machine.new("Leg press", ivan.account.userId, t0)
            gym.machines.upsert(press)
            val photo = Photo.new(press.id, ivan.account.userId, t0)
            gym.photos.add(photo, byteArrayOf(1))
            gym.machines.upsert(press.copy(coverPhoto = photo.id))
            val untouched = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            val taken = viewModel(MachineFormArgs(press.id, null, "")).also { it.load() }
            taken.addPhoto(byteArrayOf(2))

            gym.accounts.switchTo(misha.account.userId)

            assertTrue(untouched.requestLeave())
            assertFalse(taken.requestLeave())
        }
}
