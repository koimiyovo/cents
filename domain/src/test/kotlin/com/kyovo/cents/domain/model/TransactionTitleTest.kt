package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidTransactionTitleException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TransactionTitleTest
{
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    fun `refuses an empty or blank title`(value: String)
    {
        // WHEN / THEN
        assertThatThrownBy { TransactionTitle(value) }
            .isInstanceOf(InvalidTransactionTitleException::class.java)
    }

    @Test
    fun `accepts a title with content`()
    {
        // WHEN
        val title = TransactionTitle("Courses de la semaine")

        // THEN
        assertThat(title.value).isEqualTo("Courses de la semaine")
    }

    @Test
    fun `trims surrounding whitespace from the title`()
    {
        // WHEN
        val title = TransactionTitle("  Courses  ")

        // THEN
        assertThat(title.value).isEqualTo("Courses")
    }

    @ParameterizedTest
    @ValueSource(strings = ["Courses", "courses", "COURSES", "ours", ""])
    fun `contains a matching substring regardless of case`(query: String)
    {
        // GIVEN
        val title = TransactionTitle("Courses")

        // WHEN / THEN
        assertThat(title.contains(query)).isTrue()
    }

    @Test
    fun `does not contain an unrelated substring`()
    {
        // GIVEN
        val title = TransactionTitle("Courses")

        // WHEN / THEN
        assertThat(title.contains("Salaire")).isFalse()
    }
}
