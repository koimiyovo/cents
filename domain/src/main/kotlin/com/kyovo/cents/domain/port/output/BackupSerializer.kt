package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.BackupSnapshot

interface BackupSerializer
{
    fun serialize(snapshot: BackupSnapshot): String

    /**
     * Reads a backup file. [mainCurrency] is what the projects of a file written before they had a currency take:
     * the one their targets were read in.
     */
    fun deserialize(text: String, mainCurrency: AccountCurrency): BackupSnapshot
}
