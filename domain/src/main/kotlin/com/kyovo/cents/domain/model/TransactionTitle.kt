package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidTransactionTitleException

@JvmInline
value class TransactionTitle private constructor(val value: String)
{
    init
    {
        if (value.isBlank()) throw InvalidTransactionTitleException()
    }

    companion object
    {
        operator fun invoke(value: String): TransactionTitle
        {
            return TransactionTitle(value.trim())
        }
    }

    fun contains(query: String): Boolean
    {
        return value.contains(query, ignoreCase = true)
    }
}
