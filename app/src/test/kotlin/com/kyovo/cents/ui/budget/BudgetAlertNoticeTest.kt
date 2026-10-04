package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.UUID

private fun aSubcategory(name: String, emoji: String?) = Subcategory(
    SubcategoryId(UUID.randomUUID()),
    RecordableTransactionCategory.EXPENSE,
    SubcategoryName(name),
    emoji?.let { Emoji(it) },
)

private fun alertOn(subcategory: Subcategory, level: BudgetAlertLevel) =
    BudgetAlert(subcategory.id, YearMonth.of(2026, 9), level)

/** What the snackbar needs to word an alert: the subcategory's emoji and name, and the level. */
class BudgetAlertNoticeTest
{
    private val groceries = aSubcategory("Alimentation", "🛒")
    private val plain = aSubcategory("Divers", null)

    @Test
    fun `carries the subcategory's own emoji, its name and the level`()
    {
        val notice = budgetAlertNotice(
            alertOn(groceries, BudgetAlertLevel.CLOSE_TO_LIMIT),
            listOf(plain, groceries)
        )

        assertThat(notice).isEqualTo(
            BudgetAlertNotice(
                "🛒",
                "Alimentation",
                BudgetAlertLevel.CLOSE_TO_LIMIT
            )
        )
    }

    @Test
    fun `a subcategory without an emoji shows the default one of its kind`()
    {
        val notice = budgetAlertNotice(alertOn(plain, BudgetAlertLevel.OVER), listOf(plain))

        assertThat(notice!!.emoji).isEqualTo(defaultSubcategoryEmoji(RecordableTransactionCategory.EXPENSE))
        assertThat(notice.level).isEqualTo(BudgetAlertLevel.OVER)
    }

    @Test
    fun `an alert whose subcategory is gone says nothing`()
    {
        assertThat(
            budgetAlertNotice(
                alertOn(groceries, BudgetAlertLevel.OVER),
                listOf(plain)
            )
        ).isNull()
    }
}
