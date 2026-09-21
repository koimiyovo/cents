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
}
