package com.kyovo.cents.ui.recurring

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
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

private fun aRecurringExpense(accountId: AccountId, subcategoryId: SubcategoryId? = null) = RecurringExpense(
    id = RecurringExpenseId(UUID.randomUUID()),
    accountId = accountId,
    amount = Money(1_500),
    title = TransactionTitle("Loyer"),
    subcategoryId = subcategoryId,
    description = null,
    frequency = RecurrenceFrequency.MONTHLY,
    interval = 1,
    startDate = TODAY,
)

class RecurringExpenseRowsTest
{
    @Test
    fun `a row resolves its account's and subcategory's names`()
    {
        // GIVEN
        val account = anAccount(AccountId(UUID.randomUUID()), "Compte courant")
        val subcategory =
            Subcategory(SubcategoryId(UUID.randomUUID()), RecordableTransactionCategory.EXPENSE, SubcategoryName("Logement"), null)
        val rule = aRecurringExpense(account.id, subcategory.id)

        // WHEN
        val rows = recurringExpenseRows(listOf(rule), listOf(account), listOf(subcategory))

        // THEN
        assertThat(rows).containsExactly(RecurringExpenseRow(rule, "Compte courant", "Logement"))
    }

    @Test
    fun `a rule with no subcategory resolves to none`()
    {
        // GIVEN
        val account = anAccount(AccountId(UUID.randomUUID()), "Compte courant")
        val rule = aRecurringExpense(account.id)

        // WHEN
        val rows = recurringExpenseRows(listOf(rule), listOf(account), emptyList())

        // THEN
        assertThat(rows.single().subcategoryName).isNull()
    }

    @Test
    fun `a rule whose account is gone resolves to an empty name rather than crashing`()
    {
        // GIVEN
        val rule = aRecurringExpense(AccountId(UUID.randomUUID()))

        // WHEN
        val rows = recurringExpenseRows(listOf(rule), emptyList(), emptyList())

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
