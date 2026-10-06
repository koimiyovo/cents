package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidBudgetStartDayException

/**
 * The day of the month a budget cycle starts on when none was declared for it: 1 to 28, never 29 to 31, which
 * February could not honour. An invalid day cannot be built, so nothing downstream has to check it again.
 */
@JvmInline
value class BudgetStartDay(val value: Int)
{
    init
    {
        if (value !in MIN..MAX) throw InvalidBudgetStartDayException()
    }

    companion object
    {
        const val MIN = 1
        const val MAX = 28

        /** The 1st: cycles are calendar months. */
        val DEFAULT = BudgetStartDay(MIN)
    }
}
