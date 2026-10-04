package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val NOW = Instant.parse("2026-09-23T12:00:00Z")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 23)
private val CHECKING = AccountId(UUID.randomUUID())

/**
 * "Répéter" in the transaction form: an income or an expense can be turned into a recurring rule, with the
 * same pace choices as the recurring form. The form's date is the rule's start date, so a date in the future
 * starts the rule then.
 */
class TransactionFormRepeatTest
{
    private val expense = TransactionFormState(
        type = TransactionFormType.EXPENSE,
        accountId = CHECKING,
        amountText = "12,50",
        title = "Loyer",
        date = NOW,
    )

    @Test
    fun `a new form does not repeat`()
    {
        assertThat(expense.repeat).isNull()
        assertThat(expense.isRepeating).isFalse()
    }

    @Test
    fun `turning repeat on starts monthly, every 1, with no end`()
    {
        val form = expense.withRepeat(true)

        assertThat(form.repeat).isEqualTo(RepeatSettings(RecurrenceFrequency.MONTHLY, "1", null))
        assertThat(form.isRepeating).isTrue()
    }

    @Test
    fun `turning repeat off forgets the pace that was chosen`()
    {
        val form = expense.withRepeat(true).withFrequency(RecurrenceFrequency.WEEKLY).withRepeat(false)

        assertThat(form.repeat).isNull()
        assertThat(form.withRepeat(true).repeat!!.frequency).isEqualTo(RecurrenceFrequency.MONTHLY)
    }

    @Test
    fun `an income can repeat too`()
    {
        val form = expense.withType(TransactionFormType.INCOME).withRepeat(true)

        assertThat(form.isRepeating).isTrue()
    }

    @Test
    fun `a transfer cannot repeat`()
    {
        val form = expense.copy(type = TransactionFormType.TRANSFER, toAccountId = AccountId(UUID.randomUUID()))
            .withRepeat(true)

        assertThat(form.repeat).isNull()
        assertThat(form.isRepeating).isFalse()
    }

    @Test
    fun `switching to a transfer drops the repetition`()
    {
        val form = expense.withRepeat(true).withType(TransactionFormType.TRANSFER)

        assertThat(form.repeat).isNull()
        assertThat(form.withType(TransactionFormType.EXPENSE).isRepeating).isFalse()
    }

    @Test
    fun `an edited transaction cannot be made to repeat`()
    {
        val editing = expense.copy(editingId = com.kyovo.cents.domain.model.TransactionId(UUID.randomUUID()))

        assertThat(editing.withRepeat(true).repeat).isNull()
        assertThat(editing.isRepeating).isFalse()
    }

    @Test
    fun `the pace can be changed once repeat is on`()
    {
        val form = expense.withRepeat(true)
            .withFrequency(RecurrenceFrequency.YEARLY)
            .withInterval("2")

        assertThat(form.repeat).isEqualTo(RepeatSettings(RecurrenceFrequency.YEARLY, "2", null))
    }

    @Test
    fun `changing the pace does nothing while repeat is off`()
    {
        assertThat(expense.withFrequency(RecurrenceFrequency.WEEKLY)).isEqualTo(expense)
        assertThat(expense.withInterval("3")).isEqualTo(expense)
        assertThat(expense.withEndDateEnabled(true, PARIS)).isEqualTo(expense)
    }

    @Test
    fun `changing the unit keeps the count`()
    {
        val form = expense.withRepeat(true).withInterval("2").withFrequency(RecurrenceFrequency.WEEKLY)

        assertThat(form.repeat!!.intervalText).isEqualTo("2")
    }

    @Test
    fun `the interval takes digits only, at most three`()
    {
        val form = expense.withRepeat(true).withInterval("12")

        assertThat(form.withInterval("1a").repeat!!.intervalText).isEqualTo("12")
        assertThat(form.withInterval("1234").repeat!!.intervalText).isEqualTo("12")
        assertThat(form.withInterval("").repeat!!.intervalText).isEqualTo("")
    }

    @Test
    fun `an end date starts on the form's day`()
    {
        val form = expense.withRepeat(true).withEndDateEnabled(true, PARIS)

        assertThat(form.repeat!!.endDate).isEqualTo(TODAY)
    }

    @Test
    fun `an end date can be removed`()
    {
        val form = expense.withRepeat(true).withEndDateEnabled(true, PARIS).withEndDateEnabled(false, PARIS)

        assertThat(form.repeat!!.endDate).isNull()
    }

    @Test
    fun `an end date can be picked`()
    {
        val form = expense.withRepeat(true).withEndDateEnabled(true, PARIS).withEndDate(LocalDate.of(2027, 1, 1))

        assertThat(form.repeat!!.endDate).isEqualTo(LocalDate.of(2027, 1, 1))
    }

