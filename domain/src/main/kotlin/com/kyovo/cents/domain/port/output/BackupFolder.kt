package com.kyovo.cents.domain.port.output

/**
 * The folder the automatic backups are written to. A folder is whatever the system's picker handed back,
 * as text. Failures are [java.io.IOException]s.
 */
interface BackupFolder
{
    /** Writes [text] as the file [name], replacing a file of that name if there is one. */
    suspend fun write(folder: String, name: String, text: String)

    /** The names of the files of [folder] that start with [startingWith]. */
    suspend fun names(folder: String, startingWith: String): List<String>

    suspend fun delete(folder: String, name: String)
}
