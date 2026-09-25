package com.kyovo.cents.infrastructure.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UuidSubcategoryIdGeneratorTest
{
    @Test
    fun `every generated id is different`()
    {
        // GIVEN
        val generator = UuidSubcategoryIdGenerator()

        // WHEN
        val ids = List(100) { generator.generate() }

        // THEN
        assertThat(ids).doesNotHaveDuplicates()
    }
}
