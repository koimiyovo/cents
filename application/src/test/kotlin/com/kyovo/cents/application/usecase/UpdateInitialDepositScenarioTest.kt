package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.InvalidInitialDepositAmountException
import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Correcting the opening deposit is only worth it if the account's balance follows. The balance is
 * computed from the transactions, so it should — these tests pin it, through the real services.
 */
class UpdateInitialDepositScenarioTest
{
    private val accountId = anAccountId()
    private val depositId = aTransactionId("44444444-4444-4444-4444-444444444444")
    private val expenseId = aTransactionId("55555555-5555-5555-5555-555555555555")

    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val updateInitialDeposit = UpdateInitialDepositService(transactionRepository)
    private val getBalance = GetAccountBalanceService(accountRepository, transactionRepository)

    /** An account that opened with 100,00 € and spent 30,00 €: 70,00 € left. */
    private fun givenAnAccountWithADepositAndAnExpense(archived: Boolean = false)
    {
        accountRepository.save(
            anAccount(id = accountId, archivedAt = if (archived) anInstant("2026-01-01T00:00:00Z") else null),
        )
        transactionRepository.save(
            aTransaction(
                id = depositId,
                accountId = accountId,
                amount = aMoney(10_000),
                category = TransactionCategory.INITIAL_DEPOSIT,
            ),
        )
        transactionRepository.save(
            aTransaction(
                id = expenseId,
                accountId = accountId,
                amount = aMoney(3_000),
                category = TransactionCategory.EXPENSE,
            ),
        )
    }

    @Test
    fun `the balance follows the new amount of the deposit`()
    {
        // GIVEN
        givenAnAccountWithADepositAndAnExpense()
        assertThat(getBalance.getBalance(accountId)).isEqualTo(AccountBalance(7_000))

        // WHEN the deposit was really 250,50 €
        updateInitialDeposit.update(depositId, aMoney(25_050))

        // THEN 250,50 - 30,00
        assertThat(getBalance.getBalance(accountId)).isEqualTo(AccountBalance(22_050))
    }

    @Test
    fun `the balance of an archived account follows too`()
    {
        // GIVEN
        givenAnAccountWithADepositAndAnExpense(archived = true)

        // WHEN
        updateInitialDeposit.update(depositId, aMoney(25_050))

        // THEN
        assertThat(getBalance.getBalance(accountId)).isEqualTo(AccountBalance(22_050))
    }

    @Test
    fun `only the deposit changes, the other transactions of the account are left alone`()
    {
        // GIVEN
        givenAnAccountWithADepositAndAnExpense()
        val expenseBefore = transactionRepository.findById(expenseId)

        // WHEN
        updateInitialDeposit.update(depositId, aMoney(25_050))

        // THEN
        assertThat(transactionRepository.findById(expenseId)).isEqualTo(expenseBefore)
        assertThat(transactionRepository.saved).hasSize(2)
    }

    // The zero check comes before the lookup: a 0 is refused whatever it is asked of. This pins that
    // order, so that changing it is a decision and not an accident.
    @Test
    fun `a zero is refused before the transaction is even looked up`()
    {
        // WHEN / THEN
        assertThatThrownBy { updateInitialDeposit.update(aTransactionId("99999999-9999-9999-9999-999999999999"), aMoney(0)) }
            .isInstanceOf(InvalidInitialDepositAmountException::class.java)
    }

    @Test
    fun `a refused zero leaves the balance as it was`()
    {
        // GIVEN
        givenAnAccountWithADepositAndAnExpense()

        // WHEN
        assertThatThrownBy { updateInitialDeposit.update(depositId, aMoney(0)) }
            .isInstanceOf(InvalidInitialDepositAmountException::class.java)

        // THEN
        assertThat(getBalance.getBalance(accountId)).isEqualTo(AccountBalance(7_000))
    }
}
