package com.kyovo.cents.domain.model

data class ProjectProgress(
    val target: Money?,
    val expenses: Money,
    val incomes: Money,
    val transactionCount: Int,
    val alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
)
{
    val net: Long
        get() = expenses.value - incomes.value

    val remaining: Long?
        get() = (target?.value)?.minus(net)

    val isOverTarget: Boolean
        get()
        {
            if (target == null) return false
            return net > target.value
        }

    /**
     * How near the target the project is, with the bands of a budget: over it, or close to it from the project's
     * own [alertThreshold] (compared on whole cents, never floats); null below, and for a project without a
     * target. A refund lowers the net cost, so it lowers the level too.
     */
    fun alertLevel(): BudgetAlertLevel?
    {
        if (target == null) return null
        return when
        {
            isOverTarget                                               -> BudgetAlertLevel.OVER
            net * 100 >= target.value * alertThreshold.percent         -> BudgetAlertLevel.CLOSE_TO_LIMIT
            else                                                       -> null
        }
    }
}
