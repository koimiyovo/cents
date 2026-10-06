package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetCalendarEntityMapperTest
{
    @Test
    fun `a cycle is keyed by year and month, like a budget's month`()
    {
        assertThat(YearMonth.of(2026, 10).toKey()).isEqualTo(202610)
    }

    @Test
    fun `a start date is filed under the cycle it opens and kept as its epoch day`()
    {
        // WHEN September 28th, which opens October
        val row = LocalDate.of(2026, 9, 28).toCycleStartEntity()

        // THEN
        assertThat(row).isEqualTo(BudgetCycleStartEntity(202610, LocalDate.of(2026, 9, 28).toEpochDay()))
    }

    @Test
    fun `no settings row means the default calendar`()
    {
        assertThat(toBudgetCalendar(emptyList(), emptyList())).isEqualTo(BudgetCalendar())
    }

    @Test
    fun `puts the default day and the declared starts back together`()
    {
        // WHEN
        val calendar = toBudgetCalendar(
            listOf(BudgetSettingsEntity(defaultStartDay = 25)),
            listOf(LocalDate.of(2026, 9, 28).toCycleStartEntity()),
        )

        // THEN
        assertThat(calendar).isEqualTo(BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 28))))
    }

    // A database that contradicts itself must say so, not be guessed at.
    @Test
    fun `refuses a start filed under a cycle it does not open`()
    {
        // GIVEN September 28th opens October, but the row says November
        val wrong = BudgetCycleStartEntity(202611, LocalDate.of(2026, 9, 28).toEpochDay())

        // WHEN / THEN
        assertThatThrownBy { toBudgetCalendar(emptyList(), listOf(wrong)) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `refuses a default start day outside 1 to 28`()
    {
        assertThatThrownBy { toBudgetCalendar(listOf(BudgetSettingsEntity(defaultStartDay = 30)), emptyList()) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
