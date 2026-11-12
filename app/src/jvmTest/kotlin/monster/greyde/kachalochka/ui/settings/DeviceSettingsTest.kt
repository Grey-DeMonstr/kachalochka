package monster.greyde.kachalochka.ui.settings

import kotlinx.coroutines.test.runTest
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.navigation.DEFAULT_TRANSITION_MILLIS
import monster.greyde.kachalochka.navigation.TransitionPreference
import monster.greyde.kachalochka.ui.calendar.FeedPeoplePreference
import monster.greyde.kachalochka.ui.calendar.SelectedPerson
import monster.greyde.kachalochka.ui.calendar.VisitsMode
import monster.greyde.kachalochka.ui.calendar.VisitsModePreference
import monster.greyde.kachalochka.ui.network.ProxyPreference
import monster.greyde.kachalochka.ui.network.ProxySetting
import monster.greyde.kachalochka.ui.strings.AppLanguage
import monster.greyde.kachalochka.ui.strings.LanguagePreference
import monster.greyde.kachalochka.ui.theme.ThemeMode
import monster.greyde.kachalochka.ui.theme.ThemePreference
import monster.greyde.kachalochka.ui.timer.RestSound
import monster.greyde.kachalochka.ui.timer.RestSoundPreference
import monster.greyde.kachalochka.ui.visit.SetCommentPreference
import monster.greyde.kachalochka.ui.visit.ShowEveryone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceSettingsTest {
    private companion object {
        const val VIEWER = "0f8fad5b-d9cb-469f-a165-70867728950e"
        const val PERSON = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    }

    private val store = InMemoryDeviceStore()
    private val person = SelectedPerson(UserId(VIEWER), UserId(PERSON))

    @Test
    fun an_empty_store_gives_every_default() {
        assertEquals(ThemeMode.System, ThemePreference(store).mode.value)
        assertEquals(DEFAULT_TRANSITION_MILLIS, TransitionPreference(store).millis.value)
        assertEquals(AppLanguage.System, LanguagePreference(store).language.value)
        assertEquals(VisitsMode.Calendar, VisitsModePreference(store).mode.value)
        assertEquals(emptySet(), FeedPeoplePreference(store).selected.value)
        assertEquals(RestSound(), RestSoundPreference(store).sound.value)
        assertFalse(SetCommentPreference(store).shown.value)
        assertEquals(ProxySetting(), ProxyPreference(store).setting.value)
    }

    @Test
    fun a_set_value_is_in_the_flow_and_in_the_store_under_its_key_and_type() =
        runTest {
            val theme = ThemePreference(store)
            val transition = TransitionPreference(store)
            val language = LanguagePreference(store)
            val visitsMode = VisitsModePreference(store)
            val feed = FeedPeoplePreference(store)
            val restSound = RestSoundPreference(store)
            val comment = SetCommentPreference(store)

            theme.set(ThemeMode.Dark)
            transition.set(300)
            language.set(AppLanguage.English)
            visitsMode.set(VisitsMode.Feed)
            feed.set(setOf(person))
            restSound.set(RestSound(true, "content://sound/7"))
            comment.set(true)

            assertEquals(ThemeMode.Dark, theme.mode.value)
            assertEquals(300, transition.millis.value)
            assertEquals(AppLanguage.English, language.language.value)
            assertEquals(VisitsMode.Feed, visitsMode.mode.value)
            assertEquals(setOf(person), feed.selected.value)
            assertEquals(RestSound(true, "content://sound/7"), restSound.sound.value)
            assertTrue(comment.shown.value)
            assertEquals("Dark", store.read(DeviceKey.Text("theme_mode")))
            assertEquals(300, store.read(DeviceKey.Number("transition_millis")))
            assertEquals("English", store.read(DeviceKey.Text("language")))
            assertEquals("Feed", store.read(DeviceKey.Text("visits_mode")))
            assertEquals(
                setOf("$VIEWER/$PERSON"),
                store.read(DeviceKey.TextSet("feed_selected_person")),
            )
            assertEquals(true, store.read(DeviceKey.Flag("rest_sound")))
            assertEquals("content://sound/7", store.read(DeviceKey.Text("rest_sound_uri")))
            assertEquals(true, store.read(DeviceKey.Flag("set_comment_shown")))
        }

    @Test
    fun a_second_instance_over_the_same_store_reads_the_values_back() =
        runTest {
            ThemePreference(store).set(ThemeMode.Light)
            TransitionPreference(store).set(0)
            LanguagePreference(store).set(AppLanguage.English)
            VisitsModePreference(store).set(VisitsMode.Feed)
            FeedPeoplePreference(store).set(setOf(person))
            RestSoundPreference(store).set(RestSound(true, "content://sound/7"))
            SetCommentPreference(store).set(true)
            ProxyPreference(store).set(ProxySetting("192.0.2.7:3128", true))

            assertEquals(ThemeMode.Light, ThemePreference(store).mode.value)
            assertEquals(0, TransitionPreference(store).millis.value)
            assertEquals(AppLanguage.English, LanguagePreference(store).language.value)
            assertEquals(VisitsMode.Feed, VisitsModePreference(store).mode.value)
            assertEquals(setOf(person), FeedPeoplePreference(store).selected.value)
            assertEquals(
                RestSound(true, "content://sound/7"),
                RestSoundPreference(store).sound.value,
            )
            assertTrue(SetCommentPreference(store).shown.value)
            assertEquals(
                ProxySetting("192.0.2.7:3128", true),
                ProxyPreference(store).setting.value,
            )
        }

    @Test
    fun a_stored_value_that_cannot_be_decoded_gives_the_default() =
        runTest {
            store.write(DeviceKey.Text("theme_mode"), "Sepia")
            store.write(DeviceKey.Text("language"), "Klingon")
            store.write(DeviceKey.Text("visits_mode"), "List")
            store.write(DeviceKey.TextSet("feed_selected_person"), setOf("broken", "a/b/c"))

            assertEquals(ThemeMode.System, ThemePreference(store).mode.value)
            assertEquals(AppLanguage.System, LanguagePreference(store).language.value)
            assertEquals(VisitsMode.Calendar, VisitsModePreference(store).mode.value)
            assertEquals(emptySet(), FeedPeoplePreference(store).selected.value)
        }

    @Test
    fun a_broken_feed_pair_does_not_hide_the_good_ones() =
        runTest {
            store.write(
                DeviceKey.TextSet("feed_selected_person"),
                setOf("broken", "$VIEWER/$PERSON"),
            )

            assertEquals(setOf(person), FeedPeoplePreference(store).selected.value)
        }

    @Test
    fun the_rest_sound_uri_is_removed_when_it_is_set_to_null() =
        runTest {
            val preference = RestSoundPreference(store)
            preference.set(RestSound(true, "content://sound/7"))

            preference.set(RestSound(true, null))

            assertEquals(RestSound(true, null), preference.sound.value)
            assertNull(store.read(DeviceKey.Text("rest_sound_uri")))
        }

    @Test
    fun the_proxy_keeps_all_its_parts() =
        runTest {
            ProxyPreference(store).set(ProxySetting("192.0.2.7:3128", true, "ivan", "secret"))

            assertEquals("192.0.2.7:3128", store.read(DeviceKey.Text("proxy_address")))
            assertEquals(true, store.read(DeviceKey.Flag("proxy_used")))
            assertEquals("ivan", store.read(DeviceKey.Text("proxy_login")))
            assertEquals("secret", store.read(DeviceKey.Text("proxy_password")))
            assertEquals(
                ProxySetting("192.0.2.7:3128", true, "ivan", "secret"),
                ProxyPreference(store).setting.value,
            )
        }

    @Test
    fun show_everyone_starts_clear() {
        assertFalse(ShowEveryone().checked)
    }
}
