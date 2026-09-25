package com.kyovo.cents.application.fakes

import org.assertj.core.api.Assertions.assertThat

/**
 * `assertThatThrownBy` for a block that suspends: AssertJ's own takes a plain lambda, which can't call
 * a suspend function. It returns the same kind of assertion, so `.isInstanceOf(...)` chains as usual.
 */
suspend fun assertThatThrownBySuspending(block: suspend () -> Unit) =
    assertThat(catching(block)).describedAs("an exception was expected, none was thrown").isNotNull()

private suspend fun catching(block: suspend () -> Unit): Throwable? =
    try
    {
        block()
        null
    } catch (e: Throwable)
    {
        e
    }
