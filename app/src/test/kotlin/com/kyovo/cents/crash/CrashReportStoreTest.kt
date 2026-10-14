package com.kyovo.cents.crash

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CrashReportStoreTest
{
    @TempDir
    lateinit var folder: File

    private fun store() = CrashReportStore(File(folder, "last-crash.txt"))

    @Test
    fun `has nothing to read before any crash`()
    {
        assertThat(store().read()).isNull()
    }

    @Test
    fun `gives back what was saved, accents included`()
    {
        // WHEN
        store().save("Centimes 1.0 (4)\nÉchec")

        // THEN even a new store on the same file reads it, as after a restart of the app
        assertThat(store().read()).isEqualTo("Centimes 1.0 (4)\nÉchec")
    }

    @Test
    fun `keeps only the last crash`()
    {
        // GIVEN
        val store = store()
        store.save("first")

        // WHEN
        store.save("second")

        // THEN
        assertThat(store.read()).isEqualTo("second")
    }

    @Test
    fun `forgets the crash once it is cleared`()
    {
        // GIVEN
        val store = store()
        store.save("report")

        // WHEN
        store.clear()

        // THEN
        assertThat(store.read()).isNull()
    }

    @Test
    fun `clearing when there is nothing is not an error`()
    {
        store().clear()

        assertThat(store().read()).isNull()
    }
}
