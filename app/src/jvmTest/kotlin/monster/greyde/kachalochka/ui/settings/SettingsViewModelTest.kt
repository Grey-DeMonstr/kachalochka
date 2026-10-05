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
import monster.greyde.kachalochka.core.domain.gym.MachineId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.navigation.InMemoryTransitionPreference
import monster.greyde.kachalochka.ui.account.AccountAvatars
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.InMemoryLanguagePreference
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    private val themes = InMemoryThemePreference()
    private val transitions = InMemoryTransitionPreference()
    private val languages = InMemoryLanguagePreference()

    private fun viewModel() =
        SettingsViewModel(
            gym.profiles,
            gym.accounts,
            gym.currentUser,
            gym.clock,
            gym.utcOffset,
            gym.sync,
            gym.deletion,
            themes,
            transitions,
            languages,
            gym.photos,
            AccountAvatars(gym.accounts, gym.profiles),
        )

    private val SettingsViewModel.ui: ProfileUi get() = profile.value!!

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
    fun a_chosen_photo_waits_for_apply_then_becomes_the_profile_s_avatar() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val vm = viewModel()

            vm.chooseAvatar(byteArrayOf(7, 7))

            assertTrue(vm.canApply.value)
            assertNull(gym.profiles.forOwner(owner)?.avatarPhoto)

            vm.apply()

            val profile = assertNotNull(gym.profiles.forOwner(owner))
            val photo = gym.photos.rows.getValue(assertNotNull(profile.avatarPhoto))
            assertEquals(MachineId(profile.id.value), photo.machineId)
            assertEquals(owner, photo.userId)
            assertContentEquals(byteArrayOf(7, 7), gym.photos.bytes[photo.id])
            assertFalse(vm.canApply.value)
            assertEquals(
                photo.id,
                vm.avatar.value
                    ?.avatar
                    ?.photo,
            )
        }

    @Test
    fun removing_the_avatar_brings_the_google_picture_back() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val vm = viewModel()
            vm.chooseAvatar(byteArrayOf(7))
            vm.apply()
            val chosen = assertNotNull(gym.profiles.forOwner(owner)?.avatarPhoto)

            assertTrue(assertNotNull(vm.avatar.value).canRemove)
            vm.removeAvatar()
            vm.apply()

            assertNull(gym.profiles.forOwner(owner)?.avatarPhoto)
            assertTrue(
                gym.photos.rows
                    .getValue(chosen)
                    .deleted,
            )
        }

    @Test
    fun a_new_avatar_replaces_the_old_photo() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val vm = viewModel()
            vm.chooseAvatar(byteArrayOf(1))
            vm.apply()
            val first = assertNotNull(gym.profiles.forOwner(owner)?.avatarPhoto)

            vm.chooseAvatar(byteArrayOf(2))
            vm.apply()

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
                ProfileUi("Ванёк", "Иван", Sex.Male, "05.06.1990", "180.5", true, true, false),
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
            vm.apply()

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
            vm.apply()

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
            vm.apply()

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
            vm.apply()

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
            vm.apply()

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

    @Test
    fun deleting_is_offered_only_to_a_signed_in_account() =
        runTest {
            assertFalse(viewModel().deletion.value.available)

            gym.withAccounts(ivan, active = ivan)

            assertTrue(viewModel().deletion.value.available)
        }

    @Test
    fun a_confirmed_deletion_deletes_the_account_and_signs_it_out() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            val vm = viewModel()

            vm.askToDelete()
            assertTrue(vm.deletion.value.confirming)
            vm.typeDeleteWord(" delete ")
            vm.confirmDelete()

            assertEquals(listOf(ivan.account.userId), gym.accountServer.deleted)
            assertEquals(
                listOf(misha.account.userId),
                gym.accounts.accounts.value
                    .map { it.userId },
            )
            assertFalse(vm.deletion.value.confirming)
            assertNull(vm.deletion.value.error)
        }

    @Test
    fun offline_a_deletion_says_so_and_keeps_the_account() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.accountServer.offline = true
            val vm = viewModel()

            vm.askToDelete()
            vm.typeDeleteWord("DELETE")
            vm.confirmDelete()

            assertEquals("Нет связи с сервером", vm.deletion.value.error)
            assertEquals(ivan.account.userId, gym.accounts.activeId.value)
            assertFalse(vm.deletion.value.running)
        }

    @Test
    fun cancelling_keeps_the_account() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.askToDelete()
            vm.cancelDelete()

            assertFalse(vm.deletion.value.confirming)
            assertEquals(emptyList(), gym.accountServer.deleted)
        }

    @Test
    fun the_theme_and_the_transition_change_only_when_applied() =
        runTest {
            val vm = viewModel()
            assertFalse(vm.canApply.value)

            vm.chooseTheme(ThemeMode.Dark)
            vm.typeTransition("300")

            assertEquals(ThemeMode.System, themes.mode.value)
            assertEquals(150, transitions.millis.value)
            assertTrue(vm.canApply.value)
            vm.apply()

            assertEquals(ThemeMode.Dark, themes.mode.value)
            assertEquals(300, transitions.millis.value)
            assertFalse(vm.canApply.value)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_transition_out_of_range_is_refused() =
        runTest {
            val vm = viewModel()

            vm.typeTransition("1001")
            vm.typeTransition("abc")

            assertEquals("150", vm.device.value.transition)
            assertFalse(vm.canApply.value)
        }

    @Test
    fun an_invalid_profile_field_holds_back_every_change() =
        runTest {
            val vm = viewModel()

            vm.chooseTheme(ThemeMode.Light)
            vm.typeHeight("18")

            assertFalse(vm.canApply.value)
            vm.apply()
            assertEquals(ThemeMode.System, themes.mode.value)
        }

    @Test
    fun leaving_with_unapplied_changes_asks_first() =
        runTest {
            val vm = viewModel()
            assertTrue(vm.requestLeave())

            vm.chooseTheme(ThemeMode.Dark)

            assertFalse(vm.requestLeave())
            assertTrue(vm.confirmingLeave.value)
            vm.stay()
            assertFalse(vm.confirmingLeave.value)
            assertEquals(ThemeMode.Dark, vm.device.value.theme)
            vm.discard()
            assertEquals(ThemeMode.System, vm.device.value.theme)
            assertTrue(vm.requestLeave())
        }

    @Test
    fun the_advanced_section_opens_on_request() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()
            assertFalse(vm.deletion.value.advancedOpen)

            vm.toggleAdvanced()

            assertTrue(vm.deletion.value.advancedOpen)
        }

    @Test
    fun a_deletion_needs_the_word_typed() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.askToDelete()
            vm.typeDeleteWord("DELET")
            assertFalse(vm.deletion.value.canConfirm)
            vm.confirmDelete()

            assertEquals(emptyList(), gym.accountServer.deleted)
            vm.typeDeleteWord("DELETE")
            assertTrue(vm.deletion.value.canConfirm)
        }

    @Test
    fun the_language_changes_only_when_applied() =
        runTest {
            val vm = viewModel()

            vm.chooseLanguage(AppLanguage.English)

            assertEquals(AppLanguage.System, languages.language.value)
            assertTrue(vm.canApply.value)
            vm.apply()
            assertEquals(AppLanguage.English, languages.language.value)
        }

    @Test
    fun a_managed_child_keeps_only_the_settings_of_the_device() =
        runTest {
            gym.withAccounts(ivan, active = ivan).withChild(childAccount(SASHA, ivan))
            gym.accounts.switchTo(SASHA.userId)

            val vm = viewModel()

            assertNull(vm.profile.value)
            assertNull(vm.avatar.value)
            assertFalse(vm.deletion.value.available)
            assertFalse(vm.familyAvailable.value)
        }

    @Test
    fun applying_for_a_managed_child_changes_the_device_and_writes_no_profile() =
        runTest {
            gym.withAccounts(ivan, active = ivan).withChild(childAccount(SASHA, ivan))
            gym.accounts.switchTo(SASHA.userId)
            val vm = viewModel()

            vm.chooseLanguage(AppLanguage.English)
            vm.apply()

            assertEquals(AppLanguage.English, languages.language.value)
            assertNull(gym.profiles.forOwner(SASHA.userId))
        }

    private class GatedLanguages(
        val gate: kotlinx.coroutines.CompletableDeferred<Unit>,
    ) : monster.greyde.kachalochka.ui.strings.LanguagePreference {
        private val state = kotlinx.coroutines.flow.MutableStateFlow(AppLanguage.System)
        override val language: kotlinx.coroutines.flow.StateFlow<AppLanguage> = state

        override suspend fun set(language: AppLanguage) {
            gate.await()
            state.value = language
        }
    }

    @Test
    fun a_switch_to_a_managed_child_while_applying_writes_no_profile_for_the_child() =
        runTest {
            gym.withAccounts(ivan, active = ivan).withChild(childAccount(SASHA, ivan))
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val vm =
                SettingsViewModel(
                    gym.profiles,
                    gym.accounts,
                    gym.currentUser,
                    gym.clock,
                    gym.utcOffset,
                    gym.sync,
                    gym.deletion,
                    themes,
                    transitions,
                    GatedLanguages(gate),
                    gym.photos,
                    AccountAvatars(gym.accounts, gym.profiles),
                )
            vm.type("Ваня")
            vm.apply()

            gym.accounts.switchTo(SASHA.userId)
            gate.complete(Unit)

            assertNull(gym.profiles.forOwner(SASHA.userId))
            assertNull(gym.profiles.forOwner(ivan.account.userId))
        }
}
