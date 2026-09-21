package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidAccountNameException

@JvmInline
value class AccountName(val value: String)
{
    init
    {
        if (value.isBlank()) throw InvalidAccountNameException()
    }
}