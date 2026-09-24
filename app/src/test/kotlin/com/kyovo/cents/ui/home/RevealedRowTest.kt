package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RevealedRowTest
{
    @Test
    fun `opening a row makes it the revealed one, closing whichever was revealed before`()
    {
        assertThat(revealedIdAfter(current = "a", id = "b", open = true)).isEqualTo("b")
        assertThat(revealedIdAfter(current = null, id = "b", open = true)).isEqualTo("b")
    }

    @Test
    fun `closing the revealed row leaves nothing revealed`()
    {
        assertThat(revealedIdAfter(current = "a", id = "a", open = false)).isNull()
    }

    @Test
    fun `a row closing itself does not close another one that is revealed`()
    {
        // e.g. a row springing back after the user opened a different one
        assertThat(revealedIdAfter(current = "b", id = "a", open = false)).isEqualTo("b")
    }
}
