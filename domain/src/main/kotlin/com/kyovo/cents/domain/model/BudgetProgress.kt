package com.kyovo.cents.domain.model

data class BudgetProgress(val limit: Money, val spent: Money)
{
    val remaining: Long = limit.value - spent.value
    val isOverspent: Boolean = spent.value > limit.value
}
