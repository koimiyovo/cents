package com.kyovo.cents.work

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyScheduleTest
{
    private val paris = ZoneId.of("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, paris)

    @Test
    fun `waits until this morning when it is still ahead`()
    {
        assertThat(delayUntilNext(8, at(2026, 9, 15, 6, 30))).isEqualTo(Duration.ofMinutes(90))
    }

    @Test
    fun `waits until tomorrow morning once the hour has passed`()
    {
        assertThat(delayUntilNext(8, at(2026, 9, 15, 21, 0))).isEqualTo(Duration.ofHours(11))
    }

    @Test
    fun `exactly on the hour counts as passed, so the next run is tomorrow's`()
    {
        assertThat(delayUntilNext(8, at(2026, 9, 15, 8, 0))).isEqualTo(Duration.ofHours(24))
    }

    // Local time, not a fixed 24 hours: on the night the clocks go back (last Sunday of October in
    // Europe/Paris) the day has 25 hours.
    @Test
    fun `follows the local clock across a daylight saving change`()
    {
        assertThat(delayUntilNext(8, at(2026, 10, 24, 21, 0))).isEqualTo(Duration.ofHours(12))
    }
}
