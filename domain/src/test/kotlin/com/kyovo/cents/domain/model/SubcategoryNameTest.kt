package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidSubcategoryNameException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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

    // "Education" and "Éducation" are the same name as far as uniqueness goes: the accent is a matter
    // of how quickly it was typed on a phone, and two subcategories that read the same must not coexist.
    @ParameterizedTest
    @ValueSource(strings = ["Education", "Éducation", "éducation", "EDUCATION", "ÉDUCATION", "  Éducation  ", "E\u0301ducation"])
    fun `matches a name that only differs by accents, case or surrounding whitespace`(other: String)
    {
        // GIVEN
        val name = SubcategoryName("Éducation")

        // WHEN / THEN
        assertThat(name.matches(SubcategoryName(other))).isTrue()
    }

    @ParameterizedTest
    @CsvSource("Café,Cafe", "Garçon,Garcon", "Où,Ou", "Crème brûlée,creme brulee")
    fun `an accent is ignored in either direction`(withAccent: String, without: String)
    {
        assertThat(SubcategoryName(withAccent).matches(SubcategoryName(without))).isTrue()
        assertThat(SubcategoryName(without).matches(SubcategoryName(withAccent))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Educations", "Éducatio", "Ecole", "Education physique"])
    fun `ignoring accents does not make different names match`(other: String)
    {
        // GIVEN
        val name = SubcategoryName("Éducation")

        // WHEN / THEN
        assertThat(name.matches(SubcategoryName(other))).isFalse()
    }

    @Test
    fun `a name is limited to 40 characters`()
    {
        assertThat(SubcategoryName.MAX_LENGTH).isEqualTo(40)
    }

    @Test
    fun `accepts a name of exactly the limit`()
    {
        // GIVEN
        val text = "a".repeat(SubcategoryName.MAX_LENGTH)

        // WHEN / THEN
        assertThat(SubcategoryName(text).value).isEqualTo(text)
    }

    @Test
    fun `refuses a name one character over the limit`()
    {
        // WHEN / THEN
        assertThatThrownBy { SubcategoryName("a".repeat(SubcategoryName.MAX_LENGTH + 1)) }
            .isInstanceOf(InvalidSubcategoryNameException::class.java)
    }

    @Test
    fun `the limit is checked after trimming`()
    {
        // GIVEN
        val text = "a".repeat(SubcategoryName.MAX_LENGTH)

        // WHEN / THEN the surrounding spaces don't count, the characters do
        assertThat(SubcategoryName("  $text  ").value).isEqualTo(text)
        assertThatThrownBy { SubcategoryName("  ${text}a  ") }
            .isInstanceOf(InvalidSubcategoryNameException::class.java)
    }
}
