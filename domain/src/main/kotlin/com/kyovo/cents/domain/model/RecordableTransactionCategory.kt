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

    fun accepts(subcategory: TransactionSubcategory): Boolean
    {
        return when (this)
        {
            EXPENSE -> subcategory is ExpenseSubcategory
            INCOME  -> subcategory is IncomeSubcategory
        }
    }
}