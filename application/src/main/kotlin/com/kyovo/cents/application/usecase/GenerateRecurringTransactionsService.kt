package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.GenerateRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * How far past today a recurring transaction's occurrences are generated: far enough that browsing a couple
 * of months ahead already shows what is coming, bounded so a never-ending rule cannot generate forever.
 */
private const val LOOKAHEAD_MONTHS = 3L

class GenerateRecurringTransactionsService(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val unitOfWork: UnitOfWork,
    private val clock: Clock,
    private val notifyBudgetAlerts: NotifyBudgetAlertUseCase
) : GenerateRecurringTransactionsUseCase
{
    override suspend fun generate()
    {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val horizon = today.plusMonths(LOOKAHEAD_MONTHS)
        val currentMonth = YearMonth.from(today)
        var recordedInCurrentMonth = false

        for (rule in recurringTransactionRepository.findAll())
        {
            val pending = rule.pendingOccurrences(horizon)
            if (pending.isEmpty()) continue

            // An archived account refuses new transactions: skip the rule entirely rather than generate
            // some of its due months and not others. lastGeneratedDate is left untouched, so every
            // occurrence stays pending and is retried the next time this runs.
            val account = accountRepository.findById(rule.accountId) ?: continue
            if (account.archivedAt != null) continue

            // A subcategory the rule pointed to may have been deleted since: generate uncategorised
            // rather than fail the whole rule, the same way a deleted subcategory leaves past
            // transactions uncategorised instead of erasing them.
            val subcategory = rule.subcategoryId?.let { subcategoryRepository.findById(it) }

            unitOfWork.execute {
                pending.forEach { occurrence ->
                    val date = occurrence.atTime(LocalTime.NOON).atZone(zone).toInstant()
                    val transaction = Transaction.recorded(
                        transactionIdGenerator.generate(),
                        rule.accountId,
                        rule.amount,
                        rule.title,
                        rule.category,
                        subcategory,
                        rule.description,
                        date,
                    )
                    transactionRepository.save(transaction)
                }
                recurringTransactionRepository.save(rule.copy(lastGeneratedDate = pending.last()))
            }

            // Only spending can cross a budget: an income generated this month changes none.
            if (rule.category == RecordableTransactionCategory.EXPENSE && pending.any { YearMonth.from(it) == currentMonth })
            {
                recordedInCurrentMonth = true
            }
        }

        // Nobody is typing these in, so nobody sees a budget move: the alerts are checked right now, once
        // everything is recorded, instead of waiting for the daily check. Only the current month: a month
        // ahead is checked when it becomes the current one (a notification saying "over its monthly limit"
        // says nothing of which month), and a month long past, reached by a catch-up, is not news any more.
        if (recordedInCurrentMonth) notifyBudgetAlerts.notify(currentMonth)
    }
}
