package monster.greyde.kachalochka.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.core.domain.profile.ageOn
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import monster.greyde.kachalochka.runScreenTestInEnglish
import monster.greyde.kachalochka.ui.family.IVAN_MEMBER
import monster.greyde.kachalochka.ui.family.PAPA
import monster.greyde.kachalochka.ui.family.SASHA
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.RuStrings
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.timer.RestSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    private val ivan =
        session("11111111-1111-4111-8111-111111111111", "Иван", Instant.fromEpochSeconds(0))

    @Test
    fun the_hub_opens_the_profile_the_people_and_the_account() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        val opened = mutableListOf<String>()
        runScreenTest(
            gym,
            screen = {
                SettingsScreen(
                    onBack = {},
                    onOpenProfile = { opened += "profile" },
                    onOpenPeople = { opened += "people" },
                    onOpenAccount = { opened += "account" },
                )
            },
        ) {
            onNodeWithTag("settings-profile").performClick()
            onNodeWithTag("settings-people").performClick()
            onNodeWithTag("settings-account").performClick()
        }
        assertEquals(listOf("profile", "people", "account"), opened)
    }

    @Test
    fun the_card_and_the_rows_show_the_current_values() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runBlocking {
            gym.profiles.upsert(
                Profile.new(ivan.account.userId, gym.clock.current).copy(
                    displayName = "ГДМ",
                    sex = Sex.Male,
                    birthDate = CalendarDay(1900, 1, 1),
                    heightCm = 174.0,
                ),
            )
        }
        gym.family.link(SASHA, IVAN_MEMBER)
        gym.family.link(IVAN_MEMBER, PAPA)
        val age = RuStrings.years(ageOn(CalendarDay(1900, 1, 1), gym.today))
        runScreenTest(gym, screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("settings-profile-name", useUnmergedTree = true).assertTextEquals("ГДМ")
            onNodeWithTag("settings-profile-line", useUnmergedTree = true)
                .assertTextEquals("Мужской · $age · 174 см · кг")
            onNodeWithTag("settings-theme-value", useUnmergedTree = true)
                .assertTextEquals("Системная")
            onNodeWithTag("settings-animation-value", useUnmergedTree = true)
                .assertTextEquals("Быстрая")
            onNodeWithTag("settings-rest-sound-value", useUnmergedTree = true)
                .assertTextEquals("Выключен")
            onNodeWithTag("settings-people-counts", useUnmergedTree = true)
                .assertTextEquals("1 ребёнок · 1 родитель")
            onNodeWithTag("settings-account-value", useUnmergedTree = true)
                .assertTextEquals("Иван@example.test")
        }
    }

    @Test
    fun without_an_account_there_are_no_people_and_no_account() {
        runScreenTest(FakeGym(), screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("settings-profile-name", useUnmergedTree = true)
                .assertTextEquals("Профиль")
            onNodeWithTag("settings-people").assertDoesNotExist()
            onNodeWithTag("settings-account").assertDoesNotExist()
        }
    }

    @Test
    fun a_theme_chosen_in_the_sheet_applies_and_closes_it() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("settings-theme").performClick()
            waitForIdle()
            onNodeWithTag("theme-system").assertIsSelected()

            onNodeWithTag("theme-dark").performClick()
            waitForIdle()

            onNodeWithTag("theme-dark").assertDoesNotExist()
            onNodeWithTag("settings-theme-value", useUnmergedTree = true)
                .assertTextEquals("Тёмная")
        }
        assertEquals(ThemeMode.Dark, gym.themes.mode.value)
    }

    @Test
    fun the_language_is_chosen_in_a_sheet() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("settings-language").performClick()
            waitForIdle()
            onNodeWithTag("language-russian").performClick()
            waitForIdle()
        }
        assertEquals(AppLanguage.Russian, gym.languages.language.value)
    }

    @Test
    fun the_animation_offers_four_lengths() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("settings-animation").performClick()
            waitForIdle()
            onNodeWithTag("transition-fast").assertIsSelected()
            onNodeWithTag("transition-none").assertExists()
            onNodeWithTag("transition-normal").assertExists()

            onNodeWithTag("transition-smooth").performClick()
            waitForIdle()

            onNodeWithTag("settings-animation-value", useUnmergedTree = true)
                .assertTextEquals("Плавная")
        }
        assertEquals(500, gym.transitions.millis.value)
    }

    @Test
    fun switching_the_timer_sound_on_asks_for_notifications() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("settings-rest-sound").performClick()
            waitForIdle()
            onNodeWithTag("rest-sound-off").assertIsSelected()
            onNodeWithTag("rest-sound-pick").assertDoesNotExist()

            onNodeWithTag("rest-sound-on").performClick()
            waitForIdle()

            onNodeWithTag("settings-rest-sound-value", useUnmergedTree = true)
                .assertTextEquals("Включён")
        }
        assertEquals(1, gym.restSoundChooser.notificationAsks)
        assertEquals(true, gym.restSounds.sound.value.enabled)
    }

    @Test
    fun a_picked_timer_sound_shows_its_name() {
        val gym = FakeGym()
        runBlocking { gym.restSounds.set(RestSound(enabled = true)) }
        runScreenTest(gym, screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("settings-rest-sound").performClick()
            waitForIdle()
            onNodeWithTag("rest-sound-name", useUnmergedTree = true)
                .assertTextEquals("По умолчанию")

            onNodeWithTag("rest-sound-pick").performClick()
            waitForIdle()

            onNodeWithTag("rest-sound-name", useUnmergedTree = true)
                .assertTextEquals("content://sound/7")
        }
        assertEquals(RestSound(true, "content://sound/7"), gym.restSounds.sound.value)
        assertEquals(0, gym.restSoundChooser.notificationAsks)
    }

    @Test
    fun the_settings_speak_english() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTestInEnglish(gym, screen = { SettingsScreen(onBack = {}) }) {
            onNodeWithTag("top-bar-title").assertTextEquals("Settings")
            onNodeWithTag("settings-title").assertTextEquals("APP")
            onNodeWithTag("settings-theme-value", useUnmergedTree = true)
                .assertTextEquals("System")
            onNodeWithTag("settings-animation-value", useUnmergedTree = true)
                .assertTextEquals("Fast")
        }
    }
}
