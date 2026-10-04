package com.kyovo.cents.infrastructure.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UuidProjectIdGeneratorTest
{
    @Test
    fun `every generated id is different`()
    {
        // GIVEN
        val generator = UuidProjectIdGenerator()

        // WHEN
        val ids = List(100) { generator.generate() }

        // THEN
        assertThat(ids).doesNotHaveDuplicates()
    }
}
