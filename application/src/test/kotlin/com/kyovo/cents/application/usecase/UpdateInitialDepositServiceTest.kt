package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.InvalidInitialDepositAmountException
import com.kyovo.cents.domain.exception.NotAnInitialDepositException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The opening deposit can't be deleted or turned into something else ([UpdateTransactionService]
 * still refuses it, and so does [DeleteTransactionService]) — but the *amount* is the one thing a
 * user can get wrong when opening an account, and nothing else could correct it. So it has its own
 * use case, which changes the amount and nothing more: same transaction, same account, same title,
 * same date, still an initial deposit.
 *
 * It doesn't go through `UpdateTransactionCommand`, which only speaks incomes and expenses.
 */
class UpdateInitialDepositServiceTest
{
    private val id = aTransactionId()
    private val accountId = anAccountId()
    private val date = anInstant("2026-01-01T00:00:00Z")

    private val transactionRepository = InMemoryTransactionRepository()
    private val service = UpdateInitialDepositService(transactionRepository)

    private suspend fun givenAnInitialDeposit(amount: Long = 10_000)
    {
        transactionRepository.save(
            aTransaction(
                id = id,
                accountId = accountId,
                amount = aMoney(amount),
                date = date,
                category = TransactionCategory.INITIAL_DEPOSIT,
            ),
        )
    }

    @Test
    fun `changes the amount of the initial deposit`() = runTest()
    {
        // GIVEN
        givenAnInitialDeposit(amount = 10_000)

        // WHEN
        val result = service.update(id, aMoney(25_050))

        // THEN
        assertThat(result.amount).isEqualTo(aMoney(25_050))
        assertThat(transactionRepository.findById(id)).isEqualTo(result)
    }

    @Test
    fun `changes nothing but the amount`() = runTest()
    {
        // GIVEN
        givenAnInitialDeposit(amount = 10_000)
        val before = transactionRepository.findById(id)!!

        // WHEN
        val result = service.update(id, aMoney(25_050))

        // THEN
        assertThat(result).isEqualTo(
            aTransaction(
                id = before.id,
                accountId = before.accountId,
                amount = aMoney(25_050),
                date = before.date,
                category = TransactionCategory.INITIAL_DEPOSIT,
                title = before.title,
            ),
        )
        assertThat(result.category).isEqualTo(TransactionCategory.INITIAL_DEPOSIT)
    }

    @Test
    fun `leaves the other transactions alone`() = runTest()
    {
        // GIVEN
        givenAnInitialDeposit()
        val other = aTransaction(
            id = aTransactionId("55555555-5555-5555-5555-555555555555"),
            accountId = accountId,
            amount = aMoney(1_000),
            category = TransactionCategory.EXPENSE,
        )
        transactionRepository.save(other)

        // WHEN
        service.update(id, aMoney(25_050))

        // THEN
        assertThat(transactionRepository.findById(other.id)).isEqualTo(other)
        assertThat(transactionRepository.saved).hasSize(2)
    }

    // Opening an account with 0 creates no deposit at all (see OpenAccountService), so an existing
    // deposit can't be turned into a 0 one — a state opening never produces.
    @Test
    fun `throws when the new amount is zero`() = runTest()
    {
        // GIVEN
        givenAnInitialDeposit(amount = 10_000)

        // WHEN / THEN
        assertThatThrownBySuspending { service.update(id, aMoney(0)) }
            .isInstanceOf(InvalidInitialDepositAmountException::class.java)
    }

    @Test
    fun `does not modify the deposit when the new amount is zero`() = runTest()
    {
        // GIVEN
        givenAnInitialDeposit(amount = 10_000)
        val before = transactionRepository.findById(id)

        // WHEN
        assertThatThrownBySuspending { service.update(id, aMoney(0)) }
            .isInstanceOf(InvalidInitialDepositAmountException::class.java)

        // THEN
        assertThat(transactionRepository.saved).containsExactly(before)
    }

    @Test
    fun `throws when the transaction does not exist`() = runTest()
    {
        // WHEN / THEN
        assertThatThrownBySuspending { service.update(id, aMoney(25_050)) }
            .isInstanceOf(TransactionNotFoundException::class.java)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["INITIAL_DEPOSIT"], mode = EnumSource.Mode.EXCLUDE)
    fun `throws when the transaction is not an initial deposit`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        transactionRepository.save(aTransaction(id = id, category = category))

        // WHEN / THEN
        assertThatThrownBySuspending { service.update(id, aMoney(25_050)) }
            .isInstanceOf(NotAnInitialDepositException::class.java)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["INITIAL_DEPOSIT"], mode = EnumSource.Mode.EXCLUDE)
    fun `does not modify a transaction it refuses to update`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        val original = aTransaction(id = id, amount = aMoney(1_000), category = category)
        transactionRepository.save(original)

        // WHEN
        assertThatThrownBySuspending { service.update(id, aMoney(25_050)) }
            .isInstanceOf(NotAnInitialDepositException::class.java)

        // THEN
        assertThat(transactionRepository.saved).containsExactly(original)
    }
}
