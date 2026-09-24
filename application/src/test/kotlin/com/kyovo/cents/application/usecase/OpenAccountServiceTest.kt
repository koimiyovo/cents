package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedAccountIdGenerator
import com.kyovo.cents.application.fakes.FixedTransactionIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.aClock
import com.kyovo.cents.application.fakes.aCurrency
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.anOpenAccountCommand
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock

class OpenAccountServiceTest
{
    @Test
    fun `opens an account with the given name, type and currency, a generated id and the current instant, and saves it`()
    {
        // GIVEN
        val generatedId = anAccountId()
        val now = anInstant()
        val repository = InMemoryAccountRepository()
        val service =
            anOpenAccountService(repository, FixedAccountIdGenerator(generatedId), aClock(now))
        val command = anOpenAccountCommand()
        val expected = anAccount(id = generatedId, createdAt = now)

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved).containsExactly(expected)
    }

    @Test
    fun `opens another account with its own name, type, currency, generated id and creation instant`()
    {
        // GIVEN
        val generatedId = anAccountId("22222222-2222-2222-2222-222222222222")
        val now = anInstant("2027-01-15T18:30:00Z")
        val repository = InMemoryAccountRepository()
        val service =
            anOpenAccountService(repository, FixedAccountIdGenerator(generatedId), aClock(now))
        val command = anOpenAccountCommand(
            name = AccountName("Compte courant"),
            type = AccountType.SAVINGS,
            currency = aCurrency("USD")
        )
        val expected = anAccount(
            id = generatedId,
            name = AccountName("Compte courant"),
            type = AccountType.SAVINGS,
            currency = aCurrency("USD"),
            createdAt = now
        )

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved).containsExactly(expected)
    }

    @Test
    fun `keeps the description given when opening the account`()
    {
        // GIVEN
        val generatedId = anAccountId()
        val repository = InMemoryAccountRepository()
        val service =
            anOpenAccountService(
                repository,
                FixedAccountIdGenerator(generatedId),
                aClock(anInstant())
            )
        val description = AccountDescription.of("Épargne de précaution")
        val command = anOpenAccountCommand().copy(description = description)

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved.single().description).isEqualTo(description)
    }

    @Test
    fun `opens an account without description when none is given`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val service = anOpenAccountService(
            repository,
            FixedAccountIdGenerator(anAccountId()),
            aClock(anInstant())
        )

        // WHEN
        service.open(anOpenAccountCommand())

        // THEN
        assertThat(repository.saved.single().description).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Livret A", "livret a", "  livret A  "])
    fun `refuses to open an account whose name is already used by an existing account`(
        duplicateNameVariant: String
    )
    {
        // GIVEN
        val name = AccountName("Livret A")
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(name = name))
        val service =
            anOpenAccountService(repository, FixedAccountIdGenerator(anAccountId()), aClock())
        val command = anOpenAccountCommand(name = AccountName(duplicateNameVariant))

        // WHEN / THEN
        assertThatThrownBy { service.open(command) }
            .isInstanceOf(DuplicateAccountNameException::class.java)
    }

    @Test
    fun `does not save an account whose name is already used by an existing account`()
    {
        // GIVEN
        val name = AccountName("Livret A")
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(name = name))
        val service =
            anOpenAccountService(repository, FixedAccountIdGenerator(anAccountId()), aClock())
        val command = anOpenAccountCommand(name = name)

        // WHEN
        assertThatThrownBy { service.open(command) }
            .isInstanceOf(DuplicateAccountNameException::class.java)

        // THEN
        assertThat(repository.saved).hasSize(1)
    }

    @Test
    fun `opens a second account when its name differs from an existing account's name`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))
        val service = anOpenAccountService(
            repository,
            FixedAccountIdGenerator(anAccountId("22222222-2222-2222-2222-222222222222")),
            aClock()
        )
        val command = anOpenAccountCommand(name = AccountName("Compte courant"))

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved).hasSize(2)
    }

    @Test
    fun `opens an account whose name was used by another account that has since been deleted`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val deletedAccountId = anAccountId()
        repository.save(anAccount(id = deletedAccountId, name = AccountName("Livret A")))
        DeleteAccountService(
            repository,
            InMemoryTransactionRepository(),
            InMemoryUnitOfWork()
        ).delete(
            deletedAccountId
        )
        val service = anOpenAccountService(
            repository,
            FixedAccountIdGenerator(anAccountId("22222222-2222-2222-2222-222222222222")),
            aClock()
        )
        val command = anOpenAccountCommand(name = AccountName("Livret A"))

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved).hasSize(1)
    }

    @Test
    fun `opens an account whose name was used by another account that has since been archived`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val archivedAccountId = anAccountId()
        repository.save(
            anAccount(
                id = archivedAccountId,
                name = AccountName("Livret A"),
                archivedAt = anInstant()
            )
        )
        val service = anOpenAccountService(
            repository,
            FixedAccountIdGenerator(anAccountId("22222222-2222-2222-2222-222222222222")),
            aClock()
        )
        val command = anOpenAccountCommand(name = AccountName("Livret A"))

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved).hasSize(2)
    }

    @Test
    fun `records the initial amount as a transaction when opening an account`()
    {
        // GIVEN
        val generatedAccountId = anAccountId()
        val generatedTransactionId = aTransactionId()
        val now = anInstant()
        val transactionRepository = InMemoryTransactionRepository()
        val service = anOpenAccountService(
            repository = InMemoryAccountRepository(),
            accountIdGenerator = FixedAccountIdGenerator(generatedAccountId),
            clock = aClock(now),
            transactionRepository = transactionRepository,
            transactionIdGenerator = FixedTransactionIdGenerator(generatedTransactionId)
        )
        val command = anOpenAccountCommand(initialAmount = aMoney(15_000))

        // WHEN
        service.open(command)

        // THEN
        assertThat(transactionRepository.saved).containsExactly(
            aTransaction(
                id = generatedTransactionId,
                accountId = generatedAccountId,
                amount = aMoney(15_000),
                date = now,
                category = TransactionCategory.INITIAL_DEPOSIT
            )
        )
    }

    @Test
    fun `does not record any transaction when opening an account with no initial amount`()
    {
        // GIVEN
        val transactionRepository = InMemoryTransactionRepository()
        val service = anOpenAccountService(
            repository = InMemoryAccountRepository(),
            accountIdGenerator = FixedAccountIdGenerator(anAccountId()),
            transactionRepository = transactionRepository
        )
        val command = anOpenAccountCommand(initialAmount = aMoney(0))

        // WHEN
        service.open(command)

        // THEN
        assertThat(transactionRepository.saved).isEmpty()
    }

    @Test
    fun `wraps the account and transaction creation in a single unit of work`()
    {
        // GIVEN
        val unitOfWork = InMemoryUnitOfWork()
        val service = anOpenAccountService(
            repository = InMemoryAccountRepository(),
            accountIdGenerator = FixedAccountIdGenerator(anAccountId()),
            unitOfWork = unitOfWork
        )
        val command = anOpenAccountCommand()

        // WHEN
        service.open(command)

        // THEN
        assertThat(unitOfWork.executionCount).isEqualTo(1)
    }

    private fun anOpenAccountService(
        repository: InMemoryAccountRepository,
        accountIdGenerator: FixedAccountIdGenerator,
        clock: Clock = aClock(),
        transactionRepository: InMemoryTransactionRepository = InMemoryTransactionRepository(),
        transactionIdGenerator: FixedTransactionIdGenerator = FixedTransactionIdGenerator(
            aTransactionId()
        ),
        unitOfWork: InMemoryUnitOfWork = InMemoryUnitOfWork()
    ): OpenAccountService
    {
        return OpenAccountService(
            repository,
            accountIdGenerator,
            transactionRepository,
            transactionIdGenerator,
            unitOfWork,
            clock
        )
    }
}
