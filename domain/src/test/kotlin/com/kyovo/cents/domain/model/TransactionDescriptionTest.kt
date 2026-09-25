package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TransactionDescriptionTest
{
    @Test
    fun `returns null when given null`()
    {
        // WHEN / THEN
        assertThat(TransactionDescription.of(null)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    fun `returns null when given a blank string`(value: String)
    {
        // WHEN / THEN
        assertThat(TransactionDescription.of(value)).isNull()
    }

    @Test
    fun `returns a description with the trimmed value when given non-blank text`()
    {
        // WHEN
        val description = TransactionDescription.of("  Courses de la semaine  ")

        // THEN
        assertThat(description).isEqualTo(TransactionDescription.of("Courses de la semaine"))
    }
}
