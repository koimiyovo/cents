package com.kyovo.cents.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * Whether the onboarding was already shown, so it comes once and not at every launch. A single flag, kept
 * in a Preferences DataStore like the automatic backup's folder (a Room table would be too much for it).
 * Deliberately outside the database: an import replaces the user's data, and must not bring the onboarding back.
 */
class OnboardingSeen(private val store: DataStore<Preferences>)
{
    suspend fun isSeen(): Boolean
    {
        return store.data.first()[SEEN] == true
    }

    suspend fun markSeen()
    {
        store.edit { it[SEEN] = true }
    }

    private companion object
    {
        val SEEN = booleanPreferencesKey("onboarding_seen")
    }
}

val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")
