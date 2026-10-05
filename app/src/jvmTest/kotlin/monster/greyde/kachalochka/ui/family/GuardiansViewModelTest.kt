package monster.greyde.kachalochka.ui.family

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.fakes.FakeGym
import monster.greyde.kachalochka.ui.friends.IVAN_SESSION
import monster.greyde.kachalochka.ui.friends.signedInGym
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GuardiansViewModelTest {
    private val gym = signedInGym()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GuardiansViewModel(gym.family, gym.accounts)

    @Test
    fun the_list_shows_the_account_s_parents() {
        gym.family.link(IVAN_MEMBER, PAPA)

        assertEquals(listOf(PAPA), viewModel().state.value.guardians)
    }

    @Test
    fun a_code_is_typed_as_a_group_code_is() {
        val vm = viewModel()

        vm.type("papa 2345")
        assertEquals("PAPA2345", vm.state.value.code)
        assertTrue(vm.state.value.canAdd)

        vm.type("pap")
        assertFalse(vm.state.value.canAdd)
    }

    @Test
    fun a_parent_s_code_links_the_account_and_lists_the_parent() {
        gym.family.offered("PAPA2345", PAPA)
        val vm = viewModel()

        vm.type("PAPA2345")
        vm.add()

        assertEquals(listOf(PAPA), vm.state.value.guardians)
        assertEquals("", vm.state.value.code)
        assertEquals(listOf(IVAN_MEMBER.userId to PAPA.userId), gym.family.links)
    }

    @Test
    fun an_unknown_code_says_so() {
        val vm = viewModel()

        vm.type("ZZZZ2345")
        vm.add()

        assertEquals("Код не найден или устарел", vm.state.value.error)
    }

    @Test
    fun the_account_s_own_code_says_so() {
        gym.family.offered("PAPA2345", IVAN_MEMBER)
        val vm = viewModel()

        vm.type("PAPA2345")
        vm.add()

        assertEquals("Это ваш собственный код", vm.state.value.error)
        assertTrue(gym.family.links.isEmpty())
    }

    @Test
    fun offline_adding_changes_nothing_and_says_so() {
        gym.family.offered("PAPA2345", PAPA)
        val vm = viewModel()
        gym.family.offline = true

        vm.type("PAPA2345")
        vm.add()

        assertEquals("Нет связи с сервером", vm.state.value.error)
        assertTrue(gym.family.links.isEmpty())
    }

    @Test
    fun removing_a_parent_asks_first_then_ends_the_link() {
        gym.family.link(IVAN_MEMBER, PAPA)
        val vm = viewModel()

        vm.askToRemove(PAPA)
        assertEquals(1, gym.family.links.size)
        vm.confirmRemove()

        assertTrue(gym.family.links.isEmpty())
        assertEquals(emptyList(), vm.state.value.guardians)
        assertNull(vm.state.value.removing)
    }

    @Test
    fun cancelling_a_removal_closes_the_question_and_keeps_the_link() {
        gym.family.link(IVAN_MEMBER, PAPA)
        val vm = viewModel()

        vm.askToRemove(PAPA)
        vm.cancelRemove()

        assertNull(vm.state.value.removing)
        assertEquals(1, gym.family.links.size)
    }

    @Test
    fun switching_accounts_drops_the_shown_parents_and_typed_code() {
        val two = FakeGym().withAccounts(IVAN_SESSION, OLGA_SESSION, active = IVAN_SESSION)
        two.family.link(IVAN_MEMBER, PAPA)
        val vm = GuardiansViewModel(two.family, two.accounts).also { it.type("PAPA2345") }
        assertEquals(listOf(PAPA), vm.state.value.guardians)

        runBlocking { two.accounts.switchTo(OLGA_SESSION.account.userId) }

        assertEquals(emptyList(), vm.state.value.guardians)
        assertEquals("", vm.state.value.code)
    }
}
