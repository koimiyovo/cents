package com.kyovo.cents.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kyovo.cents.AppContainer

/**
 * Builds [BudgetAlertWorker] with its real dependency from [appContainer] — WorkManager's default
 * instantiation only knows a `(Context, WorkerParameters)` constructor, so a custom factory is the only
 * way a worker gets anything beyond that. Returning `null` for any other class name lets WorkManager
 * fall back to its own default, reflection-based instantiation (irrelevant here: this is the only worker).
 */
class CentsWorkerFactory(private val appContainer: AppContainer) : WorkerFactory()
{
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker?
    {
        return when (workerClassName)
        {
            BudgetAlertWorker::class.java.name ->
                BudgetAlertWorker(appContext, workerParameters, appContainer.notifyBudgetAlerts)
            else -> null
        }
    }
}
