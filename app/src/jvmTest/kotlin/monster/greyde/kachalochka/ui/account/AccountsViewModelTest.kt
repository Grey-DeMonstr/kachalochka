package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import monster.greyde.kachalochka.core.data.identity.Account
import monster.greyde.kachalochka.core.data.identity.AccountKind
import monster.greyde.kachalochka.core.data.identity.AccountSession
import monster.greyde.kachalochka.core.data.identity.Accounts
import monster.greyde.kachalochka.core.data.identity.GoogleSignIn
import monster.greyde.kachalochka.core.data.identity.InMemoryAccountStorage
import monster.greyde.kachalochka.core.data.identity.NoWatermarkReset
import monster.greyde.kachalochka.core.data.identity.OwnerlessRows
import monster.greyde.kachalochka.core.data.identity.PersistedAccountStore
import monster.greyde.kachalochka.core.data.identity.SessionActivation
import monster.greyde.kachalochka.core.data.identity.SignInCancelledException
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.domain.gym.PhotoId
import monster.greyde.kachalochka.core.domain.identity.Avatar
import monster.greyde.kachalochka.core.domain.identity.UserId
import monster.greyde.kachalochka.core.domain.profile.Profile
import monster.greyde.kachalochka.core.domain.profile.ProfileRepository
import monster.greyde.kachalochka.fakes.InMemoryProfileRepository
import monster.greyde.kachalochka.fakes.RecordingSyncTrigger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FailingSignIn(
    private val error: Throwable,
) : GoogleSignIn {
    override suspend fun signIn(): AccountSession = throw error
}

private class SucceedingSignIn(
    private val session: AccountSession,
) : GoogleSignIn {
    override suspend fun signIn(): AccountSession = session
}

private class NoOpSessionActivation : SessionActivation {
    override suspend fun activate(session: AccountSession) = Unit

    override suspend fun clear() = Unit
}

private class NoOpOwnerlessRows : OwnerlessRows {
    override suspend fun claim(owner: UserId) = Unit
}

