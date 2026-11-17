package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryExchangeRateRepository
import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The transactions a project's analysis shows (where its money went, its biggest expenses): only its own, each
 * amount in the project's currency, so they add up to what its progress says.
 */
class ListProjectTransactionsServiceTest
{
    private val japanId = aProjectId("11111111-1111-1111-1111-111111111111")
    private val kitchenId = aProjectId("22222222-2222-2222-2222-222222222222")
    private val euroAccount = anAccountId("33333333-3333-3333-3333-333333333333")

    private val projectRepository = InMemoryProjectRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val accountRepository = InMemoryAccountRepository()
    private val service = ListProjectTransactionsService(
        projectRepository, transactionRepository, accountRepository, InMemoryExchangeRateRepository()
    )

    @Test
    fun `lists the transactions of the project, converted into its currency`() = runTest()
    {
        // GIVEN a project in CFA francs, with a euro expense; another project's expense
        accountRepository.save(anAccount(euroAccount, AccountName("Courant"), currency = AccountCurrency.EUR))
        projectRepository.save(aProject(id = japanId, currency = AccountCurrency.XOF))
        projectRepository.save(aProject(id = kitchenId, currency = AccountCurrency.EUR))
        val ours = aTransactionId("a0000000-0000-0000-0000-000000000001")
        transactionRepository.save(aTransaction(ours, euroAccount, aMoney(10_000), category = TransactionCategory.EXPENSE, projectId = japanId))
        transactionRepository.save(
            aTransaction(
                aTransactionId("a0000000-0000-0000-0000-000000000002"), euroAccount, aMoney(5_000),
                category = TransactionCategory.EXPENSE, projectId = kitchenId,
            )
        )

        // WHEN
        val listed = service.observe(japanId).first()

        // THEN
        assertThat(listed.map { it.id }).containsExactly(ours)
        assertThat(listed.single().amount).isEqualTo(aMoney(65_596))
    }

    @Test
    fun `is empty for a project that does not exist`() = runTest()
    {
        assertThat(service.observe(japanId).first()).isEmpty()
    }
}
