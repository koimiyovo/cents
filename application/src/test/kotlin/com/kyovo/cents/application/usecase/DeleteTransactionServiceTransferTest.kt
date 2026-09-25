package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.exception.CannotDeleteTransferException
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * A transfer is two transactions, one on each account, that nothing links together. Deleting one leg
 * alone would leave the other behind: money would appear on one account, or vanish from it, and the
 * balances would no longer add up. Neither leg can be deleted (just as neither can be updated).
 */
class DeleteTransactionServiceTransferTest
{
    private val repository = InMemoryTransactionRepository()
    private val service = DeleteTransactionService(repository)

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["TRANSFER_OUT", "TRANSFER_IN"])
    fun `throws when the transaction is one leg of a transfer`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        val id = aTransactionId()
        repository.save(aTransaction(id = id, category = category))

        // WHEN / THEN
        assertThatThrownBySuspending { service.delete(id) }
            .isInstanceOf(CannotDeleteTransferException::class.java)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["TRANSFER_OUT", "TRANSFER_IN"])
    fun `does not delete the leg it refuses to delete`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        val id = aTransactionId()
        val leg = aTransaction(id = id, category = category)
        repository.save(leg)

        // WHEN
        assertThatThrownBySuspending { service.delete(id) }
            .isInstanceOf(CannotDeleteTransferException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(leg)
    }

    @Test
    fun `leaves both legs of the transfer, and everything else, in place when it refuses`() = runTest()
    {
        // GIVEN a transfer between two accounts, and an unrelated expense
        val legOut = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333331"),
            accountId = anAccountId("11111111-1111-1111-1111-111111111111"),
            category = TransactionCategory.TRANSFER_OUT,
        )
        val legIn = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333332"),
            accountId = anAccountId("22222222-2222-2222-2222-222222222222"),
            category = TransactionCategory.TRANSFER_IN,
        )
        val expense = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333333"),
            accountId = anAccountId("11111111-1111-1111-1111-111111111111"),
            category = TransactionCategory.EXPENSE,
        )
        repository.save(legOut)
        repository.save(legIn)
        repository.save(expense)

        // WHEN one leg is targeted
        assertThatThrownBySuspending { service.delete(legOut.id) }
            .isInstanceOf(CannotDeleteTransferException::class.java)

        // THEN nothing has moved
        assertThat(repository.saved).containsExactlyInAnyOrder(legOut, legIn, expense)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["EXPENSE", "INCOME"])
    fun `still deletes an expense or an income`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        val id = aTransactionId()
        repository.save(aTransaction(id = id, category = category))

        // WHEN
        service.delete(id)

        // THEN
        assertThat(repository.saved).isEmpty()
    }
}
