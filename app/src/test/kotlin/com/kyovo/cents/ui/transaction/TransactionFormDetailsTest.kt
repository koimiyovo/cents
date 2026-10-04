package com.kyovo.cents.ui.transaction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

/**
 * The description sits behind "Plus de détails". The form opens with it unfolded when there is
 * already one — an edited transaction — so the user doesn't have to hunt for what it already says.
 */
class TransactionFormDetailsTest
{
    private val empty = TransactionFormState(
        type = TransactionFormType.EXPENSE,
        accountId = null,
        date = Instant.parse("2026-09-23T12:00:00Z"),
    )

    @Test
    fun `a new form has no details to show`()
    {
        assertThat(empty.hasDetails).isFalse()
    }

    @Test
    fun `a subcategory is not a detail, it has its own field`()
    {
        assertThat(empty.copy(subcategory = GROCERIES_SUBCATEGORY).hasDetails).isFalse()
    }

    @Test
    fun `a description is a detail`()
    {
        assertThat(empty.copy(description = "Marché du samedi").hasDetails).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
    fun `a blank description is not`(description: String)
    {
        assertThat(empty.copy(description = description).hasDetails).isFalse()
    }

    @Test
    fun `the amount, the title and the account are not details`()
    {
        val form = empty.copy(amountText = "12,50", title = "Courses")

        assertThat(form.hasDetails).isFalse()
    }
}
