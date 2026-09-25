package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidAccountNameException

@JvmInline
value class AccountName private constructor(val value: String)
{
    init
    {
        if (value.isBlank() || value.length > MAX_LENGTH) throw InvalidAccountNameException()
    }

    companion object
    {
        const val MAX_LENGTH = 60

        operator fun invoke(value: String): AccountName
        {
            return AccountName(value.trim())
        }
    }

    fun matches(other: AccountName): Boolean
    {
        return value.equals(other.value, ignoreCase = true)
    }

    fun contains(query: String): Boolean
    {
        return value.contains(query, ignoreCase = true)
    }

}