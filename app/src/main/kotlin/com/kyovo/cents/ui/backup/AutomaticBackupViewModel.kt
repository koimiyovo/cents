package com.kyovo.cents.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.port.input.RunAutomaticBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder

/** How the last run started from the screen ended: shown until dismissed. */
enum class AutomaticBackupOutcome { BACKED_UP, FAILED }

/** [folder] is the chosen folder as the picker gave it (null while the automatic backup is off). */
data class AutomaticBackupUiState(
    val folder: String? = null,
    val running: Boolean = false,
    val outcome: AutomaticBackupOutcome? = null,
)

/**
 * The state behind the "automatic backup" card: which folder is chosen, and a backup right now. The weekly
 * run is the worker's; this is what the user starts by hand. Choosing a folder backs up at once, so the
 * user sees it work (and a refused folder is known now, not a week later) instead of waiting for the
 * first weekly run.
 */
class AutomaticBackupViewModel(
    private val settings: DataStoreAutomaticBackupSettings,
    private val runBackup: RunAutomaticBackupUseCase,
) : ViewModel()
{
    private val running = MutableStateFlow(false)
    private val outcome = MutableStateFlow<AutomaticBackupOutcome?>(null)

    val uiState: StateFlow<AutomaticBackupUiState> =
        combine(settings.observeFolder(), running, outcome, ::AutomaticBackupUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, AutomaticBackupUiState())

    fun chooseFolder(folder: String)
    {
        viewModelScope.launch {
            settings.chooseFolder(folder)
            backUp()
        }
    }

    fun backUpNow()
    {
        viewModelScope.launch { backUp() }
    }

    fun disable()
    {
        outcome.value = null
        viewModelScope.launch { settings.clearFolder() }
    }

    fun dismissOutcome()
    {
        outcome.value = null
    }

    private suspend fun backUp()
    {
        // One run at a time: a second tap while the first is running does nothing.
        if (!running.compareAndSet(false, true)) return
        outcome.value = null
        try
        {
            runBackup.run()
            outcome.value = AutomaticBackupOutcome.BACKED_UP
        } catch (e: CancellationException)
        {
            throw e
        } catch (_: Exception)
        {
            outcome.value = AutomaticBackupOutcome.FAILED
        } finally
        {
            running.update { false }
        }
    }
}

/**
 * A folder's name for the screen, from the tree Uri the picker returned, such as
 * `content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FCents`: the decoded last
 * segment without its storage prefix ("Documents/Cents"). Whatever does not look like that is shown as is.
 */
fun folderDisplayName(folder: String): String
{
    val segment = folder.substringAfterLast('/')
    val decoded = try
    {
        URLDecoder.decode(segment, "UTF-8")
    } catch (_: IllegalArgumentException)
    {
        return folder
    }
    return decoded.substringAfter(':').ifBlank { decoded }
}
