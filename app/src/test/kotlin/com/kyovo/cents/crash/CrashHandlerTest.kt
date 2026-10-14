package com.kyovo.cents.crash

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CrashHandlerTest
{
    @TempDir
    lateinit var folder: File

    private val thread = Thread.currentThread()
    private val failure = IllegalStateException("boom")

    private fun store() = CrashReportStore(File(folder, "last-crash.txt"))

    @Test
    fun `writes the report, then lets the system handle the crash as it would have`()
    {
        // GIVEN
        val store = store()
        val handled = mutableListOf<Pair<Thread, Throwable>>()
        val handler = CrashHandler(store, { "report of ${it.message}" }) { t, e -> handled += t to e }

        // WHEN
        handler.uncaughtException(thread, failure)

        // THEN
        assertThat(store.read()).isEqualTo("report of boom")
        assertThat(handled).containsExactly(thread to failure)
    }

    // A crash handler that crashes would hide the real failure: whatever goes wrong while reporting, the previous
    // handler still gets the original one.
    @Test
    fun `still hands the crash on when the report cannot be made`()
    {
        // GIVEN a report that fails to be built
        val handled = mutableListOf<Throwable>()
        val handler = CrashHandler(store(), { error("no report") }) { _, e -> handled += e }

        // WHEN
        handler.uncaughtException(thread, failure)

        // THEN
        assertThat(handled).containsExactly(failure)
    }

    @Test
    fun `still hands the crash on when the report cannot be written`()
    {
        // GIVEN a store whose file is in a folder that does not exist
        val store = CrashReportStore(File(folder, "missing/last-crash.txt"))
        val handled = mutableListOf<Throwable>()
        val handler = CrashHandler(store, { "report" }) { _, e -> handled += e }

        // WHEN
        handler.uncaughtException(thread, failure)

        // THEN
        assertThat(handled).containsExactly(failure)
        assertThat(store.read()).isNull()
    }

    @Test
    fun `does not fail when there is no handler to hand the crash on to`()
    {
        val store = store()

        CrashHandler(store, { "report" }, null).uncaughtException(thread, failure)

        assertThat(store.read()).isEqualTo("report")
    }
}
