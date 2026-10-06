package com.kyovo.cents.ui.backup

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.InvalidBackupException
import com.kyovo.cents.domain.model.BackupSummary
import com.kyovo.cents.domain.port.input.ExportDataUseCase
import com.kyovo.cents.domain.port.input.ImportDataUseCase
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The state behind the backup screen. It knows nothing of Android's file picker: the screen asks the
 * system for a place (a location, which is a `Uri` as text), and hands it over. The view model then reads
 * or writes the file through [BackupFiles] and runs the export or the import use case.
 *
 * Importing replaces everything the user owns, so it is asked about first ([DataBackupViewModel.askToImport]);
 * only one operation runs at a time; and whatever happens ends as a [BackupResult] the screen shows once.
 */
@ExtendWith(MainDispatcherExtension::class)
class DataBackupViewModelTest
{
    // "Today" is September 26th, 2026.
    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)

    private val exportData = FakeExportData("{\"the\":\"backup\"}")
    private val importData = FakeImportData()
    private val files = FakeBackupFiles()

    private val viewModel = DataBackupViewModel(exportData, importData, files, clock)

    private val state get() = viewModel.uiState.value

    // ---- at the start

    @Test
    fun `starts idle, asking nothing, with no result`()
    {
        assertThat(state).isEqualTo(DataBackupUiState())
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
        assertThat(state.confirmingImport).isFalse()
        assertThat(state.result).isNull()
    }

    // ---- exporting

    @Test
    fun `the file offered for an export is named after today`()
    {
        assertThat(viewModel.exportFileName()).isEqualTo("cents-sauvegarde-2026-09-26.json")
    }

    @Test
    fun `an export writes what the use case gave to the place that was chosen`()
    {
        // WHEN
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(files.stored).containsEntry("content://files/chosen", "{\"the\":\"backup\"}")
        assertThat(files.stored).hasSize(1)
    }

    @Test
    fun `a successful export ends idle with its result`()
    {
        // WHEN
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
        assertThat(state.result).isEqualTo(BackupResult.Exported)
    }

    @Test
    fun `exporting is shown while it runs`()
    {
        // GIVEN a write that has not finished
        val release = files.holdWrites()

        // WHEN
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(state.operation).isEqualTo(BackupOperation.EXPORTING)
        assertThat(state.result).isNull()

        // WHEN it finishes
        release.complete(Unit)

        // THEN
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
        assertThat(state.result).isEqualTo(BackupResult.Exported)
    }

    @Test
    fun `an export that cannot read the data fails and writes nothing`()
    {
        // GIVEN
        exportData.failure = IllegalStateException("the database is gone")

        // WHEN
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(state.result).isEqualTo(BackupResult.ExportFailed)
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
        assertThat(files.stored).isEmpty()
    }

    @Test
    fun `an export that cannot write the file fails`()
    {
        // GIVEN
        files.writeFailure = IOException("no space left")

        // WHEN
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(state.result).isEqualTo(BackupResult.ExportFailed)
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
    }

    @Test
    fun `an export leaves the data alone`()
    {
        // WHEN
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(importData.imported).isEmpty()
    }

    // ---- asking before importing

    @Test
    fun `asking to import raises the confirmation, and nothing is read yet`()
    {
        // WHEN
        viewModel.askToImport()

        // THEN
        assertThat(state.confirmingImport).isTrue()
        assertThat(files.reads).isEmpty()
        assertThat(importData.imported).isEmpty()
    }

    @Test
    fun `dismissing the confirmation lowers it`()
    {
        // GIVEN
        viewModel.askToImport()

        // WHEN
        viewModel.dismissImportConfirmation()

        // THEN
        assertThat(state.confirmingImport).isFalse()
    }

    // ---- importing

    @Test
    fun `an import reads the chosen file and gives its text, as it is, to the use case`()
    {
        // GIVEN a file whose text has spaces and a line break at its ends
        files.stored["content://files/backup"] = "  {\"a\": 1}\n"

        // WHEN
        viewModel.importFrom("content://files/backup")

        // THEN
        assertThat(files.reads).containsExactly("content://files/backup")
        assertThat(importData.imported).containsExactly("  {\"a\": 1}\n")
    }

    @Test
    fun `a successful import ends idle with its result, the confirmation gone`()
    {
        // GIVEN
        files.stored["content://files/backup"] = "the file"
        viewModel.askToImport()

        // WHEN
        viewModel.importFrom("content://files/backup")

        // THEN
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
        assertThat(state.confirmingImport).isFalse()
        assertThat(state.result).isEqualTo(BackupResult.Imported(importData.summary))
    }

    @Test
    fun `the result of an import carries what the use case says was restored`()
    {
        // GIVEN
        files.stored["content://files/backup"] = "the file"
        importData.summary = BackupSummary(
            accounts = 2, subcategories = 21, transactions = 412, budgets = 3, recurringTransactions = 1, projects = 0
        )

        // WHEN
        viewModel.importFrom("content://files/backup")

        // THEN
        val result = state.result as BackupResult.Imported
        assertThat(result.summary.accounts).isEqualTo(2)
        assertThat(result.summary.transactions).isEqualTo(412)
        assertThat(result.summary.isEmpty).isFalse()
    }

    @Test
    fun `an import of a backup with nothing in it is still a success, and says so`()
    {
        // GIVEN
        files.stored["content://files/empty"] = "an empty backup"
        importData.summary = BackupSummary(0, 0, 0, 0, 0, 0)

        // WHEN
        viewModel.importFrom("content://files/empty")

        // THEN
        val result = state.result as BackupResult.Imported
        assertThat(result.summary.isEmpty).isTrue()
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
    }

    @Test
    fun `importing is shown while it runs`()
    {
        // GIVEN a read that has not finished
        files.stored["content://files/backup"] = "the file"
        val release = files.holdReads()

        // WHEN
        viewModel.importFrom("content://files/backup")

        // THEN
        assertThat(state.operation).isEqualTo(BackupOperation.IMPORTING)

        // WHEN it finishes
        release.complete(Unit)

        // THEN
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
        assertThat(state.result).isEqualTo(BackupResult.Imported(importData.summary))
    }

    @Test
    fun `a file that is not a backup is refused with its own reason`()
    {
        // GIVEN
        files.stored["content://files/photo"] = "not a backup"
        importData.failure = InvalidBackupException()

        // WHEN
        viewModel.importFrom("content://files/photo")

        // THEN
        assertThat(state.result).isEqualTo(BackupResult.ImportFailed(ImportFailure.NOT_A_BACKUP))
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
    }

    @Test
    fun `a file that cannot be read is refused, and the use case is never called`()
    {
        // GIVEN
        files.readFailure = IOException("permission denied")

        // WHEN
        viewModel.importFrom("content://files/backup")

        // THEN
        assertThat(state.result).isEqualTo(BackupResult.ImportFailed(ImportFailure.FILE_UNREADABLE))
        assertThat(importData.imported).isEmpty()
    }

    @Test
    fun `a failure of the storage is reported as such, not as a bad file`()
    {
        // GIVEN
        files.stored["content://files/backup"] = "the file"
        importData.failure = IllegalStateException("disk full")

        // WHEN
        viewModel.importFrom("content://files/backup")

        // THEN
        assertThat(state.result).isEqualTo(BackupResult.ImportFailed(ImportFailure.STORAGE_FAILED))
        assertThat(state.operation).isEqualTo(BackupOperation.IDLE)
    }

    // ---- one thing at a time, and the result

    @Test
    fun `a request made while an operation runs is ignored`()
    {
        // GIVEN an export that has not finished
        val release = files.holdWrites()
        viewModel.exportTo("content://files/first")

        // WHEN
        viewModel.exportTo("content://files/second")
        viewModel.importFrom("content://files/other")

        // THEN
        assertThat(state.operation).isEqualTo(BackupOperation.EXPORTING)
        assertThat(files.reads).isEmpty()
        assertThat(importData.imported).isEmpty()

        // WHEN it finishes
        release.complete(Unit)

        // THEN only the first was written
        assertThat(files.stored.keys).containsExactly("content://files/first")
    }

    @Test
    fun `the result stays until it is dismissed`()
    {
        // GIVEN
        viewModel.exportTo("content://files/chosen")

        // WHEN
        viewModel.dismissResult()

        // THEN
        assertThat(state.result).isNull()
    }

    @Test
    fun `starting something new clears the previous result`()
    {
        // GIVEN a failed import still shown
        files.readFailure = IOException("permission denied")
        viewModel.importFrom("content://files/backup")
        assertThat(state.result).isNotNull

        // WHEN
        val release = files.holdWrites()
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(state.result).isNull()
        release.complete(Unit)
    }

    @Test
    fun `after a failure, the next attempt can succeed`()
    {
        // GIVEN
        files.writeFailure = IOException("no space left")
        viewModel.exportTo("content://files/chosen")
        assertThat(state.result).isEqualTo(BackupResult.ExportFailed)

        // WHEN
        files.writeFailure = null
        viewModel.exportTo("content://files/chosen")

        // THEN
        assertThat(state.result).isEqualTo(BackupResult.Exported)
        assertThat(files.stored).containsKey("content://files/chosen")
    }
}

