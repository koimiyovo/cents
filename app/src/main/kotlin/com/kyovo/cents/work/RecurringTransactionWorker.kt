package com.kyovo.cents.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kyovo.cents.domain.port.input.GenerateRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.GetBudgetCalendarUseCase
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import com.kyovo.cents.domain.port.input.NotifyDueRecurringTransactionsUseCase
import java.time.LocalDate

/**
 * The daily check, kept as thin as possible (everything worth testing lives in the use cases): catch the
 * recurring transactions up, tell the user about the ones that fall today, then check the budgets, since the
 * transactions just generated are the only spending that arrives without the user being in the app — one
 * typed by hand is announced by the form itself. Built by [CentsWorkerFactory], the only way it can carry
 * its use cases: WorkManager's own default instantiation only knows the `(Context, WorkerParameters)`
 * constructor.
 */
class RecurringTransactionWorker(
    context: Context,
    params: WorkerParameters,
    private val generateRecurringTransactions: GenerateRecurringTransactionsUseCase,
    private val notifyDueRecurringTransactions: NotifyDueRecurringTransactionsUseCase,
    private val notifyBudgetAlerts: NotifyBudgetAlertUseCase,
    private val getBudgetCalendar: GetBudgetCalendarUseCase,
) : CoroutineWorker(context, params)
{
    override suspend fun doWork(): Result
    {
        // Generation first: today's occurrence must be recorded before the user is told it is.
        generateRecurringTransactions.generate()
        notifyDueRecurringTransactions.notify(LocalDate.now())
        notifyBudgetAlerts.notify(getBudgetCalendar.cycleOf(LocalDate.now()))
        return Result.success()
    }
}
