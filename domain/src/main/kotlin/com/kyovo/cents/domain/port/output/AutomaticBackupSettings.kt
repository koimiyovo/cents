package com.kyovo.cents.domain.port.output

/** What the user chose for the automatic backup. */
interface AutomaticBackupSettings
{
    /** The folder the copies go to, or null while the user has not picked one (the backup is then off). */
    suspend fun folder(): String?
}
