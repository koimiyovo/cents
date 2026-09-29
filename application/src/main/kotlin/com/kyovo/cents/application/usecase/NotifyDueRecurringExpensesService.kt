package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.port.input.NotifyDueRecurringExpensesUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.RecurringExpenseNotifier
import com.kyovo.cents.domain.port.output.RecurringExpenseRepository
import java.time.LocalDate

class NotifyDueRecurringExpensesService(
    private val recurringExpenseRepository: RecurringExpenseRepository,
    private val accountRepository: AccountRepository,
    private val notifier: RecurringExpenseNotifier
) : NotifyDueRecurringExpensesUseCase
{
    override suspend fun notify(today: LocalDate)
    {
        for (rule in recurringExpenseRepository.findAll())
        {
            if (!rule.occursOn(today)) continue

            // Same rule as GenerateRecurringExpensesService: nothing is recorded for an unknown or archived
            // account, so nothing is announced either.
            val account = accountRepository.findById(rule.accountId) ?: continue
            if (account.archivedAt != null) continue

            notifier.notify(rule)
        }
    }
}
