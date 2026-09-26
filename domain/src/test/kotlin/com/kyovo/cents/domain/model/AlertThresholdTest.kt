package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidAlertThresholdException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The share of a budget's limit, in percent, from which the budget counts as "close to its limit". Each
 * budget has its own. Any whole percentage from 1 to 100 is a valid threshold: 100 means "only when the
 * limit is reached". The narrower range a slider offers is the screen's business, not the domain's.
 */
class AlertThresholdTest
{
    @ParameterizedTest
    @ValueSource(ints = [1, 50, 80, 99, 100])
    fun `accepts a whole percentage from 1 to 100`(percent: Int)
    {
        assertThat(AlertThreshold(percent).percent).isEqualTo(percent)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, -80, 101, 200])
    fun `refuses anything outside 1 to 100`(percent: Int)
    {
        assertThatThrownBy { AlertThreshold(percent) }
            .isInstanceOf(InvalidAlertThresholdException::class.java)
    }

    @Test
    fun `is 80 percent unless a budget says otherwise`()
    {
        assertThat(AlertThreshold.DEFAULT).isEqualTo(AlertThreshold(80))
    }
}
