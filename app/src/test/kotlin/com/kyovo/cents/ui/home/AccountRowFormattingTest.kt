package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountEmojiTest
{
    @Test
    fun `uses a bank emoji for a checking account`()
    {
        // WHEN / THEN
        assertThat(accountEmoji(AccountType.CHECKING)).isEqualTo("🏦")
    }

    @Test
    fun `uses a piggy bank emoji for a savings account`()
    {
        // WHEN / THEN
        assertThat(accountEmoji(AccountType.SAVINGS)).isEqualTo("🐷")
    }
}

class TruncatedDescriptionTest
{
    @Test
    fun `returns the value unchanged when shorter than the max length`()
    {
        // WHEN / THEN
        assertThat(truncatedDescription("Short text", maxLength = 40)).isEqualTo("Short text")
    }

    @Test
    fun `returns the value unchanged when exactly at the max length`()
    {
        // GIVEN
        val exactlyFortyChars = "a".repeat(40)

        // WHEN / THEN
        assertThat(truncatedDescription(exactlyFortyChars, maxLength = 40)).isEqualTo(exactlyFortyChars)
    }

    @Test
    fun `truncates and appends an ellipsis when longer than the max length`()
    {
        // GIVEN
        val fortyOneChars = "a".repeat(41)

        // WHEN
        val result = truncatedDescription(fortyOneChars, maxLength = 40)

        // THEN
        assertThat(result).isEqualTo("a".repeat(39) + "…")
        assertThat(result).hasSize(40)
    }

    @Test
    fun `trims trailing whitespace before appending the ellipsis`()
    {
        // WHEN
        val result = truncatedDescription("Hello world foo", maxLength = 7)

        // THEN
        assertThat(result).isEqualTo("Hello…")
    }

    @Test
    fun `defaults to a max length of 40 characters`()
    {
        // GIVEN
        val fortyFiveChars = "x".repeat(45)

        // WHEN
        val result = truncatedDescription(fortyFiveChars)

        // THEN
        assertThat(result).isEqualTo("x".repeat(39) + "…")
    }
}
