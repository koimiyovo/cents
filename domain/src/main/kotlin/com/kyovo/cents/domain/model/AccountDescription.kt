package com.kyovo.cents.domain.model

@JvmInline
value class AccountDescription private constructor(val value: String)
{
    companion object
    {
        fun of(value: String?): AccountDescription?
        {
            if (value.isNullOrBlank()) return null
            return AccountDescription(value.trim())
        }
    }
}
