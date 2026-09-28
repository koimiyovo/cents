package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.BudgetAlert

interface BudgetAlertNotifier
{
    suspend fun notify(alert: BudgetAlert)
}