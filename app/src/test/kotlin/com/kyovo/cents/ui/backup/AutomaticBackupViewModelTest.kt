package com.kyovo.cents.ui.backup

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.model.AutomaticBackupResult
import com.kyovo.cents.domain.port.input.RunAutomaticBackupUseCase
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException

/**
 * The "automatic backup" card: the chosen folder (null = off), a backup started by hand, and how it ended.
 * Choosing a folder backs up at once; one run at a time.
 */
@ExtendWith(MainDispatcherExtension::class)
class AutomaticBackupViewModelTest
{
    private class FakeRunBackup : RunAutomaticBackupUseCase
    {
        var runs = 0
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun run(): AutomaticBackupResult
        {
            runs++
            gate?.await()
            failure?.let { throw it }
            return AutomaticBackupResult.Done("cents-sauvegarde-auto-2026-10-06.json")
        }
    }

    private val settings = DataStoreAutomaticBackupSettings(InMemoryPreferences())
    private val runBackup = FakeRunBackup()

    // Built here, not in a field: it observes from its construction, which must come after the main
    // dispatcher is installed by the extension.
    private lateinit var viewModel: AutomaticBackupViewModel

    private val state get() = viewModel.uiState.value

    @BeforeEach
    fun setUp()
    {
        viewModel = AutomaticBackupViewModel(settings, runBackup)
    }

    @Test
    fun `starts off, idle, with no outcome`()
    {
        assertThat(state).isEqualTo(AutomaticBackupUiState())
    }

    @Test
    fun `choosing a folder remembers it and backs up at once`()
    {
        viewModel.chooseFolder("content://tree/backups")

        assertThat(state.folder).isEqualTo("content://tree/backups")
        assertThat(runBackup.runs).isEqualTo(1)
        assertThat(state.outcome).isEqualTo(AutomaticBackupOutcome.BACKED_UP)
        assertThat(state.running).isFalse()
    }

    @Test
    fun `a failed backup is said, and the folder stays chosen`()
    {
        runBackup.failure = IOException("the folder is full")

        viewModel.chooseFolder("content://tree/backups")

        assertThat(state.outcome).isEqualTo(AutomaticBackupOutcome.FAILED)
        assertThat(state.folder).isEqualTo("content://tree/backups")
        assertThat(state.running).isFalse()
    }

    @Test
    fun `backing up now runs the use case again`()
    {
        viewModel.chooseFolder("content://tree/backups")

        viewModel.backUpNow()

        assertThat(runBackup.runs).isEqualTo(2)
    }

    @Test
    fun `a second request while one is running does nothing`()
    {
        runBackup.gate = CompletableDeferred()
        viewModel.chooseFolder("content://tree/backups")
        assertThat(state.running).isTrue()

        viewModel.backUpNow()
        runBackup.gate!!.complete(Unit)

        assertThat(runBackup.runs).isEqualTo(1)
        assertThat(state.running).isFalse()
    }

    @Test
    fun `turning it off forgets the folder and the outcome`()
    {
        viewModel.chooseFolder("content://tree/backups")

        viewModel.disable()

        assertThat(state.folder).isNull()
        assertThat(state.outcome).isNull()
    }

    @Test
    fun `dismissing the outcome clears it`()
    {
        viewModel.chooseFolder("content://tree/backups")

        viewModel.dismissOutcome()

        assertThat(state.outcome).isNull()
    }

    // ---- the name shown for a folder

    @Test
    fun `a folder is shown by its path without the storage prefix`()
    {
        val folder = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FCents"

        assertThat(folderDisplayName(folder)).isEqualTo("Documents/Cents")
    }

    @Test
    fun `a folder that does not look like a tree Uri is shown as it is`()
    {
        assertThat(folderDisplayName("Backups")).isEqualTo("Backups")
    }
}
