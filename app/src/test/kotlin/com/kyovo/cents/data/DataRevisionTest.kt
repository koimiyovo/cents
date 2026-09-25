package com.kyovo.cents.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The screens read once and re-read when this counter changes: it stands in for reactive queries
 * until Room's Flows replace it. So what matters is that every write moves it, one step at a time, and
 * that two counters never affect each other.
 */
class DataRevisionTest
{
    @Test
    fun `starts at zero`()
    {
        assertThat(DataRevision().value.value).isZero()
    }

    @Test
    fun `each bump moves it up by one`()
    {
        // GIVEN
        val revision = DataRevision()

        // WHEN
        revision.bump()
        revision.bump()
        revision.bump()

        // THEN
        assertThat(revision.value.value).isEqualTo(3)
    }

    @Test
    fun `every bump gives a value the screens have not seen`()
    {
        // GIVEN
        val revision = DataRevision()
        val seen = mutableListOf(revision.value.value)

        // WHEN
        repeat(5)
        {
            revision.bump()
            seen += revision.value.value
        }

        // THEN a screen keyed on it recomposes each time: no value comes back
        assertThat(seen).doesNotHaveDuplicates()
        assertThat(seen).isSorted()
    }

    @Test
    fun `the exposed flow follows the counter`()
    {
        // GIVEN
        val revision = DataRevision()
        val flow = revision.value

        // WHEN
        revision.bump()

        // THEN the same flow, read again, holds the new value
        assertThat(flow.value).isEqualTo(1)
    }

    @Test
    fun `two counters are independent`()
    {
        // GIVEN
        val first = DataRevision()
        val second = DataRevision()

        // WHEN
        first.bump()

        // THEN
        assertThat(first.value.value).isEqualTo(1)
        assertThat(second.value.value).isZero()
    }
}
