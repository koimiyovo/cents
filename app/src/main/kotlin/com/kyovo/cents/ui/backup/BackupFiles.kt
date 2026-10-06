package com.kyovo.cents.ui.backup

/**
 * Where the backup text goes to and comes from. A location is whatever the system's file picker handed
 * back (a `content://` Uri, as text): the user chose it, so no storage permission is involved. Failures
 * are [java.io.IOException]s.
 */
interface BackupFiles
{
    suspend fun write(location: String, text: String)
    suspend fun read(location: String): String
}
