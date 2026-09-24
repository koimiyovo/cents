package com.kyovo.cents.data

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.ExpenseSubcategory
import com.kyovo.cents.domain.model.IncomeSubcategory
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionSubcategory
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Currency
import kotlin.uuid.Uuid

/**
 * Fixed demo data for the accounts/transactions screens, seeded straight into the in-memory
 * repositories. Stands in for real user input until account creation and transaction recording
 * are wired into the UI.
 */
fun seedHardcodedData(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository
)
{
    val now = Instant.now()
    val eur = AccountCurrency(Currency.getInstance("EUR"))

    val checking = Account(
        id = AccountId(Uuid.random()),
        name = AccountName("Compte Courant"),
        type = AccountType.CHECKING,
        currency = eur,
        createdAt = now.minus(400, ChronoUnit.DAYS),
        description = AccountDescription.of(
            "Compte courant principal pour les dépenses du quotidien et les prélèvements automatiques mensuels.",
        ),
    )
    val cash = Account(
        id = AccountId(Uuid.random()),
        name = AccountName("Portefeuille Espèces"),
        type = AccountType.CASH,
        currency = eur,
        createdAt = now.minus(200, ChronoUnit.DAYS),
    )

    // A closed joint account, archived since a move: it still has its history, but no longer
    // counts in the totals and takes no new transaction. Its last movement empties it into the
    // checking account, so it ends at a balance of zero, as a real closed account would.
    val oldJoint = Account(
        id = AccountId(Uuid.random()),
        name = AccountName("Ancien Compte Joint"),
        type = AccountType.CHECKING,
        currency = eur,
        createdAt = now.minus(520, ChronoUnit.DAYS),
        archivedAt = now.minus(300, ChronoUnit.DAYS),
        description = AccountDescription.of("Compte joint clos après le déménagement"),
    )

    listOf(checking, cash, oldJoint).forEach(accountRepository::save)

    fun deposit(accountId: AccountId, amountCents: Long, date: Instant)
    {
        transactionRepository.save(
            Transaction.openingDeposit(
                id = TransactionId(Uuid.random()),
                accountId = accountId,
                amount = Money(amountCents),
                date = date,
            ),
        )
    }

    fun recorded(
        accountId: AccountId,
        amountCents: Long,
        category: RecordableTransactionCategory,
        subcategory: TransactionSubcategory?,
        title: String,
        date: Instant,
    )
    {
        transactionRepository.save(
            Transaction.recorded(
                id = TransactionId(Uuid.random()),
                accountId = accountId,
                amount = Money(amountCents),
                title = TransactionTitle(title),
                category = category,
                subcategory = subcategory,
                description = null,
                date = date,
            ),
        )
    }

    // A transfer is two independent legs, not a single Transaction.recorded call: it doesn't
    // count as an expense or income on either side (see Transaction.transferOut/transferIn).
    fun transferred(
        fromAccountId: AccountId,
        toAccountId: AccountId,
        amountCents: Long,
        title: String,
        date: Instant,
    )
    {
        val amount = Money(amountCents)
        val transferTitle = TransactionTitle(title)
        transactionRepository.save(
            Transaction.transferOut(TransactionId(Uuid.random()), fromAccountId, amount, transferTitle, date),
        )
        transactionRepository.save(
            Transaction.transferIn(TransactionId(Uuid.random()), toAccountId, amount, transferTitle, date),
        )
    }

    fun ago(days: Long, hours: Long = 0): Instant =
        now.minus(days, ChronoUnit.DAYS).minus(hours, ChronoUnit.HOURS)

    val income = RecordableTransactionCategory.INCOME
    val expense = RecordableTransactionCategory.EXPENSE

    // Compte Courant: balance built entirely from its recent recorded activity (no opening
    // deposit), so it doubles as the "recent transactions" example on the accounts screen. Two
    // months of a typical routine: monthly salary and rent, weekly groceries, fuel, small outings.
    recorded(checking.id, 460, expense, ExpenseSubcategory.GROCERIES, "Boulangerie", ago(0, 2))
    recorded(checking.id, 3800, expense, null, "Restaurant", ago(1, 5))
    recorded(checking.id, 5950, expense, ExpenseSubcategory.HAIRDRESSER, "Coiffeur", ago(2))
    recorded(checking.id, 7025, expense, ExpenseSubcategory.FUEL, "Essence", ago(3))
    recorded(checking.id, 18000, expense, ExpenseSubcategory.GROCERIES, "Courses", ago(4))
    recorded(checking.id, 245000, income, IncomeSubcategory.SALARY, "Salaire", ago(5))
    recorded(checking.id, 6435, expense, ExpenseSubcategory.GROCERIES, "Courses", ago(6))
    recorded(checking.id, 1399, expense, null, "Abonnement streaming", ago(7))
    recorded(checking.id, 2640, income, IncomeSubcategory.REFUND, "Remboursement mutuelle", ago(8))
    recorded(checking.id, 5210, expense, ExpenseSubcategory.FUEL, "Essence", ago(9))
    recorded(checking.id, 1780, expense, null, "Pharmacie", ago(11))
    recorded(checking.id, 9275, expense, ExpenseSubcategory.GROCERIES, "Courses", ago(12))
    recorded(checking.id, 2400, expense, null, "Cinéma", ago(14))
    recorded(checking.id, 72000, expense, null, "Loyer", ago(16))
    recorded(checking.id, 7820, expense, ExpenseSubcategory.GROCERIES, "Courses", ago(20))
    recorded(checking.id, 5000, income, IncomeSubcategory.GIFT, "Cadeau de Mamie", ago(23))
    recorded(checking.id, 6140, expense, ExpenseSubcategory.FUEL, "Essence", ago(25))
    recorded(checking.id, 1999, expense, null, "Abonnement téléphone", ago(27))
    recorded(checking.id, 10560, expense, ExpenseSubcategory.GROCERIES, "Courses", ago(29))
    recorded(checking.id, 72000, expense, null, "Loyer", ago(31))
    recorded(checking.id, 245000, income, IncomeSubcategory.SALARY, "Salaire", ago(35))
    recorded(checking.id, 8890, expense, ExpenseSubcategory.GROCERIES, "Courses", ago(38))
    recorded(checking.id, 5500, expense, ExpenseSubcategory.HAIRDRESSER, "Coiffeur", ago(40))
    recorded(checking.id, 4875, expense, ExpenseSubcategory.FUEL, "Essence", ago(42))
    recorded(checking.id, 4650, expense, null, "Restaurant", ago(45))

    // Portefeuille Espèces: an opening float, two cash withdrawals from Compte Courant (modeled
    // as transfers, not expenses — the money isn't spent, just moved) and small everyday spending.
    deposit(cash.id, 10550, ago(200))
    transferred(checking.id, cash.id, 4000, "Retrait espèces", ago(30))
    transferred(checking.id, cash.id, 2000, "Retrait espèces", ago(1))
    recorded(cash.id, 1240, expense, ExpenseSubcategory.GROCERIES, "Marché", ago(2))
    recorded(cash.id, 350, expense, null, "Café", ago(5))
    recorded(cash.id, 600, expense, null, "Parking", ago(9))
    recorded(cash.id, 820, expense, ExpenseSubcategory.GROCERIES, "Boulangerie", ago(13))
    recorded(cash.id, 500, expense, null, "Pourboire", ago(19))
    recorded(cash.id, 3000, income, IncomeSubcategory.REFUND, "Remboursement ami", ago(21))
    recorded(cash.id, 1580, expense, ExpenseSubcategory.GROCERIES, "Marché", ago(26))

    // Ancien Compte Joint (archived): 850,00 + 45,00 - 120,00 - 60,00 = 715,00, all moved to
    // Compte Courant when it was closed.
    deposit(oldJoint.id, 85000, ago(520))
    recorded(oldJoint.id, 12000, expense, ExpenseSubcategory.GROCERIES, "Courses", ago(400))
    recorded(oldJoint.id, 4500, income, IncomeSubcategory.REFUND, "Remboursement", ago(380))
    recorded(oldJoint.id, 6000, expense, ExpenseSubcategory.FUEL, "Essence", ago(350))
    transferred(oldJoint.id, checking.id, 71500, "Clôture du compte", ago(300))
}