private class FakeExportData(private val text: String) : ExportDataUseCase
{
    var failure: Throwable? = null

    override suspend fun export(): String
    {
        failure?.let { throw it }
        return text
    }
}

private class FakeImportData : ImportDataUseCase
{
    /** The texts given to the use case, in order. */
    val imported = mutableListOf<String>()
    var failure: Throwable? = null

    /** What the use case answers once it has imported. */
    var summary = BackupSummary(
        accounts = 1, subcategories = 21, transactions = 7, budgets = 0, recurringTransactions = 0, projects = 1
    )

    override suspend fun import(text: String): BackupSummary
    {
        failure?.let { throw it }
        imported += text
        return summary
    }
}

private class FakeBackupFiles : BackupFiles
{
    /** What each location holds. */
    val stored = mutableMapOf<String, String>()

    /** The locations read, in order. */
    val reads = mutableListOf<String>()

    var writeFailure: Throwable? = null
    var readFailure: Throwable? = null

    private var writeGate: CompletableDeferred<Unit>? = null
    private var readGate: CompletableDeferred<Unit>? = null

    /** The next writes wait until the returned deferred is completed. */
    fun holdWrites() = CompletableDeferred<Unit>().also { writeGate = it }

    /** The next reads wait until the returned deferred is completed. */
    fun holdReads() = CompletableDeferred<Unit>().also { readGate = it }

    override suspend fun write(location: String, text: String)
    {
        writeGate?.await()
        writeFailure?.let { throw it }
        stored[location] = text
    }

    override suspend fun read(location: String): String
    {
        reads += location
        readGate?.await()
        readFailure?.let { throw it }
        return stored.getValue(location)
    }
}
