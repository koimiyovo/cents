package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidAlertThresholdException

/**
 * The share of a budget's limit, in percent, from which the budget counts as "close to its limit". Each
 * budget has its own. 100 means "only once the limit is reached".
 */
@JvmInline
value class AlertThreshold(val percent: Int)
{
    init
    {
        if (percent !in MIN..MAX) throw InvalidAlertThresholdException()
    }

    companion object
    {
        const val MIN = 1
        const val MAX = 100

        val DEFAULT = AlertThreshold(80)
    }
}
