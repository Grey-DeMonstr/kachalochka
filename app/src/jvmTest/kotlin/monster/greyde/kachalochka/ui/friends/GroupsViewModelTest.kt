package monster.greyde.kachalochka.ui.friends

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.domain.friends.GroupId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelTest {
    private val gym = signedInGym()
    private val cache = GroupsCache(gym.friends, gym.accounts, TestScope())

    private fun viewModel() = GroupsViewModel(gym.friends, gym.accounts, cache)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_account_s_groups_are_listed_by_name_with_their_member_counts() {
        gym.friends.group("Зал на Лесной", owner = OLEG, ME)
        gym.friends.group("Бассейн", owner = ME)
        gym.friends.group("Чужая", owner = OLEG)

        val groups = viewModel().state.value.groups

        assertEquals(
            listOf("Бассейн" to "1 участник", "Зал на Лесной" to "2 участника"),
            groups?.map { it.name to it.members },
        )
    }

    @Test
    fun groups_read_ahead_show_while_the_fresh_read_is_in_flight() =
        runTest {
            gym.friends.group("Зал на Лесной", owner = ME)
            cache.refresh(ME.userId)
            gym.friends.group("Бассейн", owner = ME)
            val gate = CompletableDeferred<Unit>()
            gym.friends.gate = gate

            val vm = viewModel()
            assertEquals(
                listOf("Зал на Лесной"),
                vm.state.value.groups
                    ?.map { it.name },
            )

            gate.complete(Unit)
            assertEquals(
                listOf("Бассейн", "Зал на Лесной"),
                vm.state.value.groups
                    ?.map { it.name },
            )
            assertEquals(2, cache.cached(ME.userId)?.size)
        }

    @Test
    fun creating_a_group_opens_it() {
        val vm = viewModel()
        vm.openCreate()
        assertEquals(
            false,
            vm.state.value.dialog
                ?.canConfirm,
        )

        vm.type("  Зал на Лесной  ")
        var opened: GroupId? = null
        vm.confirm { opened = it }

        val created =
            gym.friends.groups.values
                .single()
        assertEquals("Зал на Лесной", created.name)
        assertEquals(created.id, opened)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun a_group_name_keeps_at_most_forty_characters() {
        val vm = viewModel().also { it.openCreate() }

        vm.type("а".repeat(45))

        assertEquals(
            40,
            vm.state.value.dialog
                ?.text
                ?.length,
        )
    }

    @Test
    fun a_typed_code_is_upper_cased_and_needs_eight_characters() {
        val vm = viewModel().also { it.openJoin() }

        vm.type("abcd234")
        assertEquals(
            "ABCD234" to false,
            vm.state.value.dialog
                ?.let { it.text to it.canConfirm },
        )
        vm.type("abcd2345")
        assertEquals(
            true,
            vm.state.value.dialog
                ?.canConfirm,
        )
    }

    @Test
    fun an_unknown_code_says_so_and_keeps_the_dialog() {
        val vm = viewModel().also { it.openJoin() }
        vm.type("ZZZZ2345")

        vm.confirm { fail("nothing to open") }

        assertEquals(
            "Приглашение не найдено",
            vm.state.value.dialog
                ?.error,
        )
    }

    @Test
    fun a_known_code_joins_and_opens_the_group() {
        val group = gym.friends.group("Зал на Лесной", owner = OLEG, code = "ABCD2345")
        val vm = viewModel().also { it.openJoin() }
        vm.type("abcd2345")
        var opened: GroupId? = null

        vm.confirm { opened = it }

        assertEquals(group.id, opened)
        assertTrue(ME in gym.friends.members.getValue(group.id))
    }

    @Test
    fun a_failed_create_says_so_in_the_dialog() {
        val vm = viewModel().also { it.openCreate() }
        vm.type("Зал")
        gym.friends.offline = true

        vm.confirm { fail("nothing to open") }

        assertEquals(
            "Нет связи с сервером",
            vm.state.value.dialog
                ?.error,
        )
    }

    @Test
    fun offline_the_list_says_so_and_loading_again_reads_it() {
        gym.friends.offline = true
        val vm = viewModel()
        assertTrue(vm.state.value.offline)

        gym.friends.offline = false
        vm.load()

        assertEquals(emptyList(), vm.state.value.groups)
        assertFalse(vm.state.value.offline)
    }
}
