package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.infrastructure.persistence.realTime
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate

/** What only a database on disk can do: survive being closed. */
class RoomBudgetCalendarRepositoryTest
{
    @TempDir
    lateinit var folder: File

    private fun open(): CentsDatabase =
        Room.databaseBuilder<CentsDatabase>(File(folder, "cents.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun `what was saved is still there when the database is closed and opened again`() = realTime()
    {
        // GIVEN a default day and two declared starts saved, then the database closed (as when the app is killed)
        val first = open()
        RoomBudgetCalendarRepository(first.budgetCalendarDao()).also {
            it.saveDefaultStartDay(BudgetStartDay(25))
            it.saveCycleStart(LocalDate.of(2026, 9, 28))
            it.saveCycleStart(LocalDate.of(2026, 10, 27))
        }
        first.close()

        // WHEN the file is opened by a new database
        val second = open()
        val found = RoomBudgetCalendarRepository(second.budgetCalendarDao()).observe().first()
        second.close()

        // THEN
        assertThat(found).isEqualTo(BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 27))))
    }
}
