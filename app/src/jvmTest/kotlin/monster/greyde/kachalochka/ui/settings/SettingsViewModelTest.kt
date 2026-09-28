package monster.greyde.kachalochka.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun session(
    id: String,
    name: String,
    t0: kotlin.time.Instant,
) = AccountSession(Account(UserId(id), "$name@example.test", name), "access", "refresh", t0)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван", t0)
    private val misha = session("22222222-2222-4222-8222-222222222222", "Миша", t0)

    private fun viewModel() =
        SettingsViewModel(
            gym.profiles,
            gym.accounts,
            gym.currentUser,
            gym.clock,
            gym.utcOffset,
            gym.sync,
        )

    private val SettingsViewModel.ui: ProfileUi get() = profile.value!!

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nobody_signed_in_means_no_nickname_but_the_body_fields() =
        runTest {
            val vm = viewModel()

            assertNull(vm.ui.nickname)
            assertEquals(ProfileUi(null, "", null, "", "", true, true, false), vm.ui)
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
                ProfileUi("Ванёк", "Иван", Sex.Male, "05.06.1990", "180,5", true, true, false),
                vm.ui,
            )
        }

    @Test
    fun typing_truncates_the_nickname_to_forty_characters_and_enables_save_only_when_changed() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.type("a".repeat(50))

            assertEquals(NICKNAME_LENGTH, vm.ui.nickname?.length)
            assertTrue(vm.ui.canSave)

            vm.type("")

            assertFalse(vm.ui.canSave)
        }

    @Test
    fun saving_writes_a_new_profile_when_the_owner_has_none_and_asks_for_a_sync() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.type("Ванёк")
            vm.chooseSex(Sex.Female)
            vm.typeBirthDate("15.06.1990")
            vm.typeHeight("165,5")
            vm.save()

            val profile = gym.profiles.forOwner(ivan.account.userId)
            assertNotNull(profile)
            assertEquals(ivan.account.userId.value, profile.id.value)
            assertEquals("Ванёк", profile.displayName)
            assertEquals(Sex.Female, profile.sex)
            assertEquals(CalendarDay(1990, 6, 15), profile.birthDate)
            assertEquals(165.5, profile.heightCm)
            assertEquals(1, gym.sync.requests)
            assertFalse(vm.ui.canSave)
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
            vm.save()

            val profile = gym.profiles.forOwner(owner)
            assertEquals(
                original.copy(displayName = "New", updatedAt = gym.clock.current),
                profile,
            )
        }

    @Test
    fun a_signed_out_owner_keeps_the_body_fields_on_the_device() =
        runTest {
            val vm = viewModel()

            vm.chooseSex(Sex.Male)
            vm.save()

            assertEquals(Sex.Male, gym.profiles.forOwner(null)?.sex)
        }

    @Test
    fun weights_show_in_kilograms_until_another_unit_is_chosen_and_saved() =
        runTest {
            val vm = viewModel()
            assertEquals(PreferredWeightUnit.Kg, vm.ui.weightUnit)

            vm.chooseWeightUnit(PreferredWeightUnit.Lb)
            assertTrue(vm.ui.canSave)
            vm.chooseWeightUnit(PreferredWeightUnit.Kg)
            assertFalse(vm.ui.canSave)

            vm.chooseWeightUnit(PreferredWeightUnit.Mixed)
            vm.save()

            assertEquals(PreferredWeightUnit.Mixed, gym.profiles.forOwner(null)?.weightUnit)
            assertFalse(vm.ui.canSave)
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
                assertFalse(vm.ui.canSave, it)
            }
            vm.typeBirthDate("15.06.1990")
            assertTrue(vm.ui.birthDateValid)

            listOf("18", "1800", "сто").forEach {
                vm.typeHeight(it)
                assertFalse(vm.ui.heightValid, it)
                assertFalse(vm.ui.canSave, it)
            }
            vm.typeHeight("180")
            assertTrue(vm.ui.heightValid)
            assertTrue(vm.ui.canSave)
        }

    @Test
    fun clearing_a_field_clears_the_stored_value() =
        runTest {
            gym.profiles.upsert(Profile.new(null, t0).copy(heightCm = 180.0))
            val vm = viewModel()

            vm.typeHeight("")
            assertTrue(vm.ui.canSave)
            vm.save()

            assertNull(gym.profiles.forOwner(null)?.heightCm)
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
}
