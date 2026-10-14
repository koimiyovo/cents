package com.kyovo.cents

import java.io.IOException
import com.kyovo.cents.work.ExchangeRatesWorker
import com.kyovo.cents.infrastructure.exchange.MalformedExchangeRatesException
import androidx.work.NetworkType
import android.app.Application
import android.os.Build
import com.kyovo.cents.crash.CrashHandler
import com.kyovo.cents.crash.CrashReportStore
import com.kyovo.cents.crash.DeviceInfo
import com.kyovo.cents.crash.crashReport
import java.io.File
import java.time.Instant
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kyovo.cents.infrastructure.persistence.room.RoomPersistence
import com.kyovo.cents.work.AutomaticBackupWorker
import com.kyovo.cents.work.CentsWorkerFactory
import com.kyovo.cents.work.RecurringTransactionWorker
import com.kyovo.cents.work.delayUntilNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
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

    /** The report of the last crash, kept in the private storage until the user has been asked about it. */
    val crashReportStore: CrashReportStore by lazy { CrashReportStore(File(filesDir, "last-crash.txt")) }

    override fun onCreate() {
        super.onCreate()

        // First of all, so that a crash while starting is reported too. The previous handler (the system's) still
        // gets every crash, so Android vitals keeps working.
        val device = DeviceInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            androidSdk = Build.VERSION.SDK_INT,
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(
                store = crashReportStore,
                report = { crashReport(it, Instant.now(), device) },
                next = Thread.getDefaultUncaughtExceptionHandler(),
            )
        )

        // Catches every recurring transaction up as soon as the app opens, off the main thread: safe to run
        // every time, since an occurrence already generated is never redone.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            appContainer.generateRecurringTransactions.generate()
        }

        // Fetches the exchange rates as soon as the app opens, so that the totals are converted from the first
        // screen: off the main thread, and a failure (no network, a server down) is no news, the rates kept
        // on the device stay. The daily work below keeps them fresh when the app is not opened.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try
            {
                appContainer.refreshExchangeRates.refresh()
            } catch (_: IOException)
            {
            } catch (_: MalformedExchangeRatesException)
            {
            }
        }

        val workManager = WorkManager.getInstance(this)

        // The 6-hourly budget check this replaced may still be scheduled on a phone that ran an older
        // version: cancelling it is harmless when it is not, and without it WorkManager would keep trying
        // to build a worker class that no longer exists.
        workManager.cancelUniqueWork(OLD_BUDGET_ALERT_WORK_NAME)

        // Once a day, in the morning (the first run waits for the next 8:00, the following ones follow
        // 24 hours apart): a recurring transaction falls on a calendar day, and it is the day itself the user is
        // told about. KEEP, not REPLACE: re-enqueuing the same periodic work on every app launch must not push
        // the next run back out — only the very first launch ever actually schedules it.
        // WorkManager.getInstance(Context), not the no-arg overload, since initialization is on-demand
        // (Configuration.Provider above).
        workManager.enqueueUniquePeriodicWork(
            RECURRING_TRANSACTIONS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNext(hour = 8, now = ZonedDateTime.now()))
                .build(),
        )

        // Once a day, when there is a connection (WorkManager waits for one instead of failing): the exchange
        // rates are published once a working day. KEEP, like the others.
        workManager.enqueueUniquePeriodicWork(
            EXCHANGE_RATES_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExchangeRatesWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )

        // Once a week, whenever the phone is not low on battery (the exact day does not matter, unlike the
        // morning check above). Scheduled even when no folder is chosen: the worker then does nothing, and
        // choosing one later needs no scheduling. KEEP for the same reason as above.
        workManager.enqueueUniquePeriodicWork(
            AUTOMATIC_BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutomaticBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    private companion object {
        const val AUTOMATIC_BACKUP_WORK_NAME = "automatic-backup-weekly"
        const val EXCHANGE_RATES_WORK_NAME = "exchange-rates-daily"

        // The name of the work as it was first scheduled, kept as is: renaming it would enqueue a second
        // periodic work next to the one already scheduled on the phone.
        const val RECURRING_TRANSACTIONS_WORK_NAME = "recurring-expenses-daily"

        // The name the replaced 6-hourly budget check was scheduled under.
        const val OLD_BUDGET_ALERT_WORK_NAME = "budget-alert-check"
    }
}
