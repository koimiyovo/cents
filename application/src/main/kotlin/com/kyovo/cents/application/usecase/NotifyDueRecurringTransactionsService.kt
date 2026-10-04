package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.port.input.NotifyDueRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.RecurringTransactionNotifier
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import java.time.LocalDate

class NotifyDueRecurringTransactionsService(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val notifier: RecurringTransactionNotifier
) : NotifyDueRecurringTransactionsUseCase
{
    override suspend fun notify(today: LocalDate)
    {
        for (rule in recurringTransactionRepository.findAll())
        {
            if (!rule.occursOn(today)) continue

            // Same rule as GenerateRecurringTransactionsService: nothing is recorded for an unknown or archived
            // account, so nothing is announced either.
            val account = accountRepository.findById(rule.accountId) ?: continue
            if (account.archivedAt != null) continue

            notifier.notify(rule)
        }
    }
}
