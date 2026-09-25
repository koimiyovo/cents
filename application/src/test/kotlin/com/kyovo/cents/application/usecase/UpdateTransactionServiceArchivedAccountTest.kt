package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.aTransactionTitle
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.anUpdateTransactionCommand
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * **Decision: a transaction that sits on an archived account can still be edited.** An archived
 * account keeps its history, and a mistake found in it later (an amount, a category, a title) must
 * stay correctable — the alternative, unarchiving to fix it, can be blocked by a name that has been
 * reused since. What an archived account refuses is *receiving* a transaction, recorded or moved in
 * (see [UpdateTransactionServiceAccountChangeTest]); editing what it already holds is a different
 * matter.
 *
 * The consequence is deliberate and accepted: the account's balance follows the edit, even though
 * it is closed. The UI says so when the user opens such a transaction.
 */
class UpdateTransactionServiceArchivedAccountTest
{
    private val id = aTransactionId()
    private val accountId = anAccountId()
    private val depositId = aTransactionId("44444444-4444-4444-4444-444444444444")

    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val refundId = aSubcategoryId("22222222-2222-2222-2222-222222222222")
    private val service = UpdateTransactionService(accountRepository, transactionRepository, subcategoryRepository)

    private val archivedAccount = anAccount(id = accountId).copy(archivedAt = anInstant("2026-01-01T00:00:00Z"))

    private fun givenAnExpenseOnTheArchivedAccount()
    {
        accountRepository.save(archivedAccount)
        transactionRepository.save(
            aTransaction(id = id, accountId = accountId, amount = aMoney(1_000), category = TransactionCategory.EXPENSE),
        )
    }

    @Test
    fun `updates every editable field of a transaction that sits on an archived account`() = runTest()
    {
        // GIVEN
        givenAnExpenseOnTheArchivedAccount()
        subcategoryRepository.save(aSubcategory(id = refundId, kind = RecordableTransactionCategory.INCOME))
        val date = anInstant("2025-12-05T08:00:00Z")

        // WHEN
        val result = service.update(
            anUpdateTransactionCommand(
                id = id,
                accountId = accountId,
                amount = aMoney(2_500),
                title = aTransactionTitle("Titre corrigé"),
                category = RecordableTransactionCategory.INCOME,
                subcategoryId = refundId,
                description = TransactionDescription.of("Corrigé après coup"),
                date = date,
            ),
        )

        // THEN
        val expected = aTransaction(
            id = id,
            accountId = accountId,
            amount = aMoney(2_500),
            title = aTransactionTitle("Titre corrigé"),
            category = TransactionCategory.INCOME,
            subcategoryId = refundId,
            description = TransactionDescription.of("Corrigé après coup"),
            date = date,
        )
        assertThat(result).isEqualTo(expected)
        assertThat(transactionRepository.saved).containsExactly(expected)
    }

    @Test
    fun `editing a transaction leaves its account archived and untouched`() = runTest()
    {
        // GIVEN
        givenAnExpenseOnTheArchivedAccount()

        // WHEN
        service.update(anUpdateTransactionCommand(id = id, accountId = accountId, amount = aMoney(2_500)))

        // THEN
        assertThat(accountRepository.saved).containsExactly(archivedAccount)
    }

    @Test
    fun `the balance of the archived account follows the edit`() = runTest()
    {
        // GIVEN an archived account that opened with 100,00 € and holds a 10,00 € expense
        givenAnExpenseOnTheArchivedAccount()
        transactionRepository.save(
            aTransaction(
                id = depositId,
                accountId = accountId,
                amount = aMoney(10_000),
                category = TransactionCategory.INITIAL_DEPOSIT,
            ),
        )
        val balances = GetAccountBalanceService(accountRepository, transactionRepository)
        assertThat(balances.getBalance(accountId)!!.value).isEqualTo(9_000)

        // WHEN the expense is corrected to 25,00 €
        service.update(anUpdateTransactionCommand(id = id, accountId = accountId, amount = aMoney(2_500)))

        // THEN the closed account's final balance changed with it: nothing freezes it
        assertThat(balances.getBalance(accountId)!!.value).isEqualTo(7_500)
    }
}
