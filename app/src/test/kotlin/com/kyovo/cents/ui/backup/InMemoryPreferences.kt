package com.kyovo.cents.ui.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A preferences store kept in memory. Not DataStore's file handling, which on the Windows machine running
 * these tests cannot replace its file twice (a rename over an open file), though it does on a phone.
 */
class InMemoryPreferences : DataStore<Preferences>
{
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences
    {
        return transform(state.value).also { state.value = it }
    }
}
