package com.kyovo.cents.domain.model

data class ProjectProgress(
    val target: Money?,
    val expenses: Money,
    val incomes: Money,
    val transactionCount: Int
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
}
