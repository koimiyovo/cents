package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anUpdateTransactionCommand
import com.kyovo.cents.domain.exception.CannotUpdateTransferException
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * A transfer is two transactions, one on each account, that nothing links together — and the update
 * command only knows incomes and expenses. Updating one leg would turn it into a plain movement and
 * leave the other one orphaned, so either leg is refused, whatever else the command says.
 */
class UpdateTransactionServiceTransferTest
{
    private val id = aTransactionId()
    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val service = UpdateTransactionService(accountRepository, transactionRepository, InMemorySubcategoryRepository())

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["TRANSFER_OUT", "TRANSFER_IN"])
    fun `throws when the transaction is one leg of a transfer`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        transactionRepository.save(aTransaction(id = id, category = category))

        // WHEN / THEN
        assertThatThrownBySuspending { service.update(anUpdateTransactionCommand(id = id)) }
            .isInstanceOf(CannotUpdateTransferException::class.java)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["TRANSFER_OUT", "TRANSFER_IN"])
    fun `does not modify the leg it refuses to update`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        val leg = aTransaction(id = id, category = category)
        transactionRepository.save(leg)

        // WHEN
        assertThatThrownBySuspending { service.update(anUpdateTransactionCommand(id = id)) }
            .isInstanceOf(CannotUpdateTransferException::class.java)

        // THEN
        assertThat(transactionRepository.saved).containsExactly(leg)
    }

    @Test
    fun `refuses a transfer leg before looking at where the command would move it`() = runTest()
    {
        // GIVEN a command aimed at an account that doesn't even exist
        transactionRepository.save(aTransaction(id = id, category = TransactionCategory.TRANSFER_OUT))
        val command = anUpdateTransactionCommand(
            id = id,
            accountId = anAccountId("99999999-9999-9999-9999-999999999999"),
        )

        // WHEN / THEN it is the transfer rule that answers, not "account not found"
        assertThatThrownBySuspending { service.update(command) }
            .isInstanceOf(CannotUpdateTransferException::class.java)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["TRANSFER_OUT", "TRANSFER_IN"])
    fun `refuses a transfer leg before looking at the subcategory`(category: TransactionCategory) = runTest()
    {
        // GIVEN a command whose subcategory doesn't exist
        transactionRepository.save(aTransaction(id = id, category = category))

        // WHEN / THEN it is the transfer rule that answers
        assertThatThrownBySuspending { service.update(anUpdateTransactionCommand(id = id, subcategoryId = aSubcategoryId())) }
            .isInstanceOf(CannotUpdateTransferException::class.java)
    }
}
