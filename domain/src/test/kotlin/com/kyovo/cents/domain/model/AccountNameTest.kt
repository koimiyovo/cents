package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidAccountNameException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AccountNameTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    fun `refuses an empty or blank name`(value: String)
    {
        // WHEN / THEN
        assertThatThrownBy { AccountName(value) }
            .isInstanceOf(InvalidAccountNameException::class.java)
    }

    @Test
    fun `accepts a name with content`()
    {
        // WHEN
        val name = AccountName("Livret A")

        // THEN
        assertThat(name.value).isEqualTo("Livret A")
    }

    @Test
    fun `trims surrounding whitespace from the name`()
    {
        // WHEN
        val name = AccountName("  Livret A  ")

        // THEN
        assertThat(name.value).isEqualTo("Livret A")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Livret A", "livret a", "LIVRET A", "  Livret A  "])
    fun `matches a name that only differs by case or surrounding whitespace`(other: String)
    {
        // GIVEN
        val name = AccountName("Livret A")

        // WHEN / THEN
        assertThat(name.matches(AccountName(other))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Livret B", "Livret", "Livret AA", "Compte courant"])
    fun `does not match a different name`(other: String)
    {
        // GIVEN
        val name = AccountName("Livret A")

        // WHEN / THEN
        assertThat(name.matches(AccountName(other))).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Livret", "livret", "LIVRET", "vret A", ""])
    fun `contains a matching substring regardless of case`(query: String)
    {
        // GIVEN
        val name = AccountName("Livret A")

        // WHEN / THEN
        assertThat(name.contains(query)).isTrue()
    }

    @Test
    fun `does not contain an unrelated substring`()
    {
        // GIVEN
        val name = AccountName("Livret A")

        // WHEN / THEN
        assertThat(name.contains("Compte")).isFalse()
    }
}
