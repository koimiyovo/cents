package com.kyovo.cents.domain.model

data class BudgetProgress(
    val limit: Money,
    val spent: Money,
    val alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
)
{
    val remaining: Long = limit.value - spent.value
    val isOverspent: Boolean = spent.value > limit.value

    fun alertLevel(): BudgetAlertLevel?
    {
        return when
        {
            isOverspent                                               -> BudgetAlertLevel.OVER
            spent.value * 100 >= limit.value * alertThreshold.percent -> BudgetAlertLevel.CLOSE_TO_LIMIT
            else                                                      -> null
        }
    }
}
