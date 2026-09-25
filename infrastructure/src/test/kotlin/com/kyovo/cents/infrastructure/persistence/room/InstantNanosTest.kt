package com.kyovo.cents.infrastructure.persistence.room

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * An instant is stored as a number of nanoseconds since 1970. Milliseconds would be simpler, but a
 * clock gives finer instants than that, and an instant that changed by being saved would no longer
 * be equal to itself.
 */
class InstantNanosTest
{
    @Test
    fun `the epoch is zero`()
    {
        assertThat(Instant.EPOCH.toEpochNanos()).isZero()
    }

    @Test
    fun `an instant is counted in nanoseconds`()
    {
        assertThat(Instant.parse("1970-01-01T00:00:01.000000002Z").toEpochNanos()).isEqualTo(1_000_000_002L)
    }

    @Test
    fun `an instant comes back as it went, to the nanosecond`()
    {
        // GIVEN
        val instant = Instant.parse("2026-09-22T10:00:00.123456789Z")

        // WHEN / THEN
        assertThat(instant.toEpochNanos().toInstantFromEpochNanos()).isEqualTo(instant)
    }

    @Test
    fun `an instant before 1970 comes back as it went`()
    {
        // GIVEN
        val instant = Instant.parse("1969-12-31T23:59:59.500000001Z")

        // WHEN / THEN
        assertThat(instant.toEpochNanos().toInstantFromEpochNanos()).isEqualTo(instant)
    }

    @Test
    fun `later instants keep their order as numbers`()
    {
        assertThat(Instant.parse("2026-01-01T00:00:00Z").toEpochNanos())
            .isLessThan(Instant.parse("2026-01-01T00:00:00.000000001Z").toEpochNanos())
    }

    // A Long of nanoseconds runs out in the year 2262: better a loud failure than a wrong date.
    @Test
    fun `an instant too far away to fit is refused`()
    {
        assertThatThrownBy { Instant.parse("2300-01-01T00:00:00Z").toEpochNanos() }
            .isInstanceOf(ArithmeticException::class.java)
    }
}
