package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.port.input.SetBudgetCommand
import com.kyovo.cents.ui.transaction.GROCERIES_SUBCATEGORY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.YearMonth

/**
 * The form that sets the limit of one subcategory for one month: a single amount. Like the other
 * amount forms it keeps the raw text (parsed only in `submit`, so a half-typed "12," is never an error
 * while typing) and it is stricter than the domain about zero — a budget of nothing is refused.
 */
class BudgetFormStateTest
{
    private val september = YearMonth.of(2026, 9)

    private fun aRow(limit: Long? = null, spent: Long = 0) =
        BudgetRow(GROCERIES_SUBCATEGORY, limit?.let { BudgetProgress(Money(it), Money(spent)) })

    @Test
    fun `setting a budget already in force pre-fills its limit`()
    {
        // WHEN
        val form = BudgetFormState.setting(aRow(limit = 30_000, spent = 12_000), september)

        // THEN what is spent does not matter here, only the limit that is being changed
        assertThat(form.limitText).isEqualTo("300,00")
        assertThat(form.subcategory).isEqualTo(GROCERIES_SUBCATEGORY)
        assertThat(form.month).isEqualTo(september)
    }

    @Test
    fun `setting a budget for a subcategory that has none starts with an empty field`()
    {
        // WHEN
        val form = BudgetFormState.setting(aRow(limit = null), september)

        // THEN
        assertThat(form.limitText).isEmpty()
    }

    @ParameterizedTest(name = "\"{0}\" is {1} cents")
    // The pipe separates the columns: a comma is the decimal separator of what is typed.
    @CsvSource(delimiter = '|', value = ["450 | 45000", "450. | 45000", "450.5 | 45050", "450,50 | 45050", "0,01 | 1", "300 | 30000"])
    fun `submits the limit for the subcategory and the month shown`(text: String, cents: Long)
    {
        // GIVEN
        val form = BudgetFormState.setting(aRow(), september).withLimit(text.trim())

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            BudgetSubmission.Set(SetBudgetCommand(GROCERIES_SUBCATEGORY.id, september, Money(cents)))
        )
    }

    // A budget of nothing would be "overspent" from the first cent: refused here as the domain refuses it.
    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = ["", " ", "0", "0,00", "0.0", ",", "12,345"])
    fun `refuses a limit that is empty, zero or not an amount`(text: String)
    {
        // GIVEN text put in the field directly, as a pre-filled or pasted value could be
        val form = BudgetFormState(GROCERIES_SUBCATEGORY, september, text)

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(BudgetSubmission.Invalid)
    }

    @Test
    fun `typing accepts digits with one separator and two decimals, and ignores anything else`()
    {
        // GIVEN
        val form = BudgetFormState.setting(aRow(), september)

        // WHEN / THEN each keystroke that would leave the shape of an amount is ignored
        assertThat(form.withLimit("12").limitText).isEqualTo("12")
        assertThat(form.withLimit("12,").limitText).isEqualTo("12,")
        assertThat(form.withLimit("12,5").limitText).isEqualTo("12,5")
        assertThat(form.withLimit("12,50").limitText).isEqualTo("12,50")

        val typed = form.withLimit("12,50")
        assertThat(typed.withLimit("12,505")).isEqualTo(typed)      // a third decimal
        assertThat(typed.withLimit("12,50a")).isEqualTo(typed)      // a letter
        assertThat(typed.withLimit("12,5,0")).isEqualTo(typed)      // a second separator
        assertThat(typed.withLimit("-12,50")).isEqualTo(typed)      // a sign
        assertThat(typed.withLimit("1234567890")).isEqualTo(typed)  // ten digits: beyond what a Long of cents holds safely
    }

    @Test
    fun `clearing the field is allowed while typing, and only refused on submit`()
    {
        // GIVEN
        val form = BudgetFormState.setting(aRow(limit = 30_000), september)

        // WHEN
        val cleared = form.withLimit("")

        // THEN
        assertThat(cleared.limitText).isEmpty()
        assertThat(cleared.submit()).isEqualTo(BudgetSubmission.Invalid)
    }
}
