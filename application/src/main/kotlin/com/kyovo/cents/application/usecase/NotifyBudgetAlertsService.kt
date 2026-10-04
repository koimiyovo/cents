package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.port.input.CheckBudgetAlertsUseCase
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import com.kyovo.cents.domain.port.output.BudgetAlertNotifier
import java.time.YearMonth

class NotifyBudgetAlertsService(
    private val checkBudgetAlerts: CheckBudgetAlertsUseCase,
    private val notifier: BudgetAlertNotifier
) : NotifyBudgetAlertUseCase
{
    override suspend fun notify(month: YearMonth)
    {
        val alerts = checkBudgetAlerts.check(month)
        alerts.forEach { alert ->
            notifier.notify(alert)
        }
    }
}