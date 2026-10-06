package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.GenerateRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Generates the transactions of the recurring rules: every occurrence that has already come, and at most one
 * more, the next one, when it falls later in the current budget month (cycle). Never further ahead: generating months in
 * advance filled the transactions list with what was not due yet.
 */
class GenerateRecurringTransactionsService(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val unitOfWork: UnitOfWork,
    private val calendarRepository: BudgetCalendarRepository,
    private val clock: Clock,
    private val notifyBudgetAlerts: NotifyBudgetAlertUseCase
) : GenerateRecurringTransactionsUseCase
{
    override suspend fun generate()
    {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        // "This month" is the budget cycle open today, which may run from the 25th to the 24th.
        val calendar = calendarRepository.observe().first()
        val currentMonth = calendar.cycleOf(today)
        val endOfCurrentMonth = calendar.endOf(currentMonth).minusDays(1)
        var recordedInCurrentMonth = false

        for (rule in recurringTransactionRepository.findAll())
        {
            val toGenerate = occurrencesToGenerate(rule, today, endOfCurrentMonth)
            if (toGenerate.isEmpty()) continue

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
                toGenerate.forEach { occurrence ->
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
                recurringTransactionRepository.save(rule.copy(lastGeneratedDate = toGenerate.last()))
            }

            // Only spending can cross a budget: an income generated this month changes none.
            if (rule.category == RecordableTransactionCategory.EXPENSE && toGenerate.any { calendar.cycleOf(it) == currentMonth })
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

    /**
     * What is still to generate for [rule]: the occurrences that have already come ([today] included), even
     * from past months (a rule left alone for a while catches up, and is never stuck on an old occurrence),
     * then the next one, if it falls before the end of the month — unless one ahead of today was already
     * generated by an earlier run, so running again the same day does not pull another one in. It is always a
     * prefix of the rule's pending occurrences, which is what `lastGeneratedDate` can express.
     */
    private fun occurrencesToGenerate(rule: RecurringTransaction, today: LocalDate, endOfMonth: LocalDate): List<LocalDate>
    {
        val (due, ahead) = rule.pendingOccurrences(endOfMonth).partition { !it.isAfter(today) }
        val oneIsAlreadyAhead = rule.lastGeneratedDate?.isAfter(today) == true
        return if (oneIsAlreadyAhead) due else due + ahead.take(1)
    }
}
