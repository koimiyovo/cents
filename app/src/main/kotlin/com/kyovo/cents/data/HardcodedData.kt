package com.kyovo.cents.data

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.ExpenseSubcategory
import com.kyovo.cents.domain.model.IncomeSubcategory
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionSubcategory
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
    )
    val savings = Account(
        id = AccountId(Uuid.random()),
        name = AccountName("Livret A (Sécurité)"),
        type = AccountType.SAVINGS,
        currency = eur,
        createdAt = now.minus(400, ChronoUnit.DAYS),
    )
    val cash = Account(
        id = AccountId(Uuid.random()),
        name = AccountName("Portefeuille Espèces"),
        type = AccountType.CHECKING,
        currency = eur,
        createdAt = now.minus(200, ChronoUnit.DAYS),
    )
    val envelope = Account(
        id = AccountId(Uuid.random()),
        name = AccountName("Enveloppe Projets"),
        type = AccountType.SAVINGS,
        currency = eur,
        createdAt = now.minus(90, ChronoUnit.DAYS),
    )

    listOf(checking, savings, cash, envelope).forEach(accountRepository::save)

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
        description: String,
        date: Instant,
    )
    {
        transactionRepository.save(
            Transaction.recorded(
                id = TransactionId(Uuid.random()),
                accountId = accountId,
                amount = Money(amountCents),
                category = category,
                subcategory = subcategory,
                description = TransactionDescription.of(description),
                date = date,
            ),
        )
    }

    // Compte Courant: balance built entirely from its recent recorded activity (no opening
    // deposit), so it doubles as the "recent transactions" example on the accounts screen.
    recorded(
        checking.id,
        245000,
        RecordableTransactionCategory.INCOME,
        IncomeSubcategory.SALARY,
        "Salaire",
        now.minus(5, ChronoUnit.DAYS)
    )
    recorded(
        checking.id,
        18000,
        RecordableTransactionCategory.EXPENSE,
        ExpenseSubcategory.GROCERIES,
        "Courses",
        now.minus(4, ChronoUnit.DAYS)
    )
    recorded(
        checking.id,
        7025,
        RecordableTransactionCategory.EXPENSE,
        ExpenseSubcategory.FUEL,
        "Essence",
        now.minus(3, ChronoUnit.DAYS)
    )
    recorded(
        checking.id,
        5950,
        RecordableTransactionCategory.EXPENSE,
        ExpenseSubcategory.HAIRDRESSER,
        "Coiffeur",
        now.minus(2, ChronoUnit.DAYS)
    )

    // Livret A / Enveloppe Projets: just their starting capital, no recent movement.
    deposit(savings.id, 240000, now.minus(400, ChronoUnit.DAYS))
    deposit(envelope.id, 35000, now.minus(90, ChronoUnit.DAYS))

    // Portefeuille Espèces: an opening float plus a recent withdrawal.
    deposit(cash.id, 10550, now.minus(200, ChronoUnit.DAYS))
    recorded(
        cash.id,
        2000,
        RecordableTransactionCategory.EXPENSE,
        null,
        "Retrait espèces",
        now.minus(1, ChronoUnit.DAYS)
    )
}
