package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NameInputTest
{
    @Test
    fun `leaves a text within the limit as it is, spaces included`()
    {
        assertThat(limitNameInput("Alimentation", 40)).isEqualTo("Alimentation")
        assertThat(limitNameInput("  Alimentation ", 40)).isEqualTo("  Alimentation ")
        assertThat(limitNameInput("", 40)).isEqualTo("")
    }

    @Test
    fun `leaves a text of exactly the limit`()
    {
        val text = "a".repeat(40)

        assertThat(limitNameInput(text, 40)).isEqualTo(text)
    }

    @Test
    fun `typing one character too many changes nothing`()
    {
        // GIVEN the field already holds 40 characters
        val text = "a".repeat(40)

        // WHEN one more is typed
        val result = limitNameInput(text + "b", 40)

        // THEN
        assertThat(result).isEqualTo(text)
    }

    @Test
    fun `a long text pasted in is cut, keeping its beginning`()
    {
        assertThat(limitNameInput("abcdefghij", 4)).isEqualTo("abcd")
    }

    @Test
    fun `never leaves half an emoji at the end`()
    {
        // GIVEN an emoji (two chars) straddling the limit
        val text = "a".repeat(39) + "🛒"

        // WHEN
        val result = limitNameInput(text, 40)

        // THEN it is dropped whole, not cut in two
        assertThat(result).isEqualTo("a".repeat(39))
    }

    @Test
    fun `keeps an emoji that fits`()
    {
        val text = "a".repeat(38) + "🛒"

        assertThat(limitNameInput(text, 40)).isEqualTo(text)
    }

    // What the field lets through is never refused by the domain for its length.
    @Test
    fun `a subcategory name typed through the field is always accepted by the domain`()
    {
        val typed = limitNameInput("x".repeat(500), SubcategoryName.MAX_LENGTH)

        assertThat(SubcategoryName(typed).value).hasSize(SubcategoryName.MAX_LENGTH)
    }

    @Test
    fun `an account name typed through the field is always accepted by the domain`()
    {
        val typed = limitNameInput("x".repeat(500), AccountName.MAX_LENGTH)

        assertThat(AccountName(typed).value).hasSize(AccountName.MAX_LENGTH)
    }
}
