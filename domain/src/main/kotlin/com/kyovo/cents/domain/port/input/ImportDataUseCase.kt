package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.BackupSummary

interface ImportDataUseCase
{
    /** Replaces everything with the backup [text]; answers with what the backup held. */
    suspend fun import(text: String): BackupSummary
}
