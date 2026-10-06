package com.kyovo.cents.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.InvalidBackupException
import com.kyovo.cents.domain.port.input.ExportDataUseCase
import com.kyovo.cents.domain.port.input.ImportDataUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

enum class BackupOperation { IDLE, EXPORTING, IMPORTING }

enum class ImportFailure { NOT_A_BACKUP, FILE_UNREADABLE, STORAGE_FAILED }

/** How an operation ended: shown once, then dismissed. */
sealed interface BackupResult
{
    data object Exported : BackupResult
    data object Imported : BackupResult
    data object ExportFailed : BackupResult
    data class ImportFailed(val reason: ImportFailure) : BackupResult
}

data class DataBackupUiState(
    val operation: BackupOperation = BackupOperation.IDLE,
    val confirmingImport: Boolean = false,
    val result: BackupResult? = null,
)

/**
 * The state behind the backup screen. It knows nothing of Android's file picker: the screen asks the system
 * for a place and hands it over as text. Importing replaces everything, so it is asked about first, and only
 * one operation runs at a time.
 */
class DataBackupViewModel(
    private val exportData: ExportDataUseCase,
    private val importData: ImportDataUseCase,
    private val files: BackupFiles,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel()
{
    private val state = MutableStateFlow(DataBackupUiState())
    val uiState: StateFlow<DataBackupUiState> = state.asStateFlow()

    fun exportFileName(): String
    {
        return "cents-sauvegarde-${LocalDate.now(clock)}.json"
    }

    fun exportTo(location: String)
    {
        if (!start(BackupOperation.EXPORTING)) return
        viewModelScope.launch {
            val result = try
            {
                files.write(location, exportData.export())
                BackupResult.Exported
            } catch (e: CancellationException)
            {
                throw e
            } catch (_: Exception)
            {
                BackupResult.ExportFailed
            }
            finish(result)
        }
    }

    fun askToImport()
    {
        state.update { it.copy(confirmingImport = true) }
    }

    fun dismissImportConfirmation()
    {
        state.update { it.copy(confirmingImport = false) }
    }

    fun importFrom(location: String)
    {
        if (!start(BackupOperation.IMPORTING)) return
        viewModelScope.launch {
            finish(readAndImport(location))
        }
    }

    fun dismissResult()
    {
        state.update { it.copy(result = null) }
    }

    private suspend fun readAndImport(location: String): BackupResult
    {
        val text = try
        {
            files.read(location)
        } catch (e: CancellationException)
        {
            throw e
        } catch (_: IOException)
        {
            return BackupResult.ImportFailed(ImportFailure.FILE_UNREADABLE)
        }

        return try
        {
            importData.import(text)
            BackupResult.Imported
        } catch (e: CancellationException)
        {
            throw e
        } catch (_: InvalidBackupException)
        {
            BackupResult.ImportFailed(ImportFailure.NOT_A_BACKUP)
        } catch (_: Exception)
        {
            BackupResult.ImportFailed(ImportFailure.STORAGE_FAILED)
        }
    }

    /** Takes the one operation slot, or says it is taken. A new operation drops the previous result. */
    private fun start(operation: BackupOperation): Boolean
    {
        var started = false
        state.update {
            started = it.operation == BackupOperation.IDLE
            if (started) it.copy(operation = operation, confirmingImport = false, result = null) else it
        }
        return started
    }

    private fun finish(result: BackupResult)
    {
        state.update { it.copy(operation = BackupOperation.IDLE, result = result) }
    }
}
