package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.domain.friends.FRIEND_PALETTE_SIZE
import monster.greyde.kachalochka.core.domain.friends.Friend
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.fakes.FakeGym
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {
    private val gym = signedInGym()

    private fun viewModel(
        group: FriendGroup,
        gym: FakeGym = this.gym,
        profiles: ProfileRepository = gym.profiles,
    ) = GroupViewModel(
        group.id,
        gym.friends,
        gym.invites,
        gym.currentUser,
        gym.accounts,
        FriendColorStore(profiles, gym.clock, Random(1)),
        gym.sync,
    )

    /** [profiles] that fail every read while [failing] is set. */
    private class FlakyProfiles(
        private val profiles: ProfileRepository,
    ) : ProfileRepository by profiles {
        var failing = false

        override suspend fun forOwner(owner: UserId?): Profile? {
            if (failing) error("no connection")
            return profiles.forOwner(owner)
        }
    }

    @Test
    fun a_failed_colour_read_after_a_switch_shows_none_of_the_previous_account_s() =
        runTest {
            val misha =
                AccountSession(
                    Account(
                        UserId("22222222-2222-4222-8222-222222222222"),
                        "m@example.test",
                        "Миша",
                    ),
                    "access",
                    "refresh",
                    gym.clock.current,
                )
            val two = FakeGym().withAccounts(IVAN_SESSION, misha, active = IVAN_SESSION)
            val mishaFriend = Friend(misha.account.userId, "Миша")
            val group = two.friends.group("Зал на Лесной", owner = OLEG, ME, mishaFriend)
            two.profiles.upsert(
                Profile
                    .new(ME.userId, two.clock.current)
                    .copy(friendColors = mapOf(OLEG.userId to 4)),
            )
            val profiles = FlakyProfiles(two.profiles)
            val vm = viewModel(group, two, profiles)
            assertEquals(
                4,
                vm.state.value
                    ?.members
                    ?.single { it.friend == OLEG }
                    ?.color,
            )
            profiles.failing = true

            two.accounts.switchTo(misha.account.userId)

            assertNull(
                vm.state.value
                    ?.members
                    ?.single { it.friend == OLEG }
                    ?.color,
            )
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_group_shows_its_members_owner_first_and_its_code() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, PASHA, ME, code = "ABCD2345")

        val state = assertNotNull(viewModel(group).state.value)

        assertEquals("Зал на Лесной", state.title)
        assertEquals("ABCD2345", state.code)
        assertEquals(
            listOf(
                Triple("Олег", true, true),
                Triple("Иван", false, false),
                Triple("Паша", false, true),
            ),
            state.members.map { Triple(it.friend.displayName, it.owner, it.opens) },
        )
        assertFalse(state.isOwner)
    }

    @Test
    fun every_member_but_the_viewer_shows_their_stored_colour() =
        runTest {
            gym.profiles.upsert(
                Profile
                    .new(ME.userId, gym.clock.current)
                    .copy(friendColors = mapOf(OLEG.userId to 4)),
            )
            val group = gym.friends.group("Зал на Лесной", owner = OLEG, PASHA, ME)

            val state = assertNotNull(viewModel(group).state.value)

            val colors = state.members.associate { it.friend.userId to it.color }
            assertEquals(4, colors[OLEG.userId])
            assertNull(colors[ME.userId])
            val pasha = assertNotNull(colors[PASHA.userId])
            assertTrue(pasha in 0 until FRIEND_PALETTE_SIZE && pasha != 4)
            assertEquals(
                mapOf(OLEG.userId to 4, PASHA.userId to pasha),
                gym.profiles.forOwner(ME.userId)?.friendColors,
            )
        }

    @Test
    fun a_chosen_colour_is_stored_and_shown() =
        runTest {
            val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            val vm = viewModel(group)

            vm.pickColor(OLEG.userId)
            assertEquals(OLEG.userId, vm.state.value?.colorPicker)
            vm.chooseColor(6)

            val state = assertNotNull(vm.state.value)
            assertNull(state.colorPicker)
            assertEquals(6, state.members.single { it.friend == OLEG }.color)
            assertEquals(
                6,
                gym.profiles
                    .forOwner(ME.userId)
                    ?.friendColors
                    ?.get(OLEG.userId),
            )
            assertEquals(1, gym.sync.requests)
        }

    @Test
    fun dismissing_the_palette_keeps_the_colour() =
        runTest {
            val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
            val vm = viewModel(group)
            val before =
                vm.state.value
                    ?.members
                    ?.single { it.friend == OLEG }
                    ?.color

            vm.pickColor(OLEG.userId)
            vm.dismissColor()

            val state = assertNotNull(vm.state.value)
            assertNull(state.colorPicker)
            assertEquals(before, state.members.single { it.friend == OLEG }.color)
            assertEquals(0, gym.sync.requests)
        }

    @Test
    fun a_member_leaves_once_they_confirm() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        val vm = viewModel(group)

        vm.askToGo()

        assertEquals(
            GroupConfirmUi(
                "Выйти из группы?",
                "Вы перестанете видеть визиты участников, а они — ваши.",
                "Выйти",
            ),
            vm.state.value?.confirming,
        )
        vm.confirm()
        assertTrue(vm.gone.value)
        assertEquals(listOf(OLEG), gym.friends.members.getValue(group.id))
    }

    @Test
    fun the_owner_deletes_the_group_once_they_confirm() {
        val group = gym.friends.group("Зал на Лесной", owner = ME, OLEG)
        val vm = viewModel(group)
        assertTrue(assertNotNull(vm.state.value).isOwner)

        vm.askToGo()

        assertEquals(
            GroupConfirmUi(
                "Удалить группу?",
                "Участники перестанут видеть визиты друг друга.",
                "Удалить",
            ),
            vm.state.value?.confirming,
        )
        vm.confirm()
        assertTrue(vm.gone.value)
        assertFalse(group.id in gym.friends.groups)
    }

    @Test
    fun cancelling_keeps_the_group() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        val vm = viewModel(group)
        vm.askToGo()

        vm.cancel()

        assertNull(vm.state.value?.confirming)
        assertEquals(
            2,
            gym.friends.members
                .getValue(group.id)
                .size,
        )
    }

    @Test
    fun a_group_the_account_is_not_in_is_gone() {
        val group = gym.friends.group("Чужая", owner = OLEG)

        assertTrue(viewModel(group).gone.value)
    }

    @Test
    fun a_leave_that_fails_keeps_the_group_and_says_so() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        val vm = viewModel(group)
        vm.askToGo()
        gym.friends.offline = true

        vm.confirm()

        assertFalse(vm.gone.value)
        assertNull(vm.state.value?.confirming)
        assertEquals("Нет связи с сервером", vm.state.value?.notice)
    }

    @Test
    fun offline_the_group_says_so_and_a_retry_reads_it_again() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.offline = true
        val vm = viewModel(group)
        assertTrue(vm.offline.value)

        gym.friends.offline = false
        vm.refresh()

        assertEquals("Зал на Лесной", vm.state.value?.title)
    }

    @Test
    fun inviting_hands_the_group_s_link_and_code_over_and_shows_what_came_back() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME, code = "ABCD2345")
        val vm = viewModel(group)

        vm.invite()

        assertEquals(
            listOf(
                Invite(
                    "Зал на Лесной",
                    "ABCD2345",
                    "https://example.test/kachalochka/?join=ABCD2345",
                ),
            ),
            gym.invites.shared,
        )
        assertEquals("Ссылка скопирована", vm.state.value?.notice)
    }

    @Test
    fun without_an_address_only_the_code_is_shared() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, ME, code = "ABCD2345")
        gym.invites.pageAddress = null
        gym.invites.notice = null

        val vm = viewModel(group).also { it.invite() }

        assertEquals(listOf(Invite("Зал на Лесной", "ABCD2345", null)), gym.invites.shared)
        // The share sheet speaks for itself, so the screen adds no notice.
        assertNull(assertNotNull(vm.state.value).notice)
    }
}
