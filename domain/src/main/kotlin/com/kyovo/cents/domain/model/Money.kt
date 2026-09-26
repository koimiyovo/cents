package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidMoneyAmountException

@JvmInline
value class Money(val value: Long)
{
    init
    {
        if (value < 0) throw InvalidMoneyAmountException()
    }

    fun isZero(): Boolean
    {
        return value == 0L
    }

    fun isNotZero(): Boolean
    {
        return !isZero()
    }
}