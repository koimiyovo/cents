package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The plain space between the digits depends on the JDK, so labels are compared with ordinary spaces. */
private fun String?.plain() = this?.replace(Regex("[\\u00A0\\u202F]"), " ")

/**
 * The net of one day, shown at the right of its date in the Transactions tab. When every transaction of the day
 * is in one currency, it is added up in that currency. When the day mixes currencies it is added up in the main
 * currency, from the converted amounts, and shown as approximate; when one of them cannot be converted, nothing
 * is shown rather than a figure that leaves it out.
 */
class DayNetTest
{
    private val euros = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val dollars = AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
    private val francs = AccountId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private var number = 0

    private val currencies = mapOf(
        euros to AccountCurrency.EUR,
        dollars to AccountCurrency.USD,
        francs to AccountCurrency.XOF
    )

    private fun currencyOf(transaction: Transaction): AccountCurrency? = currencies[transaction.accountId]

    private fun aTransaction(
        accountId: AccountId,
        cents: Long,
        category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE
    ): Transaction
    {
        return Transaction.recorded(
            id = TransactionId(UUID(0, (++number).toLong())),
            accountId = accountId,
            amount = Money(cents),
            title = TransactionTitle("Test"),
            category = category,
            subcategory = null,
            description = null,
            date = Instant.parse("2026-10-12T10:00:00Z")
        )
    }

    private fun convertedById(vararg pairs: Pair<Transaction, Long>): Map<TransactionId, Transaction> =
        pairs.associate { (transaction, cents) -> transaction.id to transaction.withAmount(Money(cents)) }

    // --- A day in one currency --------------------------------------------------------------------------------

    @Test
    fun `adds up a day in one currency, in that currency`()
    {
        val day = listOf(
            aTransaction(euros, 3_000),
            aTransaction(euros, 1_000, RecordableTransactionCategory.INCOME)
        )

        assertThat(dayNet(day, ::currencyOf, emptyMap())).isEqualTo(DayNet.InCurrency(-2_000, AccountCurrency.EUR))
    }

    @Test
    fun `adds up a day in a foreign currency in that currency, without converting`()
    {
        val day = listOf(aTransaction(francs, 5_000), aTransaction(francs, 2_500))

        assertThat(dayNet(day, ::currencyOf, emptyMap())).isEqualTo(DayNet.InCurrency(-7_500, AccountCurrency.XOF))
    }

    // --- A day that mixes currencies --------------------------------------------------------------------------

    @Test
    fun `adds up a day that mixes currencies in the main currency, from the converted amounts`()
    {
        // GIVEN 30,00 EUR and 5,00 USD (4,47 EUR) of expenses
        val lunch = aTransaction(euros, 3_000)
        val coffee = aTransaction(dollars, 500)

        // WHEN
        val net = dayNet(listOf(lunch, coffee), ::currencyOf, convertedById(lunch to 3_000, coffee to 447))

        // THEN the dollars count at their converted amount, not as 5,00
        assertThat(net).isEqualTo(DayNet.Converted(-3_447))
    }

    @Test
    fun `keeps the sign of each movement in a day that mixes currencies`()
    {
        // GIVEN a 100,00 USD income (89,38 EUR) and a 20,00 EUR expense
        val pay = aTransaction(dollars, 10_000, RecordableTransactionCategory.INCOME)
        val lunch = aTransaction(euros, 2_000)

        // WHEN
        val net = dayNet(listOf(pay, lunch), ::currencyOf, convertedById(pay to 8_938, lunch to 2_000))

        // THEN
        assertThat(net).isEqualTo(DayNet.Converted(6_938))
    }

    @Test
    fun `shows nothing when a transaction of a day that mixes currencies cannot be converted`()
    {
        // GIVEN the Canadian-dollar-like case: the dollar transaction has no converted version
        val lunch = aTransaction(euros, 3_000)
        val coffee = aTransaction(dollars, 500)

        // WHEN
        val net = dayNet(listOf(lunch, coffee), ::currencyOf, convertedById(lunch to 3_000))

        // THEN a total that leaves one out would be wrong, so there is none
        assertThat(net).isEqualTo(DayNet.None)
    }

    @Test
    fun `a day in one currency does not need any conversion`()
    {
        val day = listOf(aTransaction(dollars, 500))

        assertThat(dayNet(day, ::currencyOf, emptyMap())).isEqualTo(DayNet.InCurrency(-500, AccountCurrency.USD))
    }

    // --- Nothing to show --------------------------------------------------------------------------------------

    @Test
    fun `shows nothing for a day without transactions`()
    {
        assertThat(dayNet(emptyList(), ::currencyOf, emptyMap())).isEqualTo(DayNet.None)
    }

    @Test
    fun `shows nothing when the currency of a transaction is unknown`()
    {
        val unknownAccount = AccountId(UUID.fromString("99999999-9999-9999-9999-999999999999"))
        val day = listOf(aTransaction(euros, 3_000), aTransaction(unknownAccount, 500))

        assertThat(dayNet(day, ::currencyOf, emptyMap())).isEqualTo(DayNet.None)
    }

    // --- The text -----------------------------------------------------------------------------------------------

    @Test
    fun `writes a day in one currency with its sign and its symbol`()
    {
        assertThat(dayNetLabel(DayNet.InCurrency(-2_000, AccountCurrency.EUR), AccountCurrency.EUR).plain())
            .isEqualTo("-20,00 €")
        assertThat(dayNetLabel(DayNet.InCurrency(150_000, AccountCurrency.XOF), AccountCurrency.EUR).plain())
            .startsWith("+150 000").contains("CFA")
    }

    @Test
    fun `writes a day that mixes currencies as approximate, in the main currency`()
    {
        assertThat(dayNetLabel(DayNet.Converted(-3_447), AccountCurrency.EUR).plain()).isEqualTo("≈ -34,47 €")
        assertThat(dayNetLabel(DayNet.Converted(6_938), AccountCurrency.USD).plain()).startsWith("≈ +69,38")
    }

    @Test
    fun `writes nothing for a day without a net`()
    {
        assertThat(dayNetLabel(DayNet.None, AccountCurrency.EUR)).isNull()
    }

    @Test
    fun `a day that nets to zero has no sign`()
    {
        assertThat(dayNetLabel(DayNet.InCurrency(0, AccountCurrency.EUR), AccountCurrency.EUR).plain())
            .isEqualTo("0,00 €")
    }
}
