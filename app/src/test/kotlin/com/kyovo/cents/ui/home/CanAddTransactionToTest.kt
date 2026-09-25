package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

private val WHEN_IT_HAPPENED = Instant.parse("2026-09-20T08:30:00Z")

private fun anAccount(archived: Boolean) = Account(
    id = AccountId(UUID.randomUUID()),
    name = AccountName("Compte"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = WHEN_IT_HAPPENED,
    archivedAt = if (archived) WHEN_IT_HAPPENED else null,
)

/**
 * The "+ Transaction" button of an account's page: an archived account takes no new transaction (a
 * domain rule), so there is no button rather than a button leading to an error. An account that is
 * not there (deleted while its page was open) has none either.
 */
class CanAddTransactionToTest
{
    @Test
    fun `an active account takes a new transaction`()
    {
        assertThat(canAddTransactionTo(anAccount(archived = false))).isTrue()
    }

    @Test
    fun `an archived account does not`()
    {
        assertThat(canAddTransactionTo(anAccount(archived = true))).isFalse()
    }

    @Test
    fun `an account that is gone does not`()
    {
        assertThat(canAddTransactionTo(null)).isFalse()
    }
}
