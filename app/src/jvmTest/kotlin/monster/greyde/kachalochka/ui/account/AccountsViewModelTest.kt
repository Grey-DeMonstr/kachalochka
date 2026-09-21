package monster.greyde.kachalochka.ui.account

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class GetCredentialCancellationException : Exception("the user backed out")

class AccountsViewModelTest {
    @Test
    fun backing_out_of_the_google_picker_reads_as_a_change_of_mind() {
        assertTrue(isUserCancellation(GetCredentialCancellationException()))
    }

    @Test
    fun a_missing_client_id_is_not_mistaken_for_a_change_of_mind() {
        assertFalse(isUserCancellation(IllegalStateException("Sign-in needs a visible screen")))
    }

    @Test
    fun kotlin_s_own_cancellation_is_not_mistaken_for_a_change_of_mind() {
        assertFalse(isUserCancellation(CancellationException("stopped")))
    }
}
