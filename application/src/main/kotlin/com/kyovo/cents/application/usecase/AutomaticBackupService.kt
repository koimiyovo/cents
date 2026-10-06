package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AutomaticBackupResult
import com.kyovo.cents.domain.port.input.ExportDataUseCase
import com.kyovo.cents.domain.port.input.RunAutomaticBackupUseCase
import com.kyovo.cents.domain.port.output.AutomaticBackupSettings
import com.kyovo.cents.domain.port.output.BackupFolder
import java.time.Clock
import java.time.LocalDate

class AutomaticBackupService(
    private val exportData: ExportDataUseCase,
    private val settings: AutomaticBackupSettings,
    private val backupFolder: BackupFolder,
    private val clock: Clock
) : RunAutomaticBackupUseCase
{
    override suspend fun run(): AutomaticBackupResult
    {
        val folder = settings.folder() ?: return AutomaticBackupResult.NotConfigured

        val name = "$PREFIX${LocalDate.now(clock)}$SUFFIX"
        // Written before anything is deleted: a failure leaves the older copies in place.
        backupFolder.write(folder, name, exportData.export())

        // The date in the name is ISO, so the names sort in calendar order: the newest come first.
        backupFolder.names(folder, PREFIX)
            .sortedDescending()
            .drop(KEPT_COPIES)
            .forEach { backupFolder.delete(folder, it) }

        return AutomaticBackupResult.Done(name)
    }

    private companion object
    {
        const val PREFIX = "cents-sauvegarde-auto-"
        const val SUFFIX = ".json"
        const val KEPT_COPIES = 5
    }
}
