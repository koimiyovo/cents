package com.kyovo.cents.ui.recurring

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

private val NOW: Instant = Instant.parse("2026-09-27T10:00:00Z")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 27)

private fun anAccount(id: AccountId, name: String) = Account(
    id = id,
    name = AccountName(name),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = NOW,
)

private fun aRecurringTransaction(
    accountId: AccountId,
    subcategoryId: SubcategoryId? = null,
    category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
) = RecurringTransaction(
    id = RecurringTransactionId(UUID.randomUUID()),
    accountId = accountId,
    category = category,
    amount = Money(1_500),
    title = TransactionTitle("Loyer"),
    subcategoryId = subcategoryId,
    description = null,
    frequency = RecurrenceFrequency.MONTHLY,
    interval = 1,
    startDate = TODAY,
)

class RecurringTransactionRowsTest
{
    // The list says which way the money goes, like the transactions list: minus for an expense, plus for an income.
    @Test
    fun `an expense reads as negative and an income as positive`()
    {
        // GIVEN
        val accountId = AccountId(UUID.randomUUID())
        val rent = RecurringTransactionRow(aRecurringTransaction(accountId), "Compte courant", null)
        val salary = RecurringTransactionRow(
            aRecurringTransaction(accountId, category = RecordableTransactionCategory.INCOME), "Compte courant", null,
        )

        // WHEN / THEN
        assertThat(rent.signedAmountCents).isEqualTo(-1_500L)
        assertThat(salary.signedAmountCents).isEqualTo(1_500L)
    }

    @Test
    fun `a row resolves its account's and subcategory's names`()
    {
        // GIVEN
        val account = anAccount(AccountId(UUID.randomUUID()), "Compte courant")
        val subcategory =
            Subcategory(SubcategoryId(UUID.randomUUID()), RecordableTransactionCategory.EXPENSE, SubcategoryName("Logement"), null)
        val rule = aRecurringTransaction(account.id, subcategory.id)

        // WHEN
        val rows = recurringTransactionRows(listOf(rule), listOf(account), listOf(subcategory))

        // THEN
        assertThat(rows).containsExactly(RecurringTransactionRow(rule, "Compte courant", "Logement"))
    }

    @Test
    fun `a rule with no subcategory resolves to none`()
    {
        // GIVEN
        val account = anAccount(AccountId(UUID.randomUUID()), "Compte courant")
        val rule = aRecurringTransaction(account.id)

        // WHEN
        val rows = recurringTransactionRows(listOf(rule), listOf(account), emptyList())

        // THEN
        assertThat(rows.single().subcategoryName).isNull()
    }

    @Test
    fun `a rule whose account is gone resolves to an empty name rather than crashing`()
    {
        // GIVEN
        val rule = aRecurringTransaction(AccountId(UUID.randomUUID()))

        // WHEN
        val rows = recurringTransactionRows(listOf(rule), emptyList(), emptyList())

        // THEN
        assertThat(rows.single().accountName).isEmpty()
    }
}

class RecurrenceSummaryTest
{
    @Test
    fun `weekly at interval one omits the count`()
    {
        assertThat(recurrenceSummary(RecurrenceFrequency.WEEKLY, 1)).isEqualTo("Toutes les semaines")
    }

    @Test
    fun `weekly at another interval names the count`()
    {
        assertThat(recurrenceSummary(RecurrenceFrequency.WEEKLY, 2)).isEqualTo("Toutes les 2 semaines")
    }

    @Test
    fun `monthly at interval one omits the count`()
    {
        assertThat(recurrenceSummary(RecurrenceFrequency.MONTHLY, 1)).isEqualTo("Tous les mois")
    }

    @Test
    fun `monthly at another interval names the count`()
    {
        assertThat(recurrenceSummary(RecurrenceFrequency.MONTHLY, 3)).isEqualTo("Tous les 3 mois")
    }

    @Test
    fun `yearly at interval one omits the count`()
    {
        assertThat(recurrenceSummary(RecurrenceFrequency.YEARLY, 1)).isEqualTo("Tous les ans")
    }

    @Test
    fun `yearly at another interval names the count`()
    {
        assertThat(recurrenceSummary(RecurrenceFrequency.YEARLY, 2)).isEqualTo("Tous les 2 ans")
    }
}
