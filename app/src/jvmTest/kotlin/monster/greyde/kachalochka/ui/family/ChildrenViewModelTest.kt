package monster.greyde.kachalochka.ui.family

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.Invite
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChildrenViewModelTest {
    private val gym = signedInGym()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChildrenViewModel(gym.family, gym.invites, gym.accounts)

    @Test
    fun the_list_shows_the_account_s_children() {
        gym.family.link(SASHA, IVAN_MEMBER)

        assertEquals(listOf(SASHA), viewModel().state.value.children)
    }

    @Test
    fun offline_the_screen_says_so() {
        gym.family.offline = true

        assertTrue(viewModel().state.value.offline)
    }

    @Test
    fun adding_a_child_shows_a_new_code_of_the_account() {
        val vm = viewModel()

        vm.addChild()

        assertEquals("PAPA2345", vm.state.value.code)
        assertEquals(IVAN_MEMBER.userId, gym.family.codes["PAPA2345"])
    }

    @Test
    fun adding_a_child_shows_the_code_s_link_when_the_page_address_is_known() {
        val vm = viewModel()

        vm.addChild()

        assertEquals(
            "https://example.test/kachalochka/?parent=PAPA2345",
            vm.state.value.link,
        )
    }

    @Test
    fun without_a_page_address_only_the_code_is_shown() {
        gym.invites.pageAddress = null
        val vm = viewModel()

        vm.addChild()

        assertEquals("PAPA2345", vm.state.value.code)
        assertNull(vm.state.value.link)
    }

    @Test
    fun offline_no_code_is_shown_and_the_screen_says_so() {
        val vm = viewModel()
        gym.family.offline = true

        vm.addChild()

        assertNull(vm.state.value.code)
        assertEquals("Нет связи с сервером", vm.state.value.notice)
    }

    @Test
    fun sharing_hands_the_code_over_with_its_link() {
        val vm = viewModel().also { it.addChild() }

        vm.share()

        assertEquals(
            listOf(
                Invite(
                    "Добавь меня родителем в Качалочке",
                    "PAPA2345",
                    "https://example.test/kachalochka/?parent=PAPA2345",
                ),
            ),
            gym.invites.shared,
        )
        assertEquals("Ссылка скопирована", vm.state.value.notice)
    }

    @Test
    fun removing_a_child_asks_first_then_ends_the_link() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()

        vm.askToRemove(SASHA)
        assertEquals(SASHA, vm.state.value.removing)
        assertEquals(1, gym.family.links.size)

        vm.confirmRemove()

        assertTrue(gym.family.links.isEmpty())
        assertEquals(emptyList(), vm.state.value.children)
        assertNull(vm.state.value.removing)
    }

    @Test
    fun a_removal_offline_changes_nothing_and_says_so() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()
        gym.family.offline = true

        vm.askToRemove(SASHA)
        vm.confirmRemove()

        assertEquals(1, gym.family.links.size)
        assertEquals("Нет связи с сервером", vm.state.value.notice)
    }

    @Test
    fun switching_accounts_drops_the_shown_children_and_code() {
        val two = FakeGym().withAccounts(IVAN_SESSION, OLGA_SESSION, active = IVAN_SESSION)
        two.family.link(SASHA, IVAN_MEMBER)
        val vm = ChildrenViewModel(two.family, two.invites, two.accounts).also { it.addChild() }
        assertEquals(listOf(SASHA), vm.state.value.children)

        runBlocking { two.accounts.switchTo(OLGA_SESSION.account.userId) }

        assertEquals(emptyList(), vm.state.value.children)
        assertNull(vm.state.value.code)
    }

    @Test
    fun cancelling_a_removal_closes_the_question_and_keeps_the_link() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()

        vm.askToRemove(SASHA)
        vm.cancelRemove()

        assertNull(vm.state.value.removing)
        assertEquals(1, gym.family.links.size)
    }
}