    @Test
    fun `moving the day past the end date brings the end date along`()
    {
        val form = expense.withRepeat(true).withEndDateEnabled(true, PARIS)
            .withDay(LocalDate.of(2026, 10, 5), NOW, PARIS)

        assertThat(form.repeat!!.endDate).isEqualTo(LocalDate.of(2026, 10, 5))
    }

    @Test
    fun `moving the day before the end date leaves it alone`()
    {
        val form = expense.withRepeat(true).withEndDateEnabled(true, PARIS).withEndDate(LocalDate.of(2027, 1, 1))
            .withDay(LocalDate.of(2026, 10, 5), NOW, PARIS)

        assertThat(form.repeat!!.endDate).isEqualTo(LocalDate.of(2027, 1, 1))
    }

    @Test
    fun `a form that repeats submits a recurring rule starting on its day`()
    {
        val submission = expense.withRepeat(true).submit(PARIS)

        assertThat(submission).isEqualTo(
            FormSubmission.Repeat(
                CreateRecurringTransactionCommand(
                    accountId = CHECKING,
                    category = RecordableTransactionCategory.EXPENSE,
                    amount = Money(1_250),
                    title = TransactionTitle("Loyer"),
                    subcategoryId = null,
                    description = null,
                    frequency = RecurrenceFrequency.MONTHLY,
                    interval = 1,
                    startDate = TODAY,
                    endDate = null,
                ),
            ),
        )
    }

    @Test
    fun `a day in the future starts the rule then`()
    {
        val form = expense.withRepeat(true).withDay(LocalDate.of(2026, 12, 1), NOW, PARIS)

        val command = (form.submit(PARIS) as FormSubmission.Repeat).command

        assertThat(command.startDate).isEqualTo(LocalDate.of(2026, 12, 1))
    }

    @Test
    fun `the rule carries the subcategory, the description, the pace and the end date`()
    {
        val form = expense.copy(subcategory = GROCERIES_SUBCATEGORY, description = "Chaque mois")
            .withRepeat(true)
            .withFrequency(RecurrenceFrequency.WEEKLY)
            .withInterval("2")
            .withEndDateEnabled(true, PARIS)
            .withEndDate(LocalDate.of(2027, 3, 1))

        val command = (form.submit(PARIS) as FormSubmission.Repeat).command

        assertThat(command.subcategoryId).isEqualTo(GROCERIES_SUBCATEGORY.id)
        assertThat(command.description).isEqualTo(TransactionDescription.of("Chaque mois"))
        assertThat(command.frequency).isEqualTo(RecurrenceFrequency.WEEKLY)
        assertThat(command.interval).isEqualTo(2)
        assertThat(command.endDate).isEqualTo(LocalDate.of(2027, 3, 1))
    }

    @Test
    fun `an income submits an income rule`()
    {
        val form = expense.withType(TransactionFormType.INCOME).withRepeat(true)

        val command = (form.submit(PARIS) as FormSubmission.Repeat).command

        assertThat(command.category).isEqualTo(RecordableTransactionCategory.INCOME)
    }

    @Test
    fun `a blank or zero interval is refused`()
    {
        listOf("", "0", "00").forEach { text ->
            val submission = expense.withRepeat(true).withInterval(text).submit(PARIS)

            assertThat(submission).isEqualTo(FormSubmission.Invalid(setOf(FormError.INTERVAL_INVALID)))
        }
    }

    @Test
    fun `an end date before the day is refused`()
    {
        val form = expense.withRepeat(true).withEndDateEnabled(true, PARIS).withEndDate(LocalDate.of(2026, 9, 1))

        assertThat(form.submit(PARIS)).isEqualTo(FormSubmission.Invalid(setOf(FormError.END_BEFORE_START)))
    }

    @Test
    fun `the usual errors are still reported along with the repetition's`()
    {
        val form = expense.copy(amountText = "", title = "").withRepeat(true).withInterval("")

        val errors = (form.submit(PARIS) as FormSubmission.Invalid).errors

        assertThat(errors).containsExactlyInAnyOrder(
            FormError.AMOUNT_INVALID, FormError.TITLE_REQUIRED, FormError.INTERVAL_INVALID,
        )
    }

    @Test
    fun `a pace left in a form that does not repeat is not checked`()
    {
        // A transfer forgets the repetition, so a stale blank interval can never block it.
        val form = expense.withRepeat(true).withInterval("").withType(TransactionFormType.TRANSFER)
            .copy(toAccountId = AccountId(UUID.randomUUID()))

        assertThat(form.submit(PARIS)).isInstanceOf(FormSubmission.Transfer::class.java)
    }

    @Test
    fun `without repeat the form still records a plain transaction`()
    {
        assertThat(expense.submit(PARIS)).isInstanceOf(FormSubmission.Record::class.java)
    }
}
