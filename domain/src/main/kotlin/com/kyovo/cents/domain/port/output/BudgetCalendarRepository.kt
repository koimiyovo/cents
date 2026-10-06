package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.BudgetStartDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface BudgetCalendarRepository
{
    fun observe(): Flow<BudgetCalendar>
    suspend fun saveDefaultStartDay(day: BudgetStartDay)
    suspend fun saveCycleStart(date: LocalDate)
    suspend fun deleteCycleStart(month: YearMonth)
}