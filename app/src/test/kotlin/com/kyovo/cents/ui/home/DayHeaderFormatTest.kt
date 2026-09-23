package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DayHeaderFormatTest
{
    private val today = LocalDate.of(2026, 9, 23)

    @Test
    fun `recent date has no year`()
    {
        assertThat(formatDayHeader(LocalDate.of(2026, 9, 19), today)).isEqualTo("19 septembre")
    }

    @Test
    fun `date just under a year old has no year`()
    {
        assertThat(formatDayHeader(LocalDate.of(2025, 9, 24), today)).isEqualTo("24 septembre")
    }

    @Test
    fun `date exactly one year old shows the year`()
    {
        assertThat(formatDayHeader(LocalDate.of(2025, 9, 23), today)).isEqualTo("23 septembre 2025")
    }

    @Test
    fun `date more than a year old shows the year`()
    {
        assertThat(formatDayHeader(LocalDate.of(2025, 8, 19), today)).isEqualTo("19 août 2025")
    }
}
