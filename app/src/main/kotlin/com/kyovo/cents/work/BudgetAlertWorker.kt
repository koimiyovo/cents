package com.kyovo.cents.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import java.time.YearMonth

/**
 * The periodic check itself, kept as thin as possible: everything worth testing — which alerts are new
 * this month, [com.kyovo.cents.application.usecase.NotifyBudgetAlertsService] — already is, without any
 * of this. Built by [CentsWorkerFactory], the only way it can carry [notifyBudgetAlerts]: WorkManager's
 * own default instantiation only knows the `(Context, WorkerParameters)` constructor.
 */
class BudgetAlertWorker(
    context: Context,
    params: WorkerParameters,
    private val notifyBudgetAlerts: NotifyBudgetAlertUseCase,
) : CoroutineWorker(context, params)
{
    override suspend fun doWork(): Result
    {
        notifyBudgetAlerts.notify(YearMonth.now())
        return Result.success()
    }
}
