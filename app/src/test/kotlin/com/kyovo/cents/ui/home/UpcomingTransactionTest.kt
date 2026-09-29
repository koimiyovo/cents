package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which transactions the list marks "À venir": those dated on a day after today. It goes by the day, not
 * by the exact instant — a recurring expense generated for this very afternoon is due today (the
 * notification says "enregistrée aujourd'hui"), and must not read as "à venir" until it has gone by.
 */
class UpcomingTransactionTest
{
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 9, 29)

    private fun at(day: String, time: String = "12:00") = Instant.parse("${day}T$time:00Z")

    @Test
    fun `a transaction of a day after today is upcoming`()
    {
        assertThat(isUpcoming(at("2026-09-30"), today, ZoneId.of("UTC"))).isTrue()
        assertThat(isUpcoming(at("2026-12-25"), today, ZoneId.of("UTC"))).isTrue()
    }

    @Test
    fun `a transaction of today is not upcoming, whatever its time`()
    {
        assertThat(isUpcoming(at("2026-09-29", "00:00"), today, ZoneId.of("UTC"))).isFalse()
        assertThat(isUpcoming(at("2026-09-29", "23:59"), today, ZoneId.of("UTC"))).isFalse()
    }

    @Test
    fun `a transaction of a past day is not upcoming`()
    {
        assertThat(isUpcoming(at("2026-09-28"), today, ZoneId.of("UTC"))).isFalse()
        assertThat(isUpcoming(at("2020-01-01"), today, ZoneId.of("UTC"))).isFalse()
    }

    @Test
    fun `the day is the one of the device's zone, not UTC`()
    {
        // 23:30 UTC on the 29th is already 01:30 on the 30th in Paris (UTC+2 in September)
        val lateEvening = at("2026-09-29", "23:30")

        assertThat(isUpcoming(lateEvening, today, ZoneId.of("UTC"))).isFalse()
        assertThat(isUpcoming(lateEvening, today, paris)).isTrue()
    }

    @Test
    fun `just before midnight of today is not upcoming, just after is`()
    {
        // In Paris, "today" ends at 22:00 UTC (00:00 the next day, UTC+2)
        assertThat(isUpcoming(at("2026-09-29", "21:59"), today, paris)).isFalse()
        assertThat(isUpcoming(at("2026-09-29", "22:00"), today, paris)).isTrue()
    }
}
