package com.kyovo.cents.domain.model

@JvmInline
value class TransactionDescription private constructor(val value: String)
{
    companion object
    {
        fun of(value: String?): TransactionDescription?
        {
            if (value.isNullOrBlank()) return null
            return TransactionDescription(value.trim())
        }
    }
}
