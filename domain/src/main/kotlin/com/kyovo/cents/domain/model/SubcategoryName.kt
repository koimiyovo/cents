package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidSubcategoryNameException
import java.text.Normalizer

@JvmInline
value class SubcategoryName private constructor(val value: String)
{
    init
    {
        if (value.isBlank() || value.length > MAX_LENGTH) throw InvalidSubcategoryNameException()
    }

    companion object
    {
        const val MAX_LENGTH = 40

        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        operator fun invoke(value: String): SubcategoryName
        {
            return SubcategoryName(value.trim())
        }
    }

    /**
     * Whether the two read the same, which is what makes a name a duplicate: case, surrounding
     * spaces and accents are ignored ("Education" is "Éducation", however the accent was typed —
     * precomposed or as a separate combining mark). Accounts only ignore the case.
     */
    fun matches(other: SubcategoryName): Boolean
    {
        return comparable() == other.comparable()
    }

    private fun comparable(): String
    {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()
    }
}