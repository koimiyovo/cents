package com.kyovo.cents.infrastructure.persistence.room

import java.time.Instant

private const val NANOS_PER_SECOND = 1_000_000_000L

/**
 * An instant as a number of nanoseconds since 1970, which is how the database stores dates. Milliseconds
 * would be simpler, but a clock gives finer instants, and an instant that changed by being saved would
 * no longer be equal to itself. A `Long` of nanoseconds runs out in the year 2262: an instant further
 * away throws an [ArithmeticException] rather than being stored as a wrong date.
 */
fun Instant.toEpochNanos(): Long
{
    return Math.addExact(Math.multiplyExact(epochSecond, NANOS_PER_SECOND), nano.toLong())
}

fun Long.toInstantFromEpochNanos(): Instant
{
    // floorDiv/floorMod so that an instant before 1970 (negative) splits into seconds and nanoseconds correctly.
    return Instant.ofEpochSecond(Math.floorDiv(this, NANOS_PER_SECOND), Math.floorMod(this, NANOS_PER_SECOND))
}