private class RefusingProfiles(
    private val inner: InMemoryProfileRepository,
) : ProfileRepository by inner {
    var refusing = false

    override suspend fun forOwner(owner: UserId?): Profile? {
        check(!refusing) { "offline" }
        return inner.forOwner(owner)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val profiles = InMemoryProfileRepository()
    private val reads = RefusingProfiles(profiles)

    private fun viewModel(
        signIn: GoogleSignIn,
        sync: SyncTrigger = RecordingSyncTrigger(),
    ): AccountsViewModel {
        val accounts =
            Accounts(
                PersistedAccountStore(InMemoryAccountStorage()),
                signIn,
                NoOpSessionActivation(),
                NoOpOwnerlessRows(),
                NoWatermarkReset,
            )
        return AccountsViewModel(accounts, sync, AccountAvatars(accounts, reads), HeldWrites())
    }

    @Test
    fun an_account_shows_the_photo_it_chose_else_its_google_picture() =
        runTest {
            val id = UserId("11111111-1111-4111-8111-111111111111")
            val picture = "https://example.test/sam.png"
            val session =
                AccountSession(
                    Account(id, "sam@example.test", "Sam", picture),
                    "access",
                    "refresh",
                    Instant.fromEpochSeconds(0),
                )
            val sync = RecordingSyncTrigger()
            val viewModel = viewModel(SucceedingSignIn(session), sync)

            viewModel.addAccount()

            assertEquals(
                Avatar(picture = picture),
                viewModel.state.value.accounts
                    .single()
                    .avatar,
            )

            val photo = PhotoId.random()
            profiles.upsert(Profile.new(id, Instant.fromEpochSeconds(0)).copy(avatarPhoto = photo))
            sync.completePass()

            assertEquals(
                Avatar(photo, picture),
                viewModel.state.value.accounts
                    .single()
                    .avatar,
            )
        }

    @Test
    fun an_account_shows_its_profile_s_nickname_instead_of_its_google_name() =
        runTest {
            val id = UserId("11111111-1111-4111-8111-111111111111")
            val session =
                AccountSession(
                    Account(id, "sam@example.test", "Sam Smith"),
                    "access",
                    "refresh",
                    Instant.fromEpochSeconds(0),
                )
            val sync = RecordingSyncTrigger()
            val viewModel = viewModel(SucceedingSignIn(session), sync)
            viewModel.addAccount()

            profiles.upsert(Profile.new(id, Instant.fromEpochSeconds(0)).copy(displayName = "Сэм"))
            sync.completePass()

            val shown =
                viewModel.state.value.accounts
                    .single()
            assertEquals("Сэм", shown.displayName)
            assertEquals("С", shown.monogram)
        }

    @Test
    fun a_profile_that_cannot_be_read_or_is_not_there_keeps_the_stored_nickname() =
        runTest {
            val id = UserId("11111111-1111-4111-8111-111111111111")
            val session =
                AccountSession(
                    Account(id, "sam@example.test", "Sam Smith"),
                    "access",
                    "refresh",
                    Instant.fromEpochSeconds(0),
                )
            val sync = RecordingSyncTrigger()
            val viewModel = viewModel(SucceedingSignIn(session), sync)
            viewModel.addAccount()
            profiles.upsert(Profile.new(id, Instant.fromEpochSeconds(0)).copy(displayName = "Сэм"))
            sync.completePass()

            reads.refusing = true
            sync.completePass()
            assertEquals(
                "Сэм",
                viewModel.state.value.accounts
                    .single()
                    .displayName,
            )

            reads.refusing = false
            profiles.rows.clear()
            sync.completePass()
            assertEquals(
                "Сэм",
                viewModel.state.value.accounts
                    .single()
                    .displayName,
            )
        }

    /**
     * `addAccount()` fires and forgets on `viewModelScope`, so a failure escapes as an uncaught
     * exception on `Dispatchers.Main` rather than a thrown one; `runTest` is what surfaces that
     * as this test's own failure instead of letting it pass quietly.
     */
    @Test
    fun a_failed_sign_in_says_so_instead_of_crashing_the_app() =
        runTest {
            val viewModel =
                viewModel(FailingSignIn(IllegalStateException("Sign-in needs a visible screen")))

            viewModel.addAccount()

            assertNotNull(viewModel.state.value.failure)
        }

    @Test
    fun backing_out_of_the_picker_leaves_no_trace_of_an_error() =
        runTest {
            val viewModel = viewModel(FailingSignIn(SignInCancelledException()))

            viewModel.addAccount()

            assertNull(viewModel.state.value.failure)
        }

    @Test
    fun adding_an_account_asks_for_a_sync_pass() =
        runTest {
            val session =
                AccountSession(
                    Account(
                        UserId("11111111-1111-4111-8111-111111111111"),
                        "sam@example.test",
                        "Sam",
                    ),
                    "access",
                    "refresh",
                    Instant.fromEpochSeconds(0),
                )
            val sync = RecordingSyncTrigger()
            val viewModel = viewModel(SucceedingSignIn(session), sync)

            viewModel.addAccount()

            assertEquals(1, sync.requests)
        }

    @Test
    fun a_managed_child_is_marked_and_shows_the_photo_it_chose() {
        val photo = PhotoId.random()
        val child =
            Account(
                UserId("66666666-6666-4666-8666-666666666666"),
                "",
                "Саша",
                "https://example.test/s.png",
                AccountKind.Managed(UserId("11111111-1111-4111-8111-111111111111"), photo),
            )

        val shown = accountsUi(listOf(child), child.userId).single()

        assertTrue(shown.managed)
        assertEquals(Avatar(photo, "https://example.test/s.png"), shown.avatar)
        assertTrue(AccountsUi(listOf(shown), child.userId).managedActive)
    }
}
