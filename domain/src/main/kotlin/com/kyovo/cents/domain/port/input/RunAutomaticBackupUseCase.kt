package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AutomaticBackupResult

interface RunAutomaticBackupUseCase
{
    suspend fun run(): AutomaticBackupResult
}
