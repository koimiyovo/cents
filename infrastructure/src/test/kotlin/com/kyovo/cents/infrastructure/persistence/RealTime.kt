package com.kyovo.cents.infrastructure.persistence

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Runs a test in real time. The contract tests cannot use `runTest`: a database answers on its own
 * threads, and the virtual clock of `runTest` would run out while the test waits for it.
 */
fun realTime(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(block = block)

/**
 * Waits (at most a few seconds) until the flow emits a value that [matches], and gives it back. A
 * database may fold several quick changes into one emission, so the tests wait for the state they
 * expect instead of counting emissions.
 */
suspend fun <T> Flow<T>.awaitMatching(matches: (T) -> Boolean): T = withTimeout(5.seconds) { first(matches) }
