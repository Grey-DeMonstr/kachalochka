package monster.greyde.kachalochka.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.core.domain.profile.ageOn
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.navigation.InMemoryTransitionPreference
import monster.greyde.kachalochka.ui.family.IVAN_MEMBER
import monster.greyde.kachalochka.ui.family.PAPA
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.family.childAccount
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.InMemoryLanguagePreference
import monster.greyde.kachalochka.ui.theme.InMemoryThemePreference
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.timer.InMemoryRestSoundPreference
import monster.greyde.kachalochka.ui.timer.RestSound
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val gym = FakeGym()
    private val t0 = gym.clock.current
    private val ivan = session("11111111-1111-4111-8111-111111111111", "Иван", t0)

    private val themes = InMemoryThemePreference()
    private val transitions = InMemoryTransitionPreference()
    private val languages = InMemoryLanguagePreference()
    private val restSounds = InMemoryRestSoundPreference()

    private fun viewModel() =
        SettingsViewModel(
            gym.profiles,
            gym.accounts,
            gym.currentUser,
            gym.deviceClock,
            gym.family,
            themes,
            transitions,
            languages,
            restSounds,
        )

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nobody_signed_in_sees_the_profile_card_but_no_people_or_account() =
        runTest {
            val hub = viewModel().hub.value

            val card = assertNotNull(hub.card)
            assertNull(card.owner)
            assertEquals("", card.name)
            assertNull(hub.people)
            assertNull(hub.email)
        }

    @Test
    fun the_card_sums_the_profile_up() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.profiles.upsert(
                Profile.new(ivan.account.userId, t0).copy(
                    displayName = "ГДМ",
                    sex = Sex.Male,
                    birthDate = CalendarDay(1990, 6, 5),
                    heightCm = 174.0,
                    weightUnit = PreferredWeightUnit.Lb,
                ),
            )

            val card = assertNotNull(viewModel().hub.value.card)

            assertEquals(ivan.account.userId, card.owner)
            assertEquals("ГДМ", card.name)
            assertEquals(Sex.Male, card.sex)
            val today = CalendarDay.of(t0, gym.utcOffset.at(t0))
            assertEquals(ageOn(CalendarDay(1990, 6, 5), today), card.age)
            assertEquals(174.0, card.heightCm)
            assertEquals(PreferredWeightUnit.Lb, card.weightUnit)
        }

    @Test
    fun without_a_nickname_the_card_shows_the_google_name() =
        runTest {
            gym.withAccounts(ivan, active = ivan)

            assertEquals(
                "Иван",
                viewModel()
                    .hub.value.card
                    ?.name,
            )
        }

    @Test
    fun a_signed_in_account_sees_its_email_and_how_many_people_it_is_linked_to() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.family.link(SASHA, IVAN_MEMBER)
            gym.family.link(IVAN_MEMBER, PAPA)

            val hub = viewModel().hub.value

            assertEquals("Иван@example.test", hub.email)
            assertEquals(PeopleUi(children = 1, guardians = 1), hub.people)
        }

    @Test
    fun offline_the_people_row_stays_without_counts() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.family.offline = true

            assertEquals(PeopleUi(null, null), viewModel().hub.value.people)
        }

    @Test
    fun a_refresh_shows_what_changed_meanwhile() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()
            gym.profiles.upsert(Profile.new(ivan.account.userId, t0).copy(heightCm = 180.0))
            gym.family.link(SASHA, IVAN_MEMBER)

            vm.refresh()

            assertEquals(
                180.0,
                vm.hub.value.card
                    ?.heightCm,
            )
            assertEquals(
                1,
                vm.hub.value.people
                    ?.children,
            )
        }

    @Test
    fun a_managed_child_keeps_only_the_settings_of_the_device() =
        runTest {
            gym.withAccounts(ivan, active = ivan).withChild(childAccount(SASHA, ivan))
            gym.accounts.switchTo(SASHA.userId)

            assertEquals(HubUi(), viewModel().hub.value)
        }

    @Test
    fun the_theme_and_the_language_change_at_once() =
        runTest {
            val vm = viewModel()

            vm.chooseTheme(ThemeMode.Dark)
            vm.chooseLanguage(AppLanguage.English)

            assertEquals(ThemeMode.Dark, themes.mode.value)
            assertEquals(AppLanguage.English, languages.language.value)
            assertEquals(ThemeMode.Dark, vm.device.value.theme)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun the_transition_is_one_of_the_presets_and_changes_at_once() =
        runTest {
            val vm = viewModel()
            assertEquals(TransitionPreset.Fast, vm.device.value.transition)

            vm.chooseTransition(TransitionPreset.Smooth)

            assertEquals(500, transitions.millis.value)
            assertEquals(TransitionPreset.Smooth, vm.device.value.transition)
        }

    @Test
    fun a_stored_length_between_the_presets_shows_as_the_nearest() =
        runTest {
            transitions.set(380)

            assertEquals(TransitionPreset.Normal, viewModel().device.value.transition)
        }

    @Test
    fun the_timer_sound_is_off_and_switches_on_at_once() =
        runTest {
            val vm = viewModel()

            vm.chooseRestSound(true)

            assertEquals(RestSound(enabled = true), restSounds.sound.value)
        }

    @Test
    fun a_picked_sound_is_kept_at_once() =
        runTest {
            restSounds.set(RestSound(enabled = true))
            val vm = viewModel()

            vm.pickRestSound("content://sound/7")

            assertEquals(RestSound(true, "content://sound/7"), restSounds.sound.value)
            assertEquals("content://sound/7", vm.device.value.restSound.uri)
        }
}
