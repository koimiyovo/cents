package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidSubcategoryNameException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SubcategoryNameTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    fun `refuses an empty or blank name`(value: String)
    {
        // WHEN / THEN
        assertThatThrownBy { SubcategoryName(value) }
            .isInstanceOf(InvalidSubcategoryNameException::class.java)
    }

    @Test
    fun `accepts a name with content`()
    {
        // WHEN
        val name = SubcategoryName("Alimentation")

        // THEN
        assertThat(name.value).isEqualTo("Alimentation")
    }

    @Test
    fun `trims surrounding whitespace from the name`()
    {
        // WHEN
        val name = SubcategoryName("  Alimentation  ")

        // THEN
        assertThat(name.value).isEqualTo("Alimentation")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Alimentation", "alimentation", "ALIMENTATION", "  Alimentation  "])
    fun `matches a name that only differs by case or surrounding whitespace`(other: String)
    {
        // GIVEN
        val name = SubcategoryName("Alimentation")

        // WHEN / THEN
        assertThat(name.matches(SubcategoryName(other))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Alimentations", "Aliment", "Transport"])
    fun `does not match a different name`(other: String)
    {
        // GIVEN
        val name = SubcategoryName("Alimentation")

        // WHEN / THEN
        assertThat(name.matches(SubcategoryName(other))).isFalse()
    }
}
