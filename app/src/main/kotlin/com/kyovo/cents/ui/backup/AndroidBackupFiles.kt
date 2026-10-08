package com.kyovo.cents.ui.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Reads and writes the place the user picked, through the system's [ContentResolver]: a location is a
 * `content://` Uri, not a path, and the app was given access to that one file only (the Storage Access
 * Framework), so it needs no storage permission. Disk work runs off the main thread.
 */
class AndroidBackupFiles(private val resolver: ContentResolver) : BackupFiles
{
    override suspend fun write(location: String, text: String)
    {
        withContext(Dispatchers.IO) {
            // "wt": write and *truncate*. Plain "w" may leave the end of a longer file that was there
            // before (picking an existing backup to overwrite), which would corrupt the JSON.
            val stream = open { resolver.openOutputStream(Uri.parse(location), "wt") }
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    override suspend fun read(location: String): String
    {
        return withContext(Dispatchers.IO) {
            val stream = open { resolver.openInputStream(Uri.parse(location)) }
            stream.use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    // The provider may refuse (a revoked grant is a SecurityException) or hand back nothing: both are
    // "the file cannot be used", which is what the caller handles.
    private fun <T> open(opening: () -> T?): T
    {
        try
        {
            return opening() ?: throw IOException("The file could not be opened")
        } catch (e: SecurityException)
        {
            throw IOException(e)
        }
    }
}
