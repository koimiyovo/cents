package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedAutomaticBackupSettings
import com.kyovo.cents.application.fakes.FixedExportData
import com.kyovo.cents.application.fakes.InMemoryBackupFolder
import com.kyovo.cents.application.fakes.aClock
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.model.AutomaticBackupResult
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * The automatic backup writes a fresh export into the folder the user chose, keeps only the newest few
 * copies it made itself, and never touches any other file of that folder. No folder chosen means the user
 * did not ask for it: nothing happens. A failure is let through (the worker will try again later), and
 * an old copy is never deleted for a new one that was not written.
 */
class AutomaticBackupServiceTest
{
    private val folder = "content://tree/backups"
    private val settings = FixedAutomaticBackupSettings(folder)
    private val export = FixedExportData("the file")
    private val files = InMemoryBackupFolder()
    private val clock = aClock(anInstant("2026-10-06T08:00:00Z"))

    private fun service() = AutomaticBackupService(export, settings, files, clock)

    private fun auto(date: String) = "cents-sauvegarde-auto-$date.json"

    @Test
    fun `without a chosen folder nothing is exported or written`() = runTest()
    {
        // GIVEN
        settings.folder = null

        // WHEN
        val result = service().run()

        // THEN
        assertThat(result).isEqualTo(AutomaticBackupResult.NotConfigured)
        assertThat(export.calls).isZero()
    }

    @Test
    fun `writes the export in the chosen folder, named after today`() = runTest()
    {
        // WHEN
        val result = service().run()

        // THEN
        assertThat(result).isEqualTo(AutomaticBackupResult.Done(auto("2026-10-06")))
        assertThat(files.textOf(folder, auto("2026-10-06"))).isEqualTo("the file")
    }

    @Test
    fun `running twice the same day replaces that day's copy instead of adding one`() = runTest()
    {
        // GIVEN
        files.put(folder, auto("2026-10-06"), "earlier today")

        // WHEN
        service().run()

        // THEN
        assertThat(files.names(folder)).containsExactly(auto("2026-10-06"))
        assertThat(files.textOf(folder, auto("2026-10-06"))).isEqualTo("the file")
    }

    @Test
    fun `keeps the five newest copies and deletes the older ones`() = runTest()
    {
        // GIVEN
        listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22", "2026-09-29")
            .forEach { files.put(folder, auto(it)) }

        // WHEN
        service().run()

        // THEN
        assertThat(files.names(folder)).containsExactlyInAnyOrder(
            auto("2026-09-08"), auto("2026-09-15"), auto("2026-09-22"), auto("2026-09-29"), auto("2026-10-06")
        )
        assertThat(files.deleted).containsExactly(auto("2026-09-01"))
    }

    @Test
    fun `copies are ordered by their date, not by when they were added to the folder`() = runTest()
    {
        // GIVEN: stored out of order, with the oldest added last
        listOf("2026-09-22", "2026-09-29", "2026-09-08", "2026-09-15", "2026-09-01")
            .forEach { files.put(folder, auto(it)) }

        // WHEN
        service().run()

        // THEN
        assertThat(files.deleted).containsExactly(auto("2026-09-01"))
    }

    @Test
    fun `files that are not its own copies are never deleted`() = runTest()
    {
        // GIVEN: a manual export, a photo, and six automatic copies
        files.put(folder, "cents-sauvegarde-2026-01-01.json")
        files.put(folder, "holiday.jpg")
        listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22", "2026-09-29", "2026-09-30")
            .forEach { files.put(folder, auto(it)) }

        // WHEN
        service().run()

        // THEN
        assertThat(files.names(folder)).contains("cents-sauvegarde-2026-01-01.json", "holiday.jpg")
        assertThat(files.deleted).containsExactlyInAnyOrder(auto("2026-09-01"), auto("2026-09-08"))
    }

    @Test
    fun `other folders are left alone`() = runTest()
    {
        // GIVEN
        files.put("content://tree/elsewhere", auto("2026-01-01"))
        listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22", "2026-09-29")
            .forEach { files.put(folder, auto(it)) }

        // WHEN
        service().run()

        // THEN
        assertThat(files.names("content://tree/elsewhere")).containsExactly(auto("2026-01-01"))
    }

    @Test
    fun `a failed write deletes nothing, so the old copies are still there`() = runTest()
    {
        // GIVEN
        listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22", "2026-09-29")
            .forEach { files.put(folder, auto(it)) }
        files.failOnWrite = true

        // WHEN / THEN
        assertThatThrownBySuspending { service().run() }.isInstanceOf(IOException::class.java)
        assertThat(files.deleted).isEmpty()
        assertThat(files.names(folder)).hasSize(5)
    }

    @Test
    fun `a failed export writes and deletes nothing`() = runTest()
    {
        // GIVEN
        export.failure = IllegalStateException("the database is gone")

        // WHEN / THEN
        assertThatThrownBySuspending { service().run() }.isInstanceOf(IllegalStateException::class.java)
        assertThat(files.names(folder)).isEmpty()
        assertThat(files.deleted).isEmpty()
    }
}
