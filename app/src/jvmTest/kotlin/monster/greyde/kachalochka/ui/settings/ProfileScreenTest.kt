package monster.greyde.kachalochka.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import monster.greyde.kachalochka.core.domain.gym.CalendarDay
import monster.greyde.kachalochka.core.domain.profile.PreferredWeightUnit
import monster.greyde.kachalochka.core.domain.profile.Sex
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.runScreenTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ProfileScreenTest {
    private val ivan =
        session("11111111-1111-4111-8111-111111111111", "Иван", Instant.fromEpochSeconds(0))

    @Test
    fun signed_out_shows_the_body_fields_but_no_nickname_or_avatar() {
        runScreenTest(FakeGym(), screen = { ProfileScreen(onBack = {}) }) {
            onNodeWithTag("nickname").assertDoesNotExist()
            onNodeWithTag("profile-avatar").assertDoesNotExist()
            onNodeWithTag("birth-date").assertExists()
            onNodeWithTag("height").assertExists()
            onNodeWithTag("profile-saved-note").assertExists()
        }
    }

    @Test
    fun the_body_fields_are_saved_when_the_screen_closes() {
        val gym = FakeGym()
        var backs = 0
        runScreenTest(gym, screen = { ProfileScreen(onBack = { backs++ }) }) {
            onNodeWithTag("sex-female").performClick()
            onNodeWithTag("sex-female").assertIsSelected()
            onNodeWithTag("birth-date").performTextInput("15.06.1990")
            onNodeWithTag("height").performTextInput("165")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
        }
        assertEquals(1, backs)
        val profile = runBlocking { gym.profiles.forOwner(null) }
        assertEquals(Sex.Female, profile?.sex)
        assertEquals(CalendarDay(1990, 6, 15), profile?.birthDate)
        assertEquals(165.0, profile?.heightCm)
    }

    @Test
    fun the_cycle_length_is_asked_once_the_sex_is_female() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { ProfileScreen(onBack = {}) }) {
            onNodeWithTag("cycle-length").assertDoesNotExist()
            onNodeWithTag("sex-female").performClick()
            onNodeWithTag("cycle-length").performScrollTo().performTextInput("30")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
        }
        assertEquals(30, runBlocking { gym.profiles.forOwner(null)?.cycleLength })
    }

    @Test
    fun a_chosen_weight_unit_is_saved_at_once() {
        val gym = FakeGym()
        runScreenTest(gym, screen = { ProfileScreen(onBack = {}) }) {
            onNodeWithTag("weight-unit-kg").assertIsSelected()
            onNodeWithTag("weight-unit-lb").performScrollTo().performClick()
            waitForIdle()
        }
        assertEquals(
            PreferredWeightUnit.Lb,
            runBlocking { gym.profiles.forOwner(null)?.weightUnit },
        )
    }

    @Test
    fun the_avatar_takes_a_photo_that_is_kept_at_once() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(gym, screen = { ProfileScreen(onBack = {}) }) {
            waitForIdle()
            onNodeWithTag("profile-avatar").performClick()
            onNodeWithTag("avatar-remove").assertDoesNotExist()
            onNodeWithTag("pick-photo").performClick()
            waitForIdle()

            onNodeWithTag("profile-avatar").performClick()
            onNodeWithTag("avatar-remove").assertExists()
        }
        val profile = runBlocking { gym.profiles.forOwner(ivan.account.userId) }
        assertEquals(true, profile?.avatarPhoto != null)
    }

    @Test
    fun a_typed_nickname_is_saved_on_leaving() {
        val gym = FakeGym().withAccounts(ivan, active = ivan)
        runScreenTest(gym, screen = { ProfileScreen(onBack = {}) }) {
            onNodeWithTag("nickname").performTextInput("Ванёк")
            onNodeWithTag("top-bar-back").performClick()
            waitForIdle()
        }
        assertEquals(
            "Ванёк",
            runBlocking { gym.profiles.forOwner(ivan.account.userId)?.displayName },
        )
    }
}
