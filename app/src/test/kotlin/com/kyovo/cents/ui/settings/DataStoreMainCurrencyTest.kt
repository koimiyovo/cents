package com.kyovo.cents.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kyovo.cents.domain.model.AccountCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The main currency is a single code in a Preferences DataStore, outside the database. */
class DataStoreMainCurrencyTest
{
    @TempDir
    lateinit var folder: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterEach
    fun stop()
    {
        scope.cancel()
    }

    // In memory: replacing a DataStore file renames over the old one, which Windows refuses; on a phone it is fine.
    // What is tested here is what this class does with the preferences, not DataStore's file handling.
    private class InMemoryPreferences : DataStore<Preferences>
    {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences
        {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private fun newStore(): DataStore<Preferences> = InMemoryPreferences()

    @Test
    fun `nothing is chosen on a new install`() = runBlocking<Unit>()
    {
        assertThat(DataStoreMainCurrency(newStore()).observe().first()).isNull()
    }

    @Test
    fun `a saved currency is observed`() = runBlocking<Unit>()
    {
        val repository = DataStoreMainCurrency(newStore())

        repository.save(AccountCurrency.XOF)

        assertThat(repository.observe().first()).isEqualTo(AccountCurrency.XOF)
    }

    @Test
    fun `every currency the app offers can be saved and read back`() = runBlocking<Unit>()
    {
        AccountCurrency.SUPPORTED.forEach { currency ->
            val repository = DataStoreMainCurrency(newStore())

            repository.save(currency)

            assertThat(repository.observe().first()).isEqualTo(currency)
        }
    }

    @Test
    fun `saving another one replaces it`() = runBlocking<Unit>()
    {
        val repository = DataStoreMainCurrency(newStore())
        repository.save(AccountCurrency.USD)

        repository.save(AccountCurrency.EUR)

        assertThat(repository.observe().first()).isEqualTo(AccountCurrency.EUR)
    }

    @Test
    fun `the choice survives reopening the file`() = runBlocking<Unit>()
    {
        val first = PreferenceDataStoreFactory.create(scope = scope) { File(folder, "kept.preferences_pb") }
        DataStoreMainCurrency(first).save(AccountCurrency.USD)
        scope.cancel()

        val reopenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = PreferenceDataStoreFactory.create(scope = reopenScope) { File(folder, "kept.preferences_pb") }

        assertThat(DataStoreMainCurrency(second).observe().first()).isEqualTo(AccountCurrency.USD)
        reopenScope.cancel()
    }

    @Test
    fun `a code the app does not offer reads as not chosen`() = runBlocking<Unit>()
    {
        val store = newStore()
        store.edit { it[stringPreferencesKey("main_currency")] = "JPY" }

        assertThat(DataStoreMainCurrency(store).observe().first()).isNull()
    }
}
