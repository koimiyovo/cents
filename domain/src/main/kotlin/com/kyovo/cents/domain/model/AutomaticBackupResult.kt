package com.kyovo.cents.domain.model

/** How an automatic backup run ended. A failure is not a result: it is thrown. */
sealed interface AutomaticBackupResult
{
    /** The user has not chosen a folder, so nothing was done. */
    data object NotConfigured : AutomaticBackupResult

    /** [fileName] is the copy that now sits in the folder. */
    data class Done(val fileName: String) : AutomaticBackupResult
}
