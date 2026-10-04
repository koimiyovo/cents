package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidRecurringTransactionEndException
import com.kyovo.cents.domain.exception.InvalidRecurringTransactionIntervalException
import com.kyovo.cents.domain.exception.InvalidTransactionAmountException
import java.time.LocalDate

/**
 * A planned transaction that recurs — a rent, a salary, a subscription — weekly, monthly, yearly, or anything
 * "every N [weeks/months/years]" via [interval] (interval 3 with [RecurrenceFrequency.MONTHLY] is "every three
 * months"), from which real [Transaction]s are generated as their due dates come, instead of typing each one in
 * by hand. It is an income or an expense ([category]), never a transfer or an opening deposit — the same
 * restriction as [Transaction.recorded] — and it is chosen when the rule is created and never changes: what has
 * been generated so far would contradict it.
 *
 * Occurrences fall on [startDate] and every [interval] [frequency] after it, computed fresh from
 * [startDate] each time — never by chaining from the previous occurrence — so a short month's clamp (the
 * 31st becomes the 28th in February) never drifts the following ones. [lastGeneratedDate] is the
 * bookkeeping of how far generation has already gone, so a due date is never generated twice; null before
 * the first run ever generates anything for it.
 */
data class RecurringTransaction(
    val id: RecurringTransactionId,
    val accountId: AccountId,
    val category: RecordableTransactionCategory,
    val amount: Money,
    val title: TransactionTitle,
    val subcategoryId: SubcategoryId?,
    val description: TransactionDescription?,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val lastGeneratedDate: LocalDate? = null,
)
{
    init
    {
        if (amount.isZero()) throw InvalidTransactionAmountException()
        if (interval < 1) throw InvalidRecurringTransactionIntervalException()
        if (endDate != null && endDate < startDate) throw InvalidRecurringTransactionEndException()
    }

    /**
     * The occurrence dates still to generate, oldest first: after [lastGeneratedDate] (or from [startDate]
     * when nothing has been generated yet), up to and including [until] — never past [endDate] when there
     * is one. Empty when the rule has not started yet, has already ended, or is already caught up to
     * [until].
     */
    fun pendingOccurrences(until: LocalDate): List<LocalDate>
    {
        val last = if (endDate != null && endDate < until) endDate else until
        if (startDate.isAfter(last)) return emptyList()

        return generateSequence(0) { it + 1 }
            .map { occurrenceDate(it) }
            .dropWhile { lastGeneratedDate != null && !it.isAfter(lastGeneratedDate) }
            .takeWhile { !it.isAfter(last) }
            .toList()
    }

    /**
     * Whether an occurrence falls exactly on [date], whether or not it was already generated: a question
     * about the rule's calendar only, [lastGeneratedDate] plays no part. Never before [startDate] nor after
     * [endDate].
     */
    fun occursOn(date: LocalDate): Boolean
    {
        if (date.isBefore(startDate) || (endDate != null && date.isAfter(endDate))) return false

        // ponytail: walks the occurrences from the start (one per week at worst), fine for a rule's lifetime;
        // compute the index from the gap between the dates if a rule ever needs to be asked thousands of times.
        return generateSequence(0) { it + 1 }
            .map { occurrenceDate(it) }
            .first { !it.isBefore(date) } == date
    }

    private fun occurrenceDate(occurrenceIndex: Int): LocalDate
    {
        val steps = occurrenceIndex.toLong() * interval
        return when (frequency)
        {
            RecurrenceFrequency.WEEKLY  -> startDate.plusWeeks(steps)
            RecurrenceFrequency.MONTHLY -> startDate.plusMonths(steps)
            RecurrenceFrequency.YEARLY  -> startDate.plusYears(steps)
        }
    }
}
