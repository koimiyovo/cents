package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

class AccountTest
{
    private val id = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val name = AccountName("Livret A")
    private val currency = AccountCurrency(Currency.getInstance("EUR"))
    private val createdAt = Instant.parse("2026-09-22T10:00:00Z")

    @Test
    fun `has no description by default`()
    {
        // WHEN
        val account = Account(id, name, AccountType.SAVINGS, currency, createdAt)

        // THEN
        assertThat(account.description).isNull()
    }

    @Test
    fun `can have a description`()
    {
        // WHEN
        val account = Account(
            id = id,
            name = name,
            type = AccountType.SAVINGS,
            currency = currency,
            createdAt = createdAt,
            description = AccountDescription.of("Épargne de précaution")
        )

        // THEN
        assertThat(account.description?.value).isEqualTo("Épargne de précaution")
    }
}
