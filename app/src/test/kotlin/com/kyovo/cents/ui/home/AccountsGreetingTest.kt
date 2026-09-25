package com.kyovo.cents.ui.home

import com.kyovo.cents.R
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalTime

class AccountsGreetingTest
{
    @Test
    fun `shows the day greeting in the morning`()
    {
        // WHEN
        val greeting = greetingStringRes(LocalTime.of(9, 0))

        // THEN
        assertThat(greeting).isEqualTo(R.string.accounts_greeting_day)
    }

    @Test
    fun `shows the day greeting right at the morning threshold`()
    {
        // WHEN
        val greeting = greetingStringRes(LocalTime.of(6, 0))

        // THEN
        assertThat(greeting).isEqualTo(R.string.accounts_greeting_day)
    }

    @Test
    fun `shows the day greeting just before the evening threshold`()
    {
        // WHEN
        val greeting = greetingStringRes(LocalTime.of(17, 59))

        // THEN
        assertThat(greeting).isEqualTo(R.string.accounts_greeting_day)
    }

    @Test
    fun `shows the evening greeting exactly at the evening threshold`()
    {
        // WHEN
        val greeting = greetingStringRes(LocalTime.of(18, 0))

        // THEN
        assertThat(greeting).isEqualTo(R.string.accounts_greeting_evening)
    }

    @Test
    fun `shows the evening greeting at night`()
    {
        // WHEN
        val greeting = greetingStringRes(LocalTime.of(22, 30))

        // THEN
        assertThat(greeting).isEqualTo(R.string.accounts_greeting_evening)
    }

    @Test
    fun `shows the evening greeting in the middle of the night`()
    {
        // WHEN
        val greeting = greetingStringRes(LocalTime.of(3, 0))

        // THEN
        assertThat(greeting).isEqualTo(R.string.accounts_greeting_evening)
    }
}
