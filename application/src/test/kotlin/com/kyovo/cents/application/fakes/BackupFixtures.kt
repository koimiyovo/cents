package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.port.output.BackupRestorer
import com.kyovo.cents.domain.port.output.BackupSerializer

/**
 * Stands for the JSON adapter: remembers the snapshots it was asked to write (for the tests to look at)
 * and answers with a fixed text. To read, it hands back what [onRead] gives (by default it refuses to be
 * used: the export does not read), which may throw like the real one does for a file it cannot read.
 */
class RecordingBackupSerializer(private val text: String = "the backup text") : BackupSerializer
{
    /** The snapshots written, in order. */
    val written = mutableListOf<BackupSnapshot>()

    /** The texts asked to be read, in order. */
    val read = mutableListOf<String>()

    var onRead: (String) -> BackupSnapshot = { throw UnsupportedOperationException("not needed by this test") }

    override fun serialize(snapshot: BackupSnapshot): String
    {
        written += snapshot
        return text
    }

    override fun deserialize(text: String): BackupSnapshot
    {
        read += text
        return onRead(text)
    }
}

/** Stands for the database: remembers the snapshots it was asked to put in place of everything. */
class RecordingBackupRestorer : BackupRestorer
{
    /** The snapshots restored, in order. */
    val restored = mutableListOf<BackupSnapshot>()

    /** What to throw instead of restoring, to play a storage that fails. */
    var failure: Throwable? = null

    override suspend fun replaceAll(snapshot: BackupSnapshot)
    {
        failure?.let { throw it }
        restored += snapshot
    }
}
