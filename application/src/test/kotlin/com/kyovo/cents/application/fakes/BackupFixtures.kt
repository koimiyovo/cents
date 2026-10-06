package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.port.output.BackupSerializer

/**
 * Stands for the JSON adapter: remembers the snapshots it was asked to write (for the tests to look at)
 * and answers with a fixed text. It never reads: the export does not need it to.
 */
class RecordingBackupSerializer(private val text: String = "the backup text") : BackupSerializer
{
    /** The snapshots written, in order. */
    val written = mutableListOf<BackupSnapshot>()

    override fun serialize(snapshot: BackupSnapshot): String
    {
        written += snapshot
        return text
    }

    override fun deserialize(text: String): BackupSnapshot
    {
        throw UnsupportedOperationException("not needed by this fake")
    }
}
