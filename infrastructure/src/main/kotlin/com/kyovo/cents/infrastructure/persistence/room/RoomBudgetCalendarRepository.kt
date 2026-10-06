package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth

/** The budget calendar, stored in the Room database: the declared starts in one table, the default day in another. */
class RoomBudgetCalendarRepository(private val dao: BudgetCalendarDao) : BudgetCalendarRepository
{
    override suspend fun saveDefaultStartDay(day: BudgetStartDay)
    {
        dao.upsertSettings(BudgetSettingsEntity(defaultStartDay = day.value))
    }

    override suspend fun saveCycleStart(date: LocalDate)
    {
        dao.upsertStart(date.toCycleStartEntity())
    }

    override suspend fun deleteCycleStart(month: YearMonth)
    {
        dao.deleteStart(month.toKey())
    }

    // Either table changing re-emits; `distinctUntilChanged` keeps a write that changes nothing from reaching
    // the screens (BudgetCalendar is a data class, so "nothing" is a plain equality).
    override fun observe(): Flow<BudgetCalendar>
    {
        return combine(dao.observeSettings(), dao.observeStarts(), ::toBudgetCalendar).distinctUntilChanged()
    }
}
