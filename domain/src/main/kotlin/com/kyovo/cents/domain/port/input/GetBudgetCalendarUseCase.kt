package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.BudgetCalendar
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Where the budget cycles start and end. [observe] is for what stays on screen and must follow the user's
 * changes; [cycleOf] answers once, for what happens once (the alert check after a transaction, the daily
 * worker) and has no reason to stay subscribed.
 */
interface GetBudgetCalendarUseCase
{
    fun observe(): Flow<BudgetCalendar>

    /** The budget month whose cycle is open on [date]. */
    suspend fun cycleOf(date: LocalDate): YearMonth
}
