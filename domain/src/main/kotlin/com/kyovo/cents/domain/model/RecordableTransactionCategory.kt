package com.kyovo.cents.domain.model

enum class RecordableTransactionCategory
{
    INCOME,
    EXPENSE;

    fun toTransactionCategory(): TransactionCategory
    {
        return when (this)
        {
            INCOME  -> TransactionCategory.INCOME
            EXPENSE -> TransactionCategory.EXPENSE
        }
    }
}
