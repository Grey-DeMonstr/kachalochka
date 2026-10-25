package monster.greyde.kachalochka.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

internal fun session(
    id: String,
    name: String,
    t0: kotlin.time.Instant,
) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван", t0)
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша", t0)

    private fun viewModel() =
        ProfileViewModel(
            gym.profiles,
            gym.accounts,
            gym.currentUser,
            gym.clock,
            gym.utcOffset,
            gym.sync,
            gym.photos,
            AccountAvatars(gym.accounts, gym.profiles),
        )

    private val ProfileViewModel.ui: ProfileUi get() = profile.value!!

    private fun TestScope.pause() = advanceTimeBy(SAVE_DELAY + 1.milliseconds)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nobody_signed_in_has_no_avatar_to_choose() =
        runTest {
            assertNull(viewModel().avatar.value)
        }

    @Test
    fun the_avatar_shows_the_google_picture_until_one_is_chosen() =
        runTest {
            val pictured =
                ivan.copy(account = ivan.account.copy(pictureUrl = "https://example.test/i.png"))
            gym.withAccounts(pictured, active = pictured)

            val avatar = assertNotNull(viewModel().avatar.value)

            assertEquals(Avatar(picture = "https://example.test/i.png"), avatar.avatar)
            assertFalse(avatar.canRemove)
        }

    @Test
    fun a_chosen_photo_becomes_the_profile_s_avatar_at_once() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val vm = viewModel()

            vm.chooseAvatar(byteArrayOf(7, 7))

            val profile = assertNotNull(gym.profiles.forOwner(owner))
            val photo = gym.photos.rows.getValue(assertNotNull(profile.avatarPhoto))
            assertEquals(MachineId(profile.id.value), photo.machineId)
            assertEquals(owner, photo.userId)
            assertContentEquals(byteArrayOf(7, 7), gym.photos.bytes[photo.id])
            assertEquals(
                photo.id,
                vm.avatar.value
                    ?.avatar
                    ?.photo,
            )
            assertTrue(assertNotNull(vm.avatar.value).canRemove)
        }

    @Test
    fun removing_the_avatar_brings_the_google_picture_back() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val vm = viewModel()
            vm.chooseAvatar(byteArrayOf(7))
            val chosen = assertNotNull(gym.profiles.forOwner(owner)?.avatarPhoto)

            vm.removeAvatar()

            assertNull(gym.profiles.forOwner(owner)?.avatarPhoto)
            assertTrue(
                gym.photos.rows
                    .getValue(chosen)
                    .deleted,
            )
            assertFalse(assertNotNull(vm.avatar.value).canRemove)
        }

    @Test
    fun a_new_avatar_replaces_the_old_photo() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val vm = viewModel()
            vm.chooseAvatar(byteArrayOf(1))
            val first = assertNotNull(gym.profiles.forOwner(owner)?.avatarPhoto)

            vm.chooseAvatar(byteArrayOf(2))

            assertTrue(
                gym.photos.rows
                    .getValue(first)
                    .deleted,
            )
            assertEquals(
                listOf(byteArrayOf(2).toList()),
                gym.photos.rows.values
                    .filterNot { it.deleted }
                    .map {
                        gym.photos.bytes
                            .getValue(it.id)
                            .toList()
                    },
            )
        }

    @Test
    fun nobody_signed_in_means_no_nickname_but_the_body_fields() =
        runTest {
            val vm = viewModel()

            assertNull(vm.ui.nickname)
            assertEquals(ProfileUi(null, "", null, "", "", true, true), vm.ui)
        }

    @Test
    fun signing_in_shows_the_account_name_as_placeholder_and_the_stored_profile() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.profiles.upsert(
                Profile.new(ivan.account.userId, t0).copy(
                    displayName = "Ванёк",
                    sex = Sex.Male,
                    birthDate = CalendarDay(1990, 6, 5),
                    heightCm = 180.5,
                ),
            )

            val vm = viewModel()

            assertEquals(
                ProfileUi("Ванёк", "Иван", Sex.Male, "05.06.1990", "180.5", true, true),
                vm.ui,
            )
        }

    @Test
    fun a_typed_nickname_is_cut_to_forty_characters() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.type("a".repeat(50))

            assertEquals(NICKNAME_LENGTH, vm.ui.nickname?.length)
        }

    @Test
    fun a_typed_field_is_saved_after_a_short_pause_and_asks_for_a_sync() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.type("Ванёк")
            vm.typeBirthDate("15.06.1990")
            vm.typeHeight("165,5")
            assertNull(gym.profiles.forOwner(ivan.account.userId))
            pause()

            val profile = assertNotNull(gym.profiles.forOwner(ivan.account.userId))
            assertEquals(ivan.account.userId.value, profile.id.value)
            assertEquals("Ванёк", profile.displayName)
            assertEquals(CalendarDay(1990, 6, 15), profile.birthDate)
            assertEquals(165.5, profile.heightCm)
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun a_choice_is_saved_at_once() =
        runTest {
            val vm = viewModel()

            vm.chooseSex(Sex.Female)

            assertEquals(Sex.Female, gym.profiles.forOwner(null)?.sex)
        }

    @Test
    fun leaving_saves_what_is_typed_at_once() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.typeHeight("180")
            vm.leave()

            assertEquals(180.0, gym.profiles.forOwner(ivan.account.userId)?.heightCm)
        }

    @Test
    fun leaving_with_nothing_changed_writes_nothing() =
        runTest {
            val vm = viewModel()

            vm.leave()
            pause()

            assertNull(gym.profiles.forOwner(null))
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun saving_updates_the_existing_profile_with_a_fresh_timestamp_and_keeps_the_rest() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val friend = UserId("33333333-3333-4333-8333-333333333333")
            val original =
                Profile.new(owner, t0).copy(displayName = "Old", friendColors = mapOf(friend to 2))
            gym.profiles.upsert(original)
            val vm = viewModel()
            gym.clock.current += kotlin.time.Duration.parse("PT1H")

            vm.type("New")
            vm.leave()

            assertEquals(
                original.copy(displayName = "New", updatedAt = gym.clock.current),
                gym.profiles.forOwner(owner),
            )
        }

    @Test
    fun weights_show_in_kilograms_until_another_unit_is_chosen() =
        runTest {
            val vm = viewModel()
            assertEquals(PreferredWeightUnit.Kg, vm.ui.weightUnit)

            vm.chooseWeightUnit(PreferredWeightUnit.Mixed)

            assertEquals(PreferredWeightUnit.Mixed, gym.profiles.forOwner(null)?.weightUnit)
            assertEquals(PreferredWeightUnit.Mixed, vm.ui.weightUnit)
        }

    @Test
    fun the_stored_weight_unit_is_shown_for_the_active_account() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            gym.profiles.upsert(
                Profile.new(misha.account.userId, t0).copy(weightUnit = PreferredWeightUnit.Lb),
            )
            val vm = viewModel()

            gym.accounts.switchTo(misha.account.userId)

            assertEquals(PreferredWeightUnit.Lb, vm.ui.weightUnit)
        }

    @Test
    fun a_birth_date_must_be_a_real_past_day_and_a_height_plausible() =
        runTest {
            val vm = viewModel()

            listOf("1990-06-15", "31.02.1990", "01.01.1899", "01.01.2999").forEach {
                vm.typeBirthDate(it)
                assertFalse(vm.ui.birthDateValid, it)
            }
            vm.typeBirthDate("15.06.1990")
            assertTrue(vm.ui.birthDateValid)

            listOf("18", "1800", "сто").forEach {
                vm.typeHeight(it)
                assertFalse(vm.ui.heightValid, it)
            }
            vm.typeHeight("180")
            assertTrue(vm.ui.heightValid)
        }

    @Test
    fun an_invalid_field_keeps_its_stored_value_and_the_rest_is_saved() =
        runTest {
            gym.profiles.upsert(Profile.new(null, t0).copy(heightCm = 180.0))
            val vm = viewModel()

            vm.typeHeight("18")
            vm.typeBirthDate("15.06.1990")
            vm.leave()

            val profile = assertNotNull(gym.profiles.forOwner(null))
            assertEquals(180.0, profile.heightCm)
            assertEquals(CalendarDay(1990, 6, 15), profile.birthDate)
        }

    @Test
    fun clearing_a_field_clears_the_stored_value() =
        runTest {
            gym.profiles.upsert(Profile.new(null, t0).copy(heightCm = 180.0))
            val vm = viewModel()

            vm.typeHeight("")
            pause()

            assertNull(gym.profiles.forOwner(null)?.heightCm)
        }

    @Test
    fun the_cycle_length_is_asked_of_a_woman_and_saved_with_the_profile() =
        runTest {
            val vm = viewModel()
            assertFalse(vm.ui.showsCycleLength)

            vm.chooseSex(Sex.Female)
            assertTrue(vm.ui.showsCycleLength)
            assertEquals("", vm.ui.cycleLength)
            vm.typeCycleLength("31")
            pause()

            assertEquals(31, gym.profiles.forOwner(null)?.cycleLength)
            assertEquals("31", viewModel().ui.cycleLength)
        }

    @Test
    fun a_cycle_length_is_14_to_60_days_and_an_empty_one_is_none() =
        runTest {
            gym.profiles.upsert(Profile.new(null, t0).copy(sex = Sex.Female, cycleLength = 30))
            val vm = viewModel()

            listOf("13", "61", "28.5", "месяц").forEach {
                vm.typeCycleLength(it)
                assertFalse(vm.ui.cycleLengthValid, it)
            }
            listOf("14", "60").forEach {
                vm.typeCycleLength(it)
                assertTrue(vm.ui.cycleLengthValid, it)
            }
            vm.typeCycleLength("")
            vm.leave()

            assertNull(gym.profiles.forOwner(null)?.cycleLength)
        }

    @Test
    fun a_man_s_profile_keeps_the_stored_cycle_length_and_ignores_the_hidden_field() =
        runTest {
            gym.profiles.upsert(Profile.new(null, t0).copy(sex = Sex.Female, cycleLength = 30))
            val vm = viewModel()

            vm.typeCycleLength("5")
            vm.chooseSex(Sex.Male)

            assertEquals(Sex.Male, gym.profiles.forOwner(null)?.sex)
            assertEquals(30, gym.profiles.forOwner(null)?.cycleLength)
        }

    @Test
    fun switching_the_active_account_reloads_the_profile() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            gym.profiles.upsert(
                Profile.new(misha.account.userId, t0).copy(displayName = "Мишка"),
            )
            val vm = viewModel()

            gym.accounts.switchTo(misha.account.userId)

            assertEquals("Мишка", vm.ui.nickname)
            assertEquals("Миша", vm.ui.placeholder)
        }

    @Test
    fun a_switch_saves_what_was_typed_for_the_account_it_was_typed_for() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val vm = viewModel()

            vm.type("Ваня")
            gym.accounts.switchTo(misha.account.userId)
            pause()

            assertEquals("Ваня", gym.profiles.forOwner(ivan.account.userId)?.displayName)
            assertNull(gym.profiles.forOwner(misha.account.userId))
            assertEquals("", vm.ui.nickname)
        }

    @Test
    fun a_saved_nickname_becomes_the_account_s_shown_name_and_the_placeholder_stays() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.type("Ванёк")
            vm.leave()

            assertEquals(
                "Ванёк",
                gym.accounts.accounts.value
                    .single()
                    .shownName,
            )
            assertEquals("Иван", vm.ui.placeholder)
        }

    @Test
    fun the_avatar_is_named_by_the_stored_nickname() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.profiles.upsert(Profile.new(ivan.account.userId, t0).copy(displayName = "ГДМ"))

            assertEquals("ГДМ", viewModel().avatar.value?.name)
        }

    @Test
    fun opening_the_profile_remembers_the_stored_nickname() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.profiles.upsert(Profile.new(ivan.account.userId, t0).copy(displayName = "Ванёк"))

            viewModel()

            assertEquals(
                "Ванёк",
                gym.accounts.accounts.value
                    .single()
                    .shownName,
            )
        }

    @Test
    fun a_managed_child_has_no_profile_here() =
        runTest {
            gym.withAccounts(ivan, active = ivan).withChild(childAccount(SASHA, ivan))
            gym.accounts.switchTo(SASHA.userId)

            val vm = viewModel()

            assertNull(vm.profile.value)
            assertNull(vm.avatar.value)
        }
}
