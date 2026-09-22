package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidAccountNameException

@JvmInline
value class AccountName private constructor(val value: String)
{
    init
    {
        if (value.isBlank()) throw InvalidAccountNameException()
    }

    companion object
    {
        operator fun invoke(value: String): AccountName
        {
            return AccountName(value.trim())
        }
    }

    fun matches(other: AccountName): Boolean
    {
        return value.equals(other.value, ignoreCase = true)
    }

}