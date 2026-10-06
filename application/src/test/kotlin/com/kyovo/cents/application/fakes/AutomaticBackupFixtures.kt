package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.port.input.ExportDataUseCase
import com.kyovo.cents.domain.port.output.AutomaticBackupSettings
import com.kyovo.cents.domain.port.output.BackupFolder
import java.io.IOException

/** Stands for the user's choice of a folder: [folder] is null until one is picked. */
class FixedAutomaticBackupSettings(var folder: String? = null) : AutomaticBackupSettings
{
    override suspend fun folder(): String? = folder
}

/** Stands for a manual export: answers with a fixed text, or fails like a database that is gone. */
class FixedExportData(private val text: String = "the backup text") : ExportDataUseCase
{
    var calls = 0
        private set

    var failure: Throwable? = null

    override suspend fun export(): String
    {
        calls++
        failure?.let { throw it }
        return text
    }
}

/**
 * Stands for the chosen folder: a map from folder to its files (name to text). [failOnWrite] plays a full
 * or revoked folder, which is an [IOException] like the real one.
 */
class InMemoryBackupFolder : BackupFolder
{
    private val folders = mutableMapOf<String, MutableMap<String, String>>()

    var failOnWrite = false

    /** The names deleted, in order. */
    val deleted = mutableListOf<String>()

    fun put(folder: String, name: String, text: String = "old") { folders.getOrPut(folder) { mutableMapOf() }[name] = text }

    fun names(folder: String): Set<String> = folders[folder]?.keys.orEmpty()

    fun textOf(folder: String, name: String): String? = folders[folder]?.get(name)

    override suspend fun write(folder: String, name: String, text: String)
    {
        if (failOnWrite) throw IOException("the folder is full")
        put(folder, name, text)
    }

    override suspend fun names(folder: String, startingWith: String): List<String>
    {
        return names(folder).filter { it.startsWith(startingWith) }
    }

    override suspend fun delete(folder: String, name: String)
    {
        deleted += name
        folders[folder]?.remove(name)
    }
}
