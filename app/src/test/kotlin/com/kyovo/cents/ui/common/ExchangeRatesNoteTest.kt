package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.ExchangeRates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * What the totals say about the exchange rates they used: nothing when every account is in the main currency,
 * the day of the rates otherwise, a warning when the rates are old, and a more serious one when some accounts
 * could not be converted at all (so the total is incomplete).
 */
class ExchangeRatesNoteTest
{
    private val today = LocalDate.of(2026, 10, 14)

    private fun rates(day: LocalDate) = ExchangeRates(day, mapOf(AccountCurrency.USD to BigDecimal("1.1")))

    private fun anAccountIn(currency: AccountCurrency) = Account(
        id = AccountId(UUID.randomUUID()),
        name = AccountName("Compte ${UUID.randomUUID()}"),
        type = AccountType.CHECKING,
        currency = currency,
        createdAt = Instant.parse("2026-09-01T10:00:00Z"),
        archivedAt = null
    )

    // --- The note ---------------------------------------------------------------------------------------------

    @Test
    fun `says nothing when no account is in another currency`()
    {
        assertThat(ratesNote(foreignAccounts = 0, unconvertedAccounts = 0, rates = rates(today), today = today))
            .isEqualTo(RatesNote.None)
    }

    @Test
    fun `says nothing without foreign accounts even when no rates were fetched`()
    {
        assertThat(ratesNote(foreignAccounts = 0, unconvertedAccounts = 0, rates = null, today = today))
            .isEqualTo(RatesNote.None)
    }

    @Test
    fun `gives the day of the rates the conversion used`()
    {
        val day = LocalDate.of(2026, 10, 13)

        assertThat(ratesNote(foreignAccounts = 1, unconvertedAccounts = 0, rates = rates(day), today = today))
            .isEqualTo(RatesNote.RatesOf(day))
    }

    @Test
    fun `warns that the total is incomplete when accounts could not be converted`()
    {
        assertThat(ratesNote(foreignAccounts = 2, unconvertedAccounts = 2, rates = null, today = today))
            .isEqualTo(RatesNote.Incomplete(2))
    }

    @Test
    fun `an incomplete total is reported even when the rates are old`()
    {
        val old = LocalDate.of(2026, 9, 1)

        assertThat(ratesNote(foreignAccounts = 2, unconvertedAccounts = 1, rates = rates(old), today = today))
            .isEqualTo(RatesNote.Incomplete(1))
    }

    @Test
    fun `warns that the rates are old after five days`()
    {
        val old = today.minusDays(6)

        assertThat(ratesNote(foreignAccounts = 1, unconvertedAccounts = 0, rates = rates(old), today = today))
            .isEqualTo(RatesNote.Stale(old))
    }

    // Rates are published on working days: Friday's are still the latest on Tuesday, and a holiday adds a day.
    @Test
    fun `five days old is not old yet`()
    {
        val day = today.minusDays(5)

        assertThat(ratesNote(foreignAccounts = 1, unconvertedAccounts = 0, rates = rates(day), today = today))
            .isEqualTo(RatesNote.RatesOf(day))
    }

    @Test
    fun `rates dated tomorrow, by a server in another time zone, are not old`()
    {
        val day = today.plusDays(1)

        assertThat(ratesNote(foreignAccounts = 1, unconvertedAccounts = 0, rates = rates(day), today = today))
            .isEqualTo(RatesNote.RatesOf(day))
    }

    @Test
    fun `with foreign accounts and no rates at all, they are all unconverted`()
    {
        assertThat(ratesNote(foreignAccounts = 1, unconvertedAccounts = 1, rates = null, today = today))
            .isEqualTo(RatesNote.Incomplete(1))
    }

    // --- Counting the accounts ----------------------------------------------------------------------------------

    @Test
    fun `counts the accounts in another currency than the main one`()
    {
        val accounts = listOf(
            anAccountIn(AccountCurrency.EUR), anAccountIn(AccountCurrency.USD), anAccountIn(AccountCurrency.XOF)
        )

        assertThat(foreignAccountCount(accounts, AccountCurrency.EUR)).isEqualTo(2)
        assertThat(foreignAccountCount(accounts, AccountCurrency.USD)).isEqualTo(2)
    }

    @Test
    fun `counts the accounts the rates cannot convert`()
    {
        // GIVEN rates that cover the dollar but not the Canadian dollar
        val accounts = listOf(
            anAccountIn(AccountCurrency.EUR),
            anAccountIn(AccountCurrency.USD),
            anAccountIn(AccountCurrency.CAD),
            anAccountIn(AccountCurrency.XOF)
        )

        // THEN only the Canadian dollar is out of reach (the franc is tied to the euro)
        assertThat(unconvertedAccountCount(accounts, AccountCurrency.EUR, rates(today))).isEqualTo(1)
    }

    @Test
    fun `counts every foreign account as unconverted while there are no rates`()
    {
        val accounts = listOf(anAccountIn(AccountCurrency.EUR), anAccountIn(AccountCurrency.USD), anAccountIn(AccountCurrency.CAD))

        assertThat(unconvertedAccountCount(accounts, AccountCurrency.EUR, rates = null)).isEqualTo(2)
    }

    @Test
    fun `counts none when every account is in the main currency`()
    {
        val accounts = listOf(anAccountIn(AccountCurrency.EUR), anAccountIn(AccountCurrency.EUR))

        assertThat(unconvertedAccountCount(accounts, AccountCurrency.EUR, rates = null)).isEqualTo(0)
    }
}
