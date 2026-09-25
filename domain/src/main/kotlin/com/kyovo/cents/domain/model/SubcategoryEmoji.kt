package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidSubcategoryEmojiException

/**
 * An opaque piece of text as far as the domain is concerned: whether it really is an emoji is the
 * picker's job (ZWJ sequences, skin tones and flags make a reliable check unrealistic here). It only
 * refuses what can't be one — nothing, or far more text than any emoji needs.
 */
@JvmInline
value class SubcategoryEmoji private constructor(val value: String)
{
    init
    {
        if (value.isBlank() || value.length > MAX_LENGTH) throw InvalidSubcategoryEmojiException()
    }

    companion object
    {
        /** A family of four is 11 chars (four people joined by zero-width joiners). */
        private const val MAX_LENGTH = 16

        operator fun invoke(value: String): SubcategoryEmoji
        {
            return SubcategoryEmoji(value.trim())
        }
    }
}
