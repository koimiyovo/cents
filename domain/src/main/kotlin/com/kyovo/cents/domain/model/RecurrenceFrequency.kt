package com.kyovo.cents.domain.model

/**
 * How often a [RecurringExpense] repeats. [RecurringExpense.interval] then says "every how many" of this
 * unit — an interval of 3 with [MONTHLY] is "every three months".
 */
enum class RecurrenceFrequency
{
    WEEKLY,
    MONTHLY,
    YEARLY,
}
