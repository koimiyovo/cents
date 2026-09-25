package com.kyovo.cents.data

import kotlinx.coroutines.runBlocking
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.infrastructure.persistence.ListAccountRepository
import com.kyovo.cents.infrastructure.persistence.ListSubcategoryRepository
import com.kyovo.cents.infrastructure.persistence.ListTransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The demo data is what the app shows at every launch: every screen, and every number on them, comes
 * from it. Nothing else checks it, so a slip in it — a transaction pointing to a subcategory that is
 * not there, a closed account that doesn't end at zero — would only show up on the phone.
 */
class HardcodedDataTest
{
    private val accountRepository = ListAccountRepository()
    private val transactionRepository = ListTransactionRepository()
    private val subcategoryRepository = ListSubcategoryRepository()

    private val accounts: List<Account>
    private val transactions: List<Transaction>
    private val subcategories: List<Subcategory>

    init
    {
        runBlocking { seedHardcodedData(accountRepository, transactionRepository, subcategoryRepository) }
        accounts = runBlocking { accountRepository.findAll() }
        transactions = transactionRepository.findAll()
        subcategories = runBlocking { subcategoryRepository.findAll() }
    }

    private fun account(name: String) = accounts.single { it.name.value == name }

    private fun balanceOf(account: Account) =
        transactions.filter { it.accountId == account.id }.sumOf { it.signedAmount }

    // ------------------------------------------------------------------ accounts

    @Test
    fun `seeds the three demo accounts`()
    {
        assertThat(accounts.map { it.name.value })
            .containsExactlyInAnyOrder("Compte Courant", "Portefeuille Espèces", "Ancien Compte Joint")
    }

    @Test
    fun `only the old joint account is archived, so the archived section shows from the first launch`()
    {
        assertThat(accounts.filter { it.archivedAt != null }.map { it.name.value })
            .containsExactly("Ancien Compte Joint")
    }

    @Test
    fun `the names of the active accounts are unique`()
    {
        val names = accounts.filter { it.archivedAt == null }.map { it.name.value.lowercase() }

        assertThat(names).doesNotHaveDuplicates()
    }

    @Test
    fun `the closed account ends at exactly zero, as a real closed account would`()
    {
        assertThat(balanceOf(account("Ancien Compte Joint"))).isZero()
    }

    @Test
    fun `the active accounts have money in them`()
    {
        assertThat(balanceOf(account("Compte Courant"))).isPositive()
        assertThat(balanceOf(account("Portefeuille Espèces"))).isPositive()
    }

    @Test
    fun `an archived account was archived after it was created`()
    {
        val joint = account("Ancien Compte Joint")

        assertThat(joint.archivedAt).isAfter(joint.createdAt)
    }

    // ------------------------------------------------------------------ transactions

    @Test
    fun `every transaction belongs to a seeded account`()
    {
        val accountIds = accounts.map { it.id }.toSet()

        assertThat(transactions.map { it.accountId }).allMatch { it in accountIds }
    }

    @Test
    fun `no two transactions share an id`()
    {
        assertThat(transactions.map { it.id }).doesNotHaveDuplicates()
    }

    @Test
    fun `no transaction is dated in the future`()
    {
        assertThat(transactions.map { it.date }).allMatch { !it.isAfter(Instant.now()) }
    }

    @Test
    fun `no account has a transaction older than the account itself`()
    {
        transactions.forEach { transaction ->
            val owner = accounts.single { it.id == transaction.accountId }
            assertThat(transaction.date).describedAs(transaction.title.value).isAfterOrEqualTo(owner.createdAt)
        }
    }

    @Test
    fun `only the accounts documented as opened with money have an opening deposit`()
    {
        val withDeposit = transactions
            .filter { it.category == TransactionCategory.INITIAL_DEPOSIT }
            .map { transaction -> accounts.single { it.id == transaction.accountId }.name.value }

        assertThat(withDeposit).containsExactlyInAnyOrder("Portefeuille Espèces", "Ancien Compte Joint")
    }

    @Test
    fun `a transfer moves money without creating or destroying any`()
    {
        val out = transactions.filter { it.category == TransactionCategory.TRANSFER_OUT }.sumOf { it.amount.value }
        val into = transactions.filter { it.category == TransactionCategory.TRANSFER_IN }.sumOf { it.amount.value }

        assertThat(out).isPositive()
        assertThat(into).isEqualTo(out)
    }

    @Test
    fun `there are transfers between accounts, not only incomes and expenses`()
    {
        assertThat(transactions.map { it.category })
            .contains(TransactionCategory.TRANSFER_OUT, TransactionCategory.TRANSFER_IN)
    }

    // ------------------------------------------------------------------ subcategories

    @Test
    fun `seeds subcategories of both kinds`()
    {
        assertThat(subcategories.map { it.kind }.toSet()).hasSize(2)
    }

    @Test
    fun `subcategory names are unique among the ones of the same kind`()
    {
        subcategories.groupBy { it.kind }.forEach { (kind, sameKind) ->
            val names = sameKind.map { it.name.value.lowercase() }
            assertThat(names).describedAs(kind.name).doesNotHaveDuplicates()
        }
    }

    @Test
    fun `every subcategory has an emoji, so no row starts with the fallback icon`()
    {
        assertThat(subcategories).allMatch { it.emoji != null }
    }

    @Test
    fun `every transaction points to a subcategory that exists`()
    {
        val subcategoryIds = subcategories.map { it.id }.toSet()

        assertThat(transactions.mapNotNull { it.subcategoryId }).allMatch { it in subcategoryIds }
    }

    @Test
    fun `a transaction's subcategory is of its own kind`()
    {
        val byId = subcategories.associateBy { it.id }

        transactions.filter { it.subcategoryId != null }.forEach { transaction ->
            val subcategory = byId.getValue(transaction.subcategoryId!!)
            assertThat(subcategory.kind.toTransactionCategory())
                .describedAs(transaction.title.value)
                .isEqualTo(transaction.category)
        }
    }

    @Test
    fun `every seeded subcategory is used by at least one transaction, so the management screen shows real counts`()
    {
        val used = transactions.mapNotNull { it.subcategoryId }.toSet()

        assertThat(subcategories.map { it.id }).allMatch { it in used }
    }

    @Test
    fun `some transactions have no subcategory, as a real history does`()
    {
        assertThat(transactions.filter { it.category == TransactionCategory.EXPENSE }.map { it.subcategoryId })
            .contains(null as SubcategoryId?)
    }
}
