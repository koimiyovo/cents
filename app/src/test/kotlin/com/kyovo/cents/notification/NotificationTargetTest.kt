package com.kyovo.cents.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationTargetTest
{
    @Test
    fun `every target is found again from its name`()
    {
        NotificationTarget.entries.forEach {
            assertThat(NotificationTarget.from(it.name)).isEqualTo(it)
        }
    }

    @Test
    fun `an intent without a target gives none`()
    {
        assertThat(NotificationTarget.from(null)).isNull()
    }

    @Test
    fun `a target this version does not know gives none`()
    {
        assertThat(NotificationTarget.from("Settings")).isNull()
    }
}
