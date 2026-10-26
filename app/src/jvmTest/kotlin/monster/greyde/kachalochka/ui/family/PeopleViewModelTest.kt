package monster.greyde.kachalochka.ui.family

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {
    private val gym = signedInGym()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(of: FakeGym = gym) =
        PeopleViewModel(of.family, of.follower, of.invites, of.accounts, of.sync)

    @Test
    fun the_screen_lists_the_account_s_children_and_parents() {
        gym.family.link(SASHA, IVAN_MEMBER)
        gym.family.link(IVAN_MEMBER, PAPA)

        val state = viewModel().state.value

        assertEquals(listOf(SASHA), state.children)
        assertEquals(listOf(PAPA), state.guardians)
    }

    @Test
    fun offline_the_screen_says_so() {
        gym.family.offline = true

        assertTrue(viewModel().state.value.offline)
    }

    @Test
    fun adding_a_child_shows_a_new_code_of_the_account_with_its_link() {
        val vm = viewModel()

        vm.addChild()

        val invite = assertNotNull(vm.state.value.offer)
        assertEquals("PAPA2345", invite.code)
        assertEquals("https://example.test/kachalochka/?parent=PAPA2345", invite.link)
        assertEquals(IVAN_MEMBER.userId, gym.family.codes["PAPA2345"])
    }

    @Test
    fun without_a_page_address_only_the_code_is_shown() {
        gym.invites.pageAddress = null
        val vm = viewModel()

        vm.addChild()

        assertEquals(
            "PAPA2345",
            vm.state.value.offer
                ?.code,
        )
        assertNull(
            vm.state.value.offer
                ?.link,
        )
    }

    @Test
    fun offline_no_code_is_shown_and_the_screen_says_so() {
        val vm = viewModel()
        gym.family.offline = true

        vm.addChild()

        assertNull(vm.state.value.offer)
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
        assertEquals(
            "Ссылка скопирована",
            vm.state.value.offer
                ?.notice,
        )
    }

    @Test
    fun closing_the_code_keeps_the_list() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel().also { it.addChild() }

        vm.closeOffer()

        assertNull(vm.state.value.offer)
        assertEquals(listOf(SASHA), vm.state.value.children)
    }

    @Test
    fun a_parent_s_code_is_typed_as_a_group_code_is() {
        val vm = viewModel()

        vm.addGuardian()
        vm.type("papa 2345")
        assertEquals(
            "PAPA2345",
            vm.state.value.entry
                ?.code,
        )
        assertTrue(
            vm.state.value.entry
                ?.canAdd == true,
        )

        vm.type("pap")
        assertFalse(
            vm.state.value.entry
                ?.canAdd == true,
        )
    }

    @Test
    fun a_parent_s_code_links_the_account_and_lists_the_parent() {
        gym.family.offered("PAPA2345", PAPA)
        val vm = viewModel()

        vm.addGuardian()
        vm.type("PAPA2345")
        vm.link()

        assertEquals(listOf(PAPA), vm.state.value.guardians)
        assertNull(vm.state.value.entry)
        assertEquals(listOf(IVAN_MEMBER.userId to PAPA.userId), gym.family.links)
    }

    @Test
    fun an_unknown_code_says_so() {
        val vm = viewModel()

        vm.addGuardian()
        vm.type("ZZZZ2345")
        vm.link()

        assertEquals(
            "Код не найден или устарел",
            vm.state.value.entry
                ?.error,
        )
    }

    @Test
    fun the_account_s_own_code_says_so() {
        gym.family.offered("PAPA2345", IVAN_MEMBER)
        val vm = viewModel()

        vm.addGuardian()
        vm.type("PAPA2345")
        vm.link()

        assertEquals(
            "Это ваш собственный код",
            vm.state.value.entry
                ?.error,
        )
        assertTrue(gym.family.links.isEmpty())
    }

    @Test
    fun offline_linking_a_parent_changes_nothing_and_says_so() {
        gym.family.offered("PAPA2345", PAPA)
        val vm = viewModel()
        gym.family.offline = true

        vm.addGuardian()
        vm.type("PAPA2345")
        vm.link()

        assertEquals(
            "Нет связи с сервером",
            vm.state.value.entry
                ?.error,
        )
        assertTrue(gym.family.links.isEmpty())
    }

    @Test
    fun cancelling_the_code_entry_links_nobody() {
        val vm = viewModel()

        vm.addGuardian()
        vm.type("PAPA2345")
        vm.closeEntry()

        assertNull(vm.state.value.entry)
        assertTrue(gym.family.links.isEmpty())
    }

    @Test
    fun removing_a_child_asks_first_then_ends_the_link() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()

        vm.askToRemove(SASHA, Relation.Child)
        assertEquals(Removal(SASHA, Relation.Child), vm.state.value.removing)
        assertEquals(1, gym.family.links.size)

        vm.confirmRemove()

        assertTrue(gym.family.links.isEmpty())
        assertEquals(emptyList(), vm.state.value.children)
        assertNull(vm.state.value.removing)
    }

    @Test
    fun removing_a_parent_asks_first_then_ends_the_link() {
        gym.family.link(IVAN_MEMBER, PAPA)
        val vm = viewModel()

        vm.askToRemove(PAPA, Relation.Guardian)
        vm.confirmRemove()

        assertTrue(gym.family.links.isEmpty())
        assertEquals(emptyList(), vm.state.value.guardians)
    }

    @Test
    fun a_removal_offline_changes_nothing_and_says_so() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()
        gym.family.offline = true

        vm.askToRemove(SASHA, Relation.Child)
        vm.confirmRemove()

        assertEquals(1, gym.family.links.size)
        assertEquals("Нет связи с сервером", vm.state.value.notice)
    }

    @Test
    fun cancelling_a_removal_closes_the_question_and_keeps_the_link() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()

        vm.askToRemove(SASHA, Relation.Child)
        vm.cancelRemove()

        assertNull(vm.state.value.removing)
        assertEquals(1, gym.family.links.size)
    }

    @Test
    fun switching_accounts_drops_the_shown_people_code_and_typed_code() {
        val two = FakeGym().withAccounts(IVAN_SESSION, OLGA_SESSION, active = IVAN_SESSION)
        two.family.link(SASHA, IVAN_MEMBER)
        two.family.link(IVAN_MEMBER, PAPA)
        val vm = viewModel(two).also { it.addChild() }
        vm.addGuardian()
        vm.type("PAPA2345")
        assertEquals(listOf(SASHA), vm.state.value.children)

        runBlocking { two.accounts.switchTo(OLGA_SESSION.account.userId) }

        assertEquals(emptyList(), vm.state.value.children)
        assertEquals(emptyList(), vm.state.value.guardians)
        assertNull(vm.state.value.offer)
        assertNull(vm.state.value.entry)
    }

    @Test
    fun the_screen_lists_the_account_s_trainers_and_trainees() {
        gym.family.coach(SASHA, IVAN_MEMBER)
        gym.family.coach(IVAN_MEMBER, PAPA)

        val state = viewModel().state.value

        assertEquals(listOf(SASHA), state.trainees)
        assertEquals(listOf(PAPA), state.trainers)
        assertEquals(emptyList(), state.children)
    }

    @Test
    fun adding_a_trainee_shows_a_trainer_code_with_its_link() {
        val vm = viewModel()

        vm.addTrainee()

        val offer = assertNotNull(vm.state.value.offer)
        assertEquals(
            CodeOffer(
                LinkKind.Coaching,
                "TREN2345",
                "https://example.test/kachalochka/?trainer=TREN2345",
            ),
            offer,
        )
        assertEquals(IVAN_MEMBER.userId, gym.family.trainerCodes["TREN2345"])
    }

    @Test
    fun sharing_a_trainer_code_asks_to_be_added_as_trainer() {
        val vm = viewModel().also { it.addTrainee() }

        vm.share()

        assertEquals(
            "Добавь меня тренером в Качалочке",
            gym.invites.shared
                .single()
                .call,
        )
    }

    @Test
    fun a_trainer_s_code_links_the_account_as_a_trainee() {
        gym.family.offeredCoaching("TREN2345", PAPA)
        val vm = viewModel()

        vm.addTrainer()
        vm.type("tren 2345")
        vm.link()

        assertEquals(listOf(PAPA), vm.state.value.trainers)
        assertEquals(listOf(IVAN_MEMBER.userId to PAPA.userId), gym.family.coaching)
        assertTrue(gym.family.links.isEmpty())
        assertNull(vm.state.value.entry)
    }

    @Test
    fun a_parent_s_code_is_no_trainer_s_code() {
        gym.family.offered("PAPA2345", PAPA)
        val vm = viewModel()

        vm.addTrainer()
        vm.type("PAPA2345")
        vm.link()

        assertEquals(
            "Код не найден или устарел",
            vm.state.value.entry
                ?.error,
        )
    }

    @Test
    fun removing_a_trainee_ends_only_the_coaching() {
        gym.family.coach(SASHA, IVAN_MEMBER)
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()

        vm.askToRemove(SASHA, Relation.Trainee)
        vm.confirmRemove()

        assertTrue(gym.family.coaching.isEmpty())
        assertEquals(1, gym.family.links.size)
    }

    @Test
    fun removing_a_trainer_ends_the_coaching() {
        gym.family.coach(IVAN_MEMBER, PAPA)
        val vm = viewModel()

        vm.askToRemove(PAPA, Relation.Trainer)
        vm.confirmRemove()

        assertTrue(gym.family.coaching.isEmpty())
        assertEquals(emptyList(), vm.state.value.trainers)
    }

    @Test
    fun entering_the_screen_brings_a_linked_child_onto_the_device_and_syncs_it() {
        gym.family.link(SASHA, IVAN_MEMBER)

        viewModel()

        assertEquals(
            listOf(SASHA.userId),
            gym.accounts.accounts.value
                .filter { it.isManaged }
                .map { it.userId },
        )
        assertEquals(1, gym.sync.requests)
    }

    @Test
    fun removing_a_child_takes_it_and_its_rows_off_the_device() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()

        vm.askToRemove(SASHA, Relation.Child)
        vm.confirmRemove()

        assertTrue(
            gym.accounts.accounts.value
                .none { it.isManaged },
        )
        assertEquals(listOf(SASHA.userId), gym.purged)
    }

    @Test
    fun a_tap_is_not_dropped_while_the_follow_after_a_removal_waits_for_a_sync_pass() {
        gym.family.link(SASHA, IVAN_MEMBER)
        val vm = viewModel()
        val passing = CompletableDeferred<Unit>()
        CoroutineScope(Dispatchers.Unconfined).launch {
            gym.follower.followThen { passing.await() }
        }
        vm.askToRemove(SASHA, Relation.Child)
        vm.confirmRemove()

        vm.addChild()

        assertEquals(
            "PAPA2345",
            vm.state.value.offer
                ?.code,
        )
        passing.complete(Unit)
    }
}
