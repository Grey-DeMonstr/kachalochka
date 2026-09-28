package monster.greyde.kachalochka.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        SettingsViewModel(gym.profiles, gym.accounts, gym.currentUser, gym.clock, gym.sync)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nobody_signed_in_means_no_nickname_field() =
        runTest {
            val vm = viewModel()

            assertNull(vm.nickname.value)
        }

    @Test
    fun signing_in_shows_the_account_name_as_placeholder_and_the_stored_nickname() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            gym.profiles.upsert(
                Profile.new(ivan.account.userId, t0).copy(displayName = "Ванёк"),
            )

            val vm = viewModel()

            assertEquals("Ванёк", vm.nickname.value?.text)
            assertEquals("Иван", vm.nickname.value?.placeholder)
            assertEquals(false, vm.nickname.value?.canSave)
        }

    @Test
    fun typing_truncates_to_forty_characters_and_enables_save_only_when_changed() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.type("a".repeat(50))

            assertEquals(
                NICKNAME_LENGTH,
                vm.nickname.value
                    ?.text
                    ?.length,
            )
            assertEquals(true, vm.nickname.value?.canSave)

            vm.type("")

            assertEquals(false, vm.nickname.value?.canSave)
        }

    @Test
    fun saving_writes_a_new_profile_when_the_owner_has_none_and_asks_for_a_sync() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val vm = viewModel()

            vm.type("Ванёк")
            vm.save()

            val profile = gym.profiles.forOwner(ivan.account.userId)
            assertNotNull(profile)
            assertEquals(ivan.account.userId.value, profile.id.value)
            assertEquals("Ванёк", profile.displayName)
            assertEquals(1, gym.sync.requests)
            assertEquals(false, vm.nickname.value?.canSave)
        }

    @Test
    fun saving_updates_the_existing_profile_with_a_fresh_timestamp() =
        runTest {
            gym.withAccounts(ivan, active = ivan)
            val owner = ivan.account.userId
            val original = Profile.new(owner, t0).copy(displayName = "Old")
            gym.profiles.upsert(original)
            val vm = viewModel()
            gym.clock.current += kotlin.time.Duration.parse("PT1H")

            vm.type("New")
            vm.save()

            val profile = gym.profiles.forOwner(owner)
            assertNotNull(profile)
            assertEquals(original.id, profile.id)
            assertEquals("New", profile.displayName)
            assertEquals(gym.clock.current, profile.updatedAt)
        }

    @Test
    fun switching_the_active_account_reloads_the_nickname() =
        runTest {
            gym.withAccounts(ivan, misha, active = ivan)
            gym.profiles.upsert(
                Profile.new(misha.account.userId, t0).copy(displayName = "Мишка"),
            )
            val vm = viewModel()

            gym.accounts.switchTo(misha.account.userId)

            assertEquals("Мишка", vm.nickname.value?.text)
            assertEquals("Миша", vm.nickname.value?.placeholder)
        }
}
