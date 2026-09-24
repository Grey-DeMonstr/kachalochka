package monster.greyde.kachalochka.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncPass
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.TimeUnit

private const val SYNC_WORK_NAME = "sync"
private const val BACKOFF_SECONDS = 30L

class WorkManagerSyncTrigger(
    private val context: Context,
) : SyncTrigger {
    override fun request() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        // A pass waiting out its backoff must not delay the newest request; cancelling a running
        // one is safe, since its outbox entries stay and every push is an upsert.
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    override suspend fun doWork(): Result {
        if (!get<SupabaseCredentials>().isConfigured) return Result.success()
        val clean =
            try {
                get<SyncPass>().run(get<AccountStore>().accounts.value.map { it.userId })
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (failed: Exception) {
                false
            }
        return if (clean) Result.success() else Result.retry()
    }
}
