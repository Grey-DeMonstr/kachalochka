package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.friends.FriendGroup
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

    private fun viewModel(group: FriendGroup) =
        GroupViewModel(group.id, gym.friends, gym.invites, gym.currentUser, gym.accounts)

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

        viewModel(group).invite()

        assertEquals(
            null,
            gym.invites.shared
                .single()
                .link,
        )
    }
}
