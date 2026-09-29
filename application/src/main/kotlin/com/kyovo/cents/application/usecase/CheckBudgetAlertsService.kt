package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.port.input.CheckBudgetAlertsUseCase
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import com.kyovo.cents.domain.port.output.BudgetAlertRepository
import kotlinx.coroutines.flow.first
import java.time.YearMonth

class CheckBudgetAlertsService(
    private val getBudgetProgressUseCase: GetBudgetProgressUseCase,
    private val budgetAlertRepository: BudgetAlertRepository
) : CheckBudgetAlertsUseCase
{
    // A one-shot read of a Flow-based port: nothing here needs to stay subscribed, the worker that
    // calls this just wants this month's answer, once.
    override suspend fun check(month: YearMonth): List<BudgetAlert>
    {
        val progress = getBudgetProgressUseCase.observeAll(month).first()
        val recorded = budgetAlertRepository.findByMonth(month)

        // An alert is only worth remembering while the budget still holds that level: once the expense
        // behind it is deleted or corrected, the budget is back below it, and reaching it again later is
        // news again — not silence because "it was reported once". A lower level stays remembered while
        // a higher one is reached (close stays while over).
        val (stale, alreadyNotified) = recorded.partition { alert ->
            val current = progress[alert.subcategoryId]?.alertLevel()
            current == null || current < alert.level
        }
        stale.forEach { budgetAlertRepository.delete(it) }

        val newAlerts = progress.mapNotNull { (subcategoryId, budgetProgress) ->
            val level = budgetProgress.alertLevel() ?: return@mapNotNull null
            val alert = BudgetAlert(subcategoryId, month, level)
            if (alert in alreadyNotified) null else alert
        }

        newAlerts.forEach { budgetAlertRepository.record(it) }
        return newAlerts
    }
}