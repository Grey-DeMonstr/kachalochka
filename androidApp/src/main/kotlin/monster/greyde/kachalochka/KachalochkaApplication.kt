package monster.greyde.kachalochka

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import monster.greyde.kachalochka.core.data.sync.SyncTrigger
import monster.greyde.kachalochka.core.di.corePlatformModule
import monster.greyde.kachalochka.core.di.followLiveSession
import monster.greyde.kachalochka.di.appModule
import monster.greyde.kachalochka.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KachalochkaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin =
            startKoin {
                androidContext(this@KachalochkaApplication)
                modules(appModule, corePlatformModule(), platformModule())
            }.koin
        koin.followLiveSession()
        val sync = koin.get<SyncTrigger>()
        // Fires at launch and again on every return to the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = sync.request()
            },
        )
    }
}
