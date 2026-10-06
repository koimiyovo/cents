package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.BackupSummary
import com.kyovo.cents.domain.port.input.ImportDataUseCase
import com.kyovo.cents.domain.port.output.BackupRestorer
import com.kyovo.cents.domain.port.output.BackupSerializer

class ImportDataService(
    private val backupSerializer: BackupSerializer,
    private val backupRestorer: BackupRestorer
) : ImportDataUseCase
{
    override suspend fun import(text: String): BackupSummary
    {
        val backupSnapshot = backupSerializer.deserialize(text)
        backupSnapshot.requireConsistent()
        backupRestorer.replaceAll(backupSnapshot)
        return backupSnapshot.summary()
    }
}
