package com.kyovo.cents.ui.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kyovo.cents.domain.port.output.AutomaticBackupSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The folder the user chose for the automatic backup, kept in a Preferences DataStore: a single small
 * setting, which a Room table (and a schema version) would be too much for. DataStore is what replaced
 * SharedPreferences: reads are a [Flow], writes are `suspend` and transactional, and nothing blocks the
 * main thread (SharedPreferences could, and its `apply()` could lose a write when the process died).
 */
class DataStoreAutomaticBackupSettings(private val store: DataStore<Preferences>) : AutomaticBackupSettings
{
    override suspend fun folder(): String?
    {
        return store.data.first()[FOLDER]
    }

    fun observeFolder(): Flow<String?>
    {
        return store.data.map { it[FOLDER] }.distinctUntilChanged()
    }

    suspend fun chooseFolder(folder: String)
    {
        store.edit { it[FOLDER] = folder }
    }

    /** Turns the automatic backup off: no folder, nothing is written. */
    suspend fun clearFolder()
    {
        store.edit { it.remove(FOLDER) }
    }

    private companion object
    {
        val FOLDER = stringPreferencesKey("automatic_backup_folder")
    }
}

/** One DataStore per file and per process: the delegate guarantees it, so it is declared once, here. */
val Context.automaticBackupDataStore: DataStore<Preferences> by preferencesDataStore(name = "automatic_backup")
