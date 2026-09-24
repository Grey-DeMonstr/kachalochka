package monster.greyde.kachalochka.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import monster.greyde.kachalochka.core.data.identity.AccountStore
import monster.greyde.kachalochka.core.data.supabase.SupabaseCredentials
import monster.greyde.kachalochka.core.data.sync.SyncPass
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private const val SYNC_WORK_NAME = "sync"

class WorkManagerSyncTrigger(
    private val context: Context,
) : SyncTrigger {
    override fun request() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    override suspend fun doWork(): Result {
        if (!get<SupabaseCredentials>().isConfigured) return Result.success()
        get<SyncPass>().run(get<AccountStore>().accounts.value.map { it.userId })
        return Result.success()
    }
}
