package com.kyovo.cents.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.port.output.MainCurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Currency

/**
 * The main currency, as its ISO code in a Preferences DataStore (a single setting, like the automatic
 * backup's folder). Deliberately outside the database: an import replaces the user's data but not how they
 * like to see it. A code the app does not offer reads as "not chosen".
 */
class DataStoreMainCurrency(private val store: DataStore<Preferences>) : MainCurrencyRepository
{
    override fun observe(): Flow<AccountCurrency?>
    {
        return store.data.map { preferences ->
            preferences[CODE]?.let { code -> AccountCurrency.SUPPORTED.firstOrNull { it.value.currencyCode == code } }
        }.distinctUntilChanged()
    }

    override suspend fun save(currency: AccountCurrency)
    {
        store.edit { it[CODE] = currency.value.currencyCode }
    }

    private companion object
    {
        val CODE = stringPreferencesKey("main_currency")
    }
}

val Context.mainCurrencyDataStore: DataStore<Preferences> by preferencesDataStore(name = "main_currency")
