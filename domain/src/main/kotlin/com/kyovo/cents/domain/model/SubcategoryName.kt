package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidSubcategoryNameException

@JvmInline
value class SubcategoryName private constructor(val value: String)
{
    init
    {
        if (value.isBlank()) throw InvalidSubcategoryNameException()
    }

    companion object
    {
        operator fun invoke(value: String): SubcategoryName
        {
            return SubcategoryName(value.trim())
        }
    }

    fun matches(other: SubcategoryName): Boolean
    {
        return value.equals(other.value, ignoreCase = true)
    }
}