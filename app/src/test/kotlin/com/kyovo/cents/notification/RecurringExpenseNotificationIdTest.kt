package com.kyovo.cents.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RecurringExpenseNotificationIdTest
{
    private val rent = UUID.fromString("3f2b8c1e-7a4d-4e6b-9c15-2d8f0a6b7e41")
    private val gym = UUID.fromString("a91c5d07-42be-4f3a-8d60-15e7c3b2f9a8")
    private val today = LocalDate.of(2026, 9, 15)

    @Test
    fun `is the same for the same rule and day`()
    {
        assertThat(recurringExpenseNotificationId(rent, today)).isEqualTo(recurringExpenseNotificationId(rent, today))
    }

    @Test
    fun `differs from one day to the next`()
    {
        assertThat(recurringExpenseNotificationId(rent, today))
            .isNotEqualTo(recurringExpenseNotificationId(rent, today.plusDays(1)))
    }

    @Test
    fun `differs from one rule to another on the same day`()
    {
        assertThat(recurringExpenseNotificationId(rent, today)).isNotEqualTo(recurringExpenseNotificationId(gym, today))
    }
}
