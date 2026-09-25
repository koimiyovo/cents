package com.kyovo.cents.infrastructure.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UuidTransactionIdGeneratorTest
{
    @Test
    fun `every generated id is different`()
    {
        // GIVEN
        val generator = UuidTransactionIdGenerator()

        // WHEN
        val ids = List(100) { generator.generate() }

        // THEN
        assertThat(ids).doesNotHaveDuplicates()
    }
}
