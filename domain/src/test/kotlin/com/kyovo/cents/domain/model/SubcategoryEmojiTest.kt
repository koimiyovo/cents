package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidSubcategoryEmojiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The domain treats the emoji as an opaque piece of text: whether it really is an emoji is the
 * picker's job (ZWJ sequences, skin tones and flags make a reliable check in the domain unrealistic).
 * It only refuses what can't be one: nothing, or far more text than any emoji needs.
 */
class SubcategoryEmojiTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    fun `refuses an empty or blank emoji`(value: String)
    {
        // WHEN / THEN
        assertThatThrownBy { SubcategoryEmoji(value) }
            .isInstanceOf(InvalidSubcategoryEmojiException::class.java)
    }

    // A cart, a flag (two code points), a keycap, and a family (four people joined by zero-width
    // joiners: 11 chars) — one emoji each, however many code points it takes.
    @ParameterizedTest
    @ValueSource(
        strings = [
            "🛒",
            "🇫🇷",
            "1️⃣",
            "👨‍👩‍👧‍👦",
        ],
    )
    fun `accepts a single emoji, however many code points it takes`(value: String)
    {
        // WHEN
        val emoji = SubcategoryEmoji(value)

        // THEN
        assertThat(emoji.value).isEqualTo(value)
    }

    @Test
    fun `refuses a long text`()
    {
        // WHEN / THEN
        assertThatThrownBy { SubcategoryEmoji("Alimentation et courses") }
            .isInstanceOf(InvalidSubcategoryEmojiException::class.java)
    }

    @Test
    fun `trims surrounding whitespace`()
    {
        // WHEN
        val emoji = SubcategoryEmoji("  🛒 ")

        // THEN
        assertThat(emoji.value).isEqualTo("🛒")
    }

    @Test
    fun `accepts an emoji of exactly 16 characters`()
    {
        // GIVEN
        val text = "a".repeat(16)

        // WHEN / THEN
        assertThat(SubcategoryEmoji(text).value).isEqualTo(text)
    }

    @Test
    fun `refuses an emoji of 17 characters`()
    {
        // WHEN / THEN
        assertThatThrownBy { SubcategoryEmoji("a".repeat(17)) }
            .isInstanceOf(InvalidSubcategoryEmojiException::class.java)
    }

    @Test
    fun `the limit is checked after trimming`()
    {
        // GIVEN 16 characters between spaces
        val text = "a".repeat(16)

        // WHEN / THEN
        assertThat(SubcategoryEmoji("  $text ").value).isEqualTo(text)
    }
}
