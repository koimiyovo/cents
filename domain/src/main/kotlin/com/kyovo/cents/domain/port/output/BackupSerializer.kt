package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.BackupSnapshot

interface BackupSerializer
{
    fun serialize(snapshot: BackupSnapshot): String
    fun deserialize(text: String): BackupSnapshot
}
