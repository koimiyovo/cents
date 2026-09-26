package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.FixedTransactionIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.SequentialTransactionIdGenerator
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aRecordTransferCommand
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.aTransactionTitle
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.InvalidTransactionAmountException
import com.kyovo.cents.domain.exception.TransferToSameAccountException
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecordTransferServiceTest
{
    private val fromAccountId = anAccountId("11111111-1111-1111-1111-111111111111")
    private val toAccountId = anAccountId("22222222-2222-2222-2222-222222222222")

    @Test
    fun `records a debit on the source account and a credit on the destination account, for the same amount`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId))
        accountRepository.save(anAccount(id = toAccountId))
        val transactionRepository = InMemoryTransactionRepository()
        val debitId = aTransactionId("33333333-3333-3333-3333-333333333333")
        val creditId = aTransactionId("44444444-4444-4444-4444-444444444444")
        val service = aRecordTransferService(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            transactionIdGenerator = SequentialTransactionIdGenerator(listOf(debitId, creditId))
        )
        val command = aRecordTransferCommand(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = aMoney(2_000)
        )

        // WHEN
        val result = service.record(command)

        // THEN
        assertThat(result.debit.id).isEqualTo(debitId)
        assertThat(result.debit.accountId).isEqualTo(fromAccountId)
        assertThat(result.debit.category).isEqualTo(TransactionCategory.TRANSFER_OUT)
        assertThat(result.debit.signedAmount).isEqualTo(-2_000L)
        assertThat(result.credit.id).isEqualTo(creditId)
        assertThat(result.credit.accountId).isEqualTo(toAccountId)
        assertThat(result.credit.category).isEqualTo(TransactionCategory.TRANSFER_IN)
        assertThat(result.credit.signedAmount).isEqualTo(2_000L)
        assertThat(transactionRepository.saved).containsExactly(result.debit, result.credit)
    }

    @Test
    fun `both legs share the same title and date`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId))
        accountRepository.save(anAccount(id = toAccountId))
        val service = aRecordTransferService(accountRepository = accountRepository)
        val title = aTransactionTitle("Vers Portefeuille Espèces")
        val date = anInstant("2026-09-24T08:00:00Z")
        val command = aRecordTransferCommand(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            title = title,
            date = date
        )

        // WHEN
        val result = service.record(command)

        // THEN
        assertThat(result.debit.title).isEqualTo(title)
        assertThat(result.credit.title).isEqualTo(title)
        assertThat(result.debit.date).isEqualTo(date)
        assertThat(result.credit.date).isEqualTo(date)
    }

    @Test
    fun `refuses a transfer to the same account`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId))
        val service = aRecordTransferService(accountRepository = accountRepository)
        val command = aRecordTransferCommand(fromAccountId = fromAccountId, toAccountId = fromAccountId)

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command) }
            .isInstanceOf(TransferToSameAccountException::class.java)
    }

    @Test
    fun `throws when the source account does not exist`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = toAccountId))
        val service = aRecordTransferService(accountRepository = accountRepository)
        val command = aRecordTransferCommand(fromAccountId = fromAccountId, toAccountId = toAccountId)

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `throws when the destination account does not exist`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId))
        val service = aRecordTransferService(accountRepository = accountRepository)
        val command = aRecordTransferCommand(fromAccountId = fromAccountId, toAccountId = toAccountId)

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `throws when the source account is archived`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId, archivedAt = anInstant()))
        accountRepository.save(anAccount(id = toAccountId))
        val service = aRecordTransferService(accountRepository = accountRepository)
        val command = aRecordTransferCommand(fromAccountId = fromAccountId, toAccountId = toAccountId)

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command) }
            .isInstanceOf(CannotRecordTransactionOnArchivedAccountException::class.java)
    }

    @Test
    fun `throws when the destination account is archived`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId))
        accountRepository.save(anAccount(id = toAccountId, archivedAt = anInstant()))
        val service = aRecordTransferService(accountRepository = accountRepository)
        val command = aRecordTransferCommand(fromAccountId = fromAccountId, toAccountId = toAccountId)

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command) }
            .isInstanceOf(CannotRecordTransactionOnArchivedAccountException::class.java)
    }

    // Same rule as recording an income or an expense: a movement of nothing is refused, and since a
    // transfer is two legs, neither of them may be left behind.
    @Test
    fun `refuses a transfer of zero and saves neither leg`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId))
        accountRepository.save(anAccount(id = toAccountId))
        val transactionRepository = InMemoryTransactionRepository()
        val service = aRecordTransferService(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository
        )
        val command = aRecordTransferCommand(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = aMoney(0)
        )

        // WHEN / THEN
        assertThatThrownBySuspending { service.record(command) }
            .isInstanceOf(InvalidTransactionAmountException::class.java)
        assertThat(transactionRepository.saved).isEmpty()
    }

    @Test
    fun `wraps both transaction saves in a single unit of work`() = runTest()
    {
        // GIVEN
        val accountRepository = InMemoryAccountRepository()
        accountRepository.save(anAccount(id = fromAccountId))
        accountRepository.save(anAccount(id = toAccountId))
        val unitOfWork = InMemoryUnitOfWork()
        val service = aRecordTransferService(accountRepository = accountRepository, unitOfWork = unitOfWork)
        val command = aRecordTransferCommand(fromAccountId = fromAccountId, toAccountId = toAccountId)

        // WHEN
        service.record(command)

        // THEN
        assertThat(unitOfWork.executionCount).isEqualTo(1)
    }

    private fun aRecordTransferService(
        accountRepository: InMemoryAccountRepository,
        transactionRepository: InMemoryTransactionRepository = InMemoryTransactionRepository(),
        transactionIdGenerator: TransactionIdGenerator = FixedTransactionIdGenerator(aTransactionId()),
        unitOfWork: InMemoryUnitOfWork = InMemoryUnitOfWork()
    ): RecordTransferService
    {
        return RecordTransferService(
            accountRepository,
            transactionRepository,
            transactionIdGenerator,
            unitOfWork
        )
    }
}
