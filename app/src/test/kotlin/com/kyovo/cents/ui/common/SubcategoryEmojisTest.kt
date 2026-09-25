package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.SubcategoryEmoji
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SubcategoryEmojisTest
{
    @Test
    fun `the picker offers a good choice of emojis`()
    {
        assertThat(SUBCATEGORY_EMOJIS.size).isGreaterThanOrEqualTo(30)
    }

    @Test
    fun `there is no duplicate`()
    {
        assertThat(SUBCATEGORY_EMOJIS).doesNotHaveDuplicates()
    }

    // Anything the picker hands to the domain must be accepted by it: an entry it would refuse
    // (blank, too long) would turn a tap into an error the user can't explain.
    @Test
    fun `every emoji offered is accepted by the domain, as it is`()
    {
        SUBCATEGORY_EMOJIS.forEach { emoji ->
            assertThat(SubcategoryEmoji(emoji).value).describedAs(emoji).isEqualTo(emoji)
        }
    }
}
