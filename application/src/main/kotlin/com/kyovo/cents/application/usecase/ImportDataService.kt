package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.BackupSummary
import com.kyovo.cents.domain.port.input.ImportDataUseCase
import com.kyovo.cents.domain.port.output.BackupRestorer
import com.kyovo.cents.domain.port.output.BackupSerializer
import com.kyovo.cents.domain.port.output.MainCurrencyRepository
import kotlinx.coroutines.flow.first

class ImportDataService(
    private val backupSerializer: BackupSerializer,
    private val backupRestorer: BackupRestorer,
    // An old file's projects take it (see [BackupSerializer.deserialize]); the euro until one is chosen.
    private val mainCurrencyRepository: MainCurrencyRepository
) : ImportDataUseCase
{
    override suspend fun import(text: String): BackupSummary
    {
        val mainCurrency = mainCurrencyRepository.observe().first() ?: AccountCurrency.EUR
        val backupSnapshot = backupSerializer.deserialize(text, mainCurrency)
        backupSnapshot.requireConsistent()
        backupRestorer.replaceAll(backupSnapshot)
        return backupSnapshot.summary()
    }
}
