package com.kyovo.cents.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kyovo.cents.domain.port.input.GenerateRecurringExpensesUseCase
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import com.kyovo.cents.domain.port.input.NotifyDueRecurringExpensesUseCase
import java.time.LocalDate
import java.time.YearMonth

/**
 * The daily check, kept as thin as possible (everything worth testing lives in the use cases): catch the
 * recurring expenses up, tell the user about the ones that fall today, then check the budgets, since the
 * transactions just generated are the only spending that arrives without the user being in the app — one
 * typed by hand is announced by the form itself. Built by [CentsWorkerFactory], the only way it can carry
 * its use cases: WorkManager's own default instantiation only knows the `(Context, WorkerParameters)`
 * constructor.
 */
class RecurringExpenseWorker(
    context: Context,
    params: WorkerParameters,
    private val generateRecurringExpenses: GenerateRecurringExpensesUseCase,
    private val notifyDueRecurringExpenses: NotifyDueRecurringExpensesUseCase,
    private val notifyBudgetAlerts: NotifyBudgetAlertUseCase,
) : CoroutineWorker(context, params)
{
    override suspend fun doWork(): Result
    {
        // Generation first: today's occurrence must be recorded before the user is told it is.
        generateRecurringExpenses.generate()
        notifyDueRecurringExpenses.notify(LocalDate.now())
        notifyBudgetAlerts.notify(YearMonth.now())
        return Result.success()
    }
}
