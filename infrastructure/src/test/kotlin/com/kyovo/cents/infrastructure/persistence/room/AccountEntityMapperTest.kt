package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * The account's row is its own type. Its position (the order the user gave the accounts) is a fact
 * about the database, not about the account, so it is an argument of the conversion and never comes
 * back in the domain object.
 */
class AccountEntityMapperTest
{
    private val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val created = Instant.parse("2026-09-22T10:00:00.123456789Z")
    private val archived = Instant.parse("2026-09-23T11:00:00Z")

    private val account = Account(
        id = AccountId(uuid),
        name = AccountName("Livret A"),
        type = AccountType.SAVINGS,
        currency = AccountCurrency(Currency.getInstance("USD")),
        createdAt = created,
        archivedAt = archived,
        description = AccountDescription.of("Pour les vacances"),
    )

    @Test
    fun `an account becomes a row of plain values, with its position`()
    {
        // WHEN
        val entity = account.toEntity(position = 3)

        // THEN
        assertThat(entity).isEqualTo(
            AccountEntity(
                id = uuid,
                name = "Livret A",
                type = "SAVINGS",
                currency = "USD",
                createdAt = created.toEpochNanos(),
                archivedAt = archived.toEpochNanos(),
                description = "Pour les vacances",
                position = 3,
            ),
        )
    }

    @Test
    fun `an active account without a description has neither in its row`()
    {
        // WHEN
        val entity = account.copy(archivedAt = null, description = null).toEntity(position = 0)

        // THEN
        assertThat(entity.archivedAt).isNull()
        assertThat(entity.description).isNull()
    }

    @Test
    fun `each type is stored under its own name`()
    {
        assertThat(AccountType.entries.map { account.copy(type = it).toEntity(0).type })
            .containsExactly("CHECKING", "SAVINGS", "CASH")
    }

    @Test
    fun `a row becomes the account it came from`()
    {
        assertThat(account.toEntity(position = 7).toDomain()).isEqualTo(account)
    }

    @Test
    fun `a row without an archival instant or a description becomes an active account without a description`()
    {
        // GIVEN
        val row = AccountEntity(uuid, "Livret A", "CHECKING", "EUR", created.toEpochNanos(), null, null, 0)

        // WHEN
        val restored = row.toDomain()

        // THEN
        assertThat(restored.archivedAt).isNull()
        assertThat(restored.description).isNull()
        assertThat(restored.type).isEqualTo(AccountType.CHECKING)
    }

    @Test
    fun `a row with an unknown type is refused instead of being guessed`()
    {
        assertThatThrownBy { AccountEntity(uuid, "Livret A", "BROKERAGE", "EUR", 0, null, null, 0).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("BROKERAGE")
    }

    @Test
    fun `a row with an unknown currency is refused`()
    {
        assertThatThrownBy { AccountEntity(uuid, "Livret A", "CHECKING", "???", 0, null, null, 0).toDomain() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
