package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.port.output.AccountRepository
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * What every [AccountRepository] must do, whatever it stores the accounts in: the port's specification,
 * run today by the Room adapter (see [SubcategoryRepositoryContract] for why the tests run in real
 * time and wait for the state they expect). The order of the accounts is part of the stored data:
 * the user arranges them by hand, and every listing comes back in that order.
 */
abstract class AccountRepositoryContract
{
    /** A fresh, empty repository. Called before each test. */
    protected abstract fun createRepository(): AccountRepository

    protected lateinit var repository: AccountRepository

    @BeforeEach
    fun createTheRepository()
    {
        repository = createRepository()
    }

    private val idA = accountId(1)
    private val idB = accountId(2)
    private val idC = accountId(3)
    private val idD = accountId(4)

    private fun accountId(suffix: Int) = AccountId(UUID.fromString("11111111-1111-1111-1111-11111111111$suffix"))

    private fun anAccount(
        id: AccountId,
        name: String = "Compte ${id.value}",
        type: AccountType = AccountType.CHECKING,
        currency: String = "EUR",
        createdAt: Instant = Instant.parse("2026-09-22T10:00:00Z"),
        archivedAt: Instant? = null,
        description: String? = null,
    ) = Account(
        id = id,
        name = AccountName(name),
        type = type,
        currency = AccountCurrency(Currency.getInstance(currency)),
        createdAt = createdAt,
        archivedAt = archivedAt,
        description = AccountDescription.of(description),
    )

    // (a value class cannot be a vararg, hence the list)
    private suspend fun saveAll(ids: List<AccountId>) = ids.forEach { repository.save(anAccount(it)) }

    private suspend fun ids() = repository.findAll().map { it.id }

    // ------------------------------------------------------------------ reading what was saved

    @Test
    fun `finds an account that has been saved`() = realTime()
    {
        // GIVEN
        val account = anAccount(idA, name = "Livret A")
        repository.save(account)

        // WHEN / THEN
        assertThat(repository.findById(idA)).isEqualTo(account)
    }

    @Test
    fun `finds nothing for an unknown id`() = realTime()
    {
        assertThat(repository.findById(idA)).isNull()
    }

