package com.kyovo.cents.domain.model

enum class RecordableTransactionType
{
    INCOME,
    EXPENSE;

    fun toTransactionType(): TransactionType
    {
        return when (this)
        {
            INCOME  -> TransactionType.INCOME
            EXPENSE -> TransactionType.EXPENSE
        }
    }
}