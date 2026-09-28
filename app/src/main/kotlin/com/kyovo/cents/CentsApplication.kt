package com.kyovo.cents

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kyovo.cents.infrastructure.persistence.room.RoomPersistence
import com.kyovo.cents.work.BudgetAlertWorker
import com.kyovo.cents.work.CentsWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Owns the [AppContainer] for the whole process. An Activity is destroyed and recreated on every
 * rotation or light/dark switch, so a container held by the Activity would be rebuilt each time. The
 * Application object lives as long as the process does.
 *
 * The container is built at the first use, not in the constructor: it needs the application context
 * for the database file, which does not exist yet while the Application object is being constructed.
 * The database is created (with the common subcategories in it) the first time it is opened.
 *
 * [Configuration.Provider] is how WorkManager gets [CentsWorkerFactory] (so a worker can be built with
 * [AppContainer]'s real dependencies instead of a bare no-arg constructor) — on-demand initialization,
 * which also needs `androidx.work.WorkManagerInitializer` disabled in the manifest, or WorkManager
 * would already have initialized itself with the default configuration by the time this is read.
 */
class CentsApplication : Application(), Configuration.Provider {
    val appContainer: AppContainer by lazy { AppContainer(this, RoomPersistence.open(this)) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(CentsWorkerFactory(appContainer))
            .build()

    override fun onCreate() {
        super.onCreate()

        // Catches every recurring expense up as soon as the app opens, off the main thread: safe to run
        // every time, since an occurrence already generated is never redone.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            appContainer.generateRecurringExpenses.generate()
        }

        // KEEP, not REPLACE: re-enqueuing the same periodic work on every app launch must not push the
        // next run back out — only the very first launch ever actually schedules it. The 6-hour period
        // is a starting guess (budget alerts don't need to be minutes-fresh); it's this one constant to
        // change if that turns out too often or too rare. WorkManager.getInstance(Context), not the
        // no-arg overload, since initialization is on-demand (Configuration.Provider above).
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BUDGET_ALERT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BudgetAlertWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    private companion object {
        const val BUDGET_ALERT_WORK_NAME = "budget-alert-check"
    }
}