    @Test
    fun `lists nothing when nothing was saved`() = realTime()
    {
        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `gives back every field of an account, whatever its type`() = realTime()
    {
        // GIVEN one account of each type, each with something different in every field
        val checking = anAccount(idA, "Courant", AccountType.CHECKING, "EUR", Instant.parse("2026-01-02T03:04:05Z"))
        val savings = anAccount(idB, "Livret", AccountType.SAVINGS, "USD", Instant.parse("2025-12-31T23:59:59Z"), Instant.parse("2026-02-01T00:00:00Z"), "Pour les vacances\nd'été 🌴")
        val cash = anAccount(idC, "Espèces", AccountType.CASH, "JPY", Instant.parse("2026-06-15T12:00:00Z"))
        repository.save(checking)
        repository.save(savings)
        repository.save(cash)

        // WHEN / THEN
        assertThat(repository.findAll()).containsExactly(checking, savings, cash)
    }

    @Test
    fun `keeps an instant to the nanosecond`() = realTime()
    {
        // GIVEN instants a clock can really produce, far finer than a millisecond
        val account = anAccount(
            idA,
            createdAt = Instant.parse("2026-09-22T10:00:00.123456789Z"),
            archivedAt = Instant.parse("2026-09-23T11:12:13.000000001Z"),
        )

        // WHEN
        repository.save(account)

        // THEN
        assertThat(repository.findById(idA)).isEqualTo(account)
    }

    @Test
    fun `an account can be given a description, and lose it`() = realTime()
    {
        // GIVEN
        val account = anAccount(idA, description = "Compte principal")
        repository.save(account)
        assertThat(repository.findById(idA)?.description).isEqualTo(account.description)

        // WHEN
        repository.save(account.copy(description = null))

        // THEN
        assertThat(repository.findById(idA)?.description).isNull()
    }

    @Test
    fun `keeps a name of the longest length, with quotes and accents`() = realTime()
    {
        // GIVEN
        val name = "L'épargne \"bio\"; --".padEnd(AccountName.MAX_LENGTH, 'x')
        val account = anAccount(idA, name = name)

        // WHEN
        repository.save(account)

        // THEN
        assertThat(repository.findById(idA)).isEqualTo(account)
    }

    // ------------------------------------------------------------------ order

    @Test
    fun `a new account is added at the end`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB))

        // WHEN
        repository.save(anAccount(idC))

        // THEN
        assertThat(ids()).containsExactly(idA, idB, idC)
    }

    @Test
    fun `saving an existing account again keeps its position`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB, idC))

        // WHEN it is renamed, then archived
        repository.save(anAccount(idA, name = "Renommé"))
        repository.save(anAccount(idB, archivedAt = Instant.parse("2026-09-23T09:00:00Z")))

        // THEN
        assertThat(ids()).containsExactly(idA, idB, idC)
        assertThat(repository.findById(idA)?.name).isEqualTo(AccountName("Renommé"))
    }

    @Test
    fun `saving an existing account again does not duplicate it`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA))

        // WHEN
        repository.save(anAccount(idA, name = "Autre nom"))

        // THEN
        assertThat(repository.findAll()).hasSize(1)
    }

    @Test
    fun `an account added after a deletion goes to the end`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB, idC))
        repository.deleteById(idB)

        // WHEN
        repository.save(anAccount(idD))

        // THEN
        assertThat(ids()).containsExactly(idA, idC, idD)
    }

    @Test
    fun `reordering puts the accounts in the given order`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB, idC))

        // WHEN
        repository.reorder(listOf(idC, idA, idB))

        // THEN
        assertThat(ids()).containsExactly(idC, idA, idB)
    }

    @Test
    fun `reordering does not alter the accounts themselves`() = realTime()
    {
        // GIVEN
        val a = anAccount(idA, name = "A", description = "première")
        val b = anAccount(idB, name = "B", archivedAt = Instant.parse("2026-09-23T09:00:00Z"))
        repository.save(a)
        repository.save(b)

        // WHEN
        repository.reorder(listOf(idB, idA))

        // THEN
        assertThat(repository.findAll()).containsExactly(b, a)
    }

    @Test
    fun `reordering leaves the accounts that are not listed where they are`() = realTime()
    {
        // GIVEN an archived account (not in the drag list) between two active ones
        saveAll(listOf(idA, idB, idC))

        // WHEN only A and C are reordered
        repository.reorder(listOf(idC, idA))

        // THEN B did not move: A and C swapped the positions they held
        assertThat(ids()).containsExactly(idC, idB, idA)
    }

    @Test
    fun `reordering only some of the accounts shuffles them among their own positions`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB, idC, idD))

        // WHEN
        repository.reorder(listOf(idD, idB))

        // THEN B and D swapped, A and C stayed
        assertThat(ids()).containsExactly(idA, idD, idC, idB)
    }

    @Test
    fun `reordering with an empty list changes nothing`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB))

        // WHEN
        repository.reorder(emptyList())

        // THEN
        assertThat(ids()).containsExactly(idA, idB)
    }

    @Test
    fun `reordering ignores an id that matches no account`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB))

        // WHEN
        repository.reorder(listOf(idB, idD, idA))

        // THEN the two known accounts still fit their own slots
        assertThat(ids()).containsExactly(idB, idA)
    }

    @Test
    fun `the order survives a rename after a reorder`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB, idC))
        repository.reorder(listOf(idC, idB, idA))

        // WHEN
        repository.save(anAccount(idB, name = "Renommé"))

        // THEN
        assertThat(ids()).containsExactly(idC, idB, idA)
    }

    // ------------------------------------------------------------------ names

    @Test
    fun `does not consider a name used when no account has been saved`() = realTime()
    {
        assertThat(repository.existsByName(AccountName("Livret A"))).isFalse()
    }

    @Test
    fun `considers a name used once an account with that name has been saved`() = realTime()
    {
        // GIVEN
        repository.save(anAccount(idA, name = "Livret A"))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isTrue()
    }

    @Test
    fun `considers a name used regardless of case or surrounding spaces`() = realTime()
    {
        // GIVEN
        repository.save(anAccount(idA, name = "Livret A"))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("livret a"))).isTrue()
        assertThat(repository.existsByName(AccountName("  LIVRET A  "))).isTrue()
    }

    @Test
    fun `does not consider an unrelated name used`() = realTime()
    {
        // GIVEN
        repository.save(anAccount(idA, name = "Livret A"))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Compte courant"))).isFalse()
    }

    @Test
    fun `does not consider a name used by an archived account`() = realTime()
    {
        // GIVEN
        repository.save(anAccount(idA, name = "Livret A", archivedAt = Instant.parse("2026-09-23T09:00:00Z")))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isFalse()
    }

    @Test
    fun `stores two accounts with the same name`() = realTime()
    {
        // GIVEN an archived one and an active one: the store must not judge names, the services do
        repository.save(anAccount(idA, name = "Livret A", archivedAt = Instant.parse("2026-09-23T09:00:00Z")))
        repository.save(anAccount(idB, name = "Livret A"))

        // WHEN / THEN
        assertThat(repository.findAll()).hasSize(2)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `deletes an account and leaves the others`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA, idB))

        // WHEN
        repository.deleteById(idA)

        // THEN
        assertThat(ids()).containsExactly(idB)
        assertThat(repository.findById(idA)).isNull()
    }

    @Test
    fun `is silent about deleting an unknown account`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idA))

        // WHEN
        repository.deleteById(idB)

        // THEN
        assertThat(ids()).containsExactly(idA)
    }

    // ------------------------------------------------------------------ observing

    @Test
    fun `an observer first gets what is stored, in order`() = realTime()
    {
        // GIVEN
        saveAll(listOf(idB, idA))

        // WHEN / THEN
        assertThat(repository.observeAll().first().map { it.id }).containsExactly(idB, idA)
    }

    @Test
    fun `an observer of an empty repository gets an empty list`() = realTime()
    {
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `an observer collecting late still gets the current state`() = realTime()
    {
        // GIVEN changes made before anyone observes
        saveAll(listOf(idA, idB))
        repository.deleteById(idA)

        // WHEN / THEN
        assertThat(repository.observeAll().first().map { it.id }).containsExactly(idB)
    }

    @Test
    fun `an observer follows the repository as accounts are added, changed, reordered and deleted`() = realTime()
    {
        // GIVEN a screen collecting the accounts
        val latest = repository.observeAll().map { list -> list.map { it.name.value } }.stateIn(this, SharingStarted.Eagerly, null)
        latest.awaitMatching { it != null && it.isEmpty() }

        // WHEN / THEN each change shows up, in stored order
        repository.save(anAccount(idA, name = "A"))
        repository.save(anAccount(idB, name = "B"))
        latest.awaitMatching { it == listOf("A", "B") }

        repository.save(anAccount(idA, name = "Renamed"))
        latest.awaitMatching { it == listOf("Renamed", "B") }

        repository.reorder(listOf(idB, idA))
        latest.awaitMatching { it == listOf("B", "Renamed") }

        repository.deleteById(idB)
        latest.awaitMatching { it == listOf("Renamed") }

        coroutineContext.cancelChildren()
    }
}
