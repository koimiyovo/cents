package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidProjectNameException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProjectNameTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    fun `refuses an empty or blank name`(value: String)
    {
        assertThatThrownBy { ProjectName(value) }
            .isInstanceOf(InvalidProjectNameException::class.java)
    }

    @Test
    fun `trims surrounding whitespace from the name`()
    {
        assertThat(ProjectName("  Voyage au Japon  ").value).isEqualTo("Voyage au Japon")
    }

    @Test
    fun `accepts a name of exactly the maximum length, and refuses one character more`()
    {
        assertThat(ProjectName.MAX_LENGTH).isEqualTo(40)
        assertThat(ProjectName("a".repeat(40)).value).hasSize(40)
        assertThatThrownBy { ProjectName("a".repeat(41)) }
            .isInstanceOf(InvalidProjectNameException::class.java)
    }

    @Test
    fun `the length is checked after trimming`()
    {
        assertThat(ProjectName("  " + "a".repeat(40) + "  ").value).hasSize(40)
    }

    // Same rule as subcategories: what makes a name a duplicate is how it reads, not how it was typed.
    @ParameterizedTest
    @ValueSource(strings = ["Travaux cuisine", "travaux CUISINE", "  Travaux cuisine  "])
    fun `matches a name that only differs by case or surrounding whitespace`(other: String)
    {
        assertThat(ProjectName("Travaux cuisine").matches(ProjectName(other))).isTrue()
    }

    @Test
    fun `matches a name that only differs by accents, however they were typed`()
    {
        val decomposed = "Éte" // "É" as E + a combining acute accent

        assertThat(ProjectName("Été").matches(ProjectName("Ete"))).isTrue()
        assertThat(ProjectName("Ete").matches(ProjectName("été"))).isTrue()
        assertThat(ProjectName("Été").matches(ProjectName(decomposed))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Travaux cuisines", "Travaux", "Voyage"])
    fun `does not match a different name`(other: String)
    {
        assertThat(ProjectName("Travaux cuisine").matches(ProjectName(other))).isFalse()
    }
}
