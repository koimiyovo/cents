package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.BackupSnapshot

interface BackupRestorer
{
    suspend fun replaceAll(snapshot: BackupSnapshot)
}