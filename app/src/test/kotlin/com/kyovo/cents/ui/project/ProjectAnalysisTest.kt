package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.ui.budget.SpendingSlice
import com.kyovo.cents.ui.budget.SpendingSliceLabel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val JAPAN_ID = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666661"))
private val KITCHEN_ID = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666662"))
private val ACCOUNT = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))

private val FLIGHTS = Subcategory(
    SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555551")),
    RecordableTransactionCategory.EXPENSE, SubcategoryName("Transport"), Emoji("✈️"),
)
private val FOOD = Subcategory(
    SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555552")),
    RecordableTransactionCategory.EXPENSE, SubcategoryName("Restaurants"), null,
)

private fun aProject(id: ProjectId, name: String, target: Long? = null, threshold: Int = 80) =
    Project(id, ProjectName(name), null, target?.let { Money(it) }, AlertThreshold(threshold))

private var counter = 0

private fun anExpense(
    cents: Long,
    at: String,
    project: ProjectId? = JAPAN_ID,
    subcategory: Subcategory? = null,
    title: String = "Achat",
) = Transaction.recorded(
    TransactionId(UUID.fromString("33333333-3333-3333-3333-%012d".format(++counter))),
    ACCOUNT, Money(cents), TransactionTitle(title), RecordableTransactionCategory.EXPENSE,
    subcategory, null, Instant.parse(at), project,
)

private fun aRefund(cents: Long, at: String, project: ProjectId? = JAPAN_ID) = Transaction.recorded(
    TransactionId(UUID.fromString("33333333-3333-3333-3333-%012d".format(++counter))),
    ACCOUNT, Money(cents), TransactionTitle("Remboursement"), RecordableTransactionCategory.INCOME,
    null, null, Instant.parse(at), project,
)

/**
 * What the "Projets" tab of the Budget screen works out for one project, from its transactions: which project
 * it opens on, where its money went, and its biggest expenses.
 * All of it is computed from the transactions, never stored.
 */
class ProjectAnalysisTest
{
    private val japan = aProject(JAPAN_ID, "Voyage au Japon", target = 300_000)
    private val kitchen = aProject(KITCHEN_ID, "Travaux cuisine")

    // ------------------------------------------------------------------ which project it opens on

    @Test
    fun `it opens on the project of the last transaction`()
    {
        val transactions = listOf(
            anExpense(1_000, "2026-09-01T10:00:00Z", JAPAN_ID),
            anExpense(1_000, "2026-09-20T10:00:00Z", KITCHEN_ID),
            anExpense(1_000, "2026-09-10T10:00:00Z", JAPAN_ID),
        )

        assertThat(initialProjectChoice(listOf(japan, kitchen), transactions)).isEqualTo(kitchen)
    }

    @Test
    fun `the last transaction is the latest by date, not the last saved`()
    {
        // GIVEN the Japan expense was saved after the kitchen one but dated earlier
        val transactions = listOf(
            anExpense(1_000, "2026-09-20T10:00:00Z", KITCHEN_ID),
            anExpense(1_000, "2026-09-01T10:00:00Z", JAPAN_ID),
        )

        assertThat(initialProjectChoice(listOf(japan, kitchen), transactions)).isEqualTo(kitchen)
    }

    @Test
    fun `a refund counts as a transaction too`()
    {
        val transactions = listOf(
            anExpense(1_000, "2026-09-01T10:00:00Z", KITCHEN_ID),
            aRefund(500, "2026-09-05T10:00:00Z", JAPAN_ID),
        )

        assertThat(initialProjectChoice(listOf(japan, kitchen), transactions)).isEqualTo(japan)
    }

    @Test
    fun `transactions in no project, or in a project that no longer exists, do not count`()
    {
        val transactions = listOf(
            anExpense(1_000, "2026-09-01T10:00:00Z", JAPAN_ID),
            anExpense(1_000, "2026-09-30T10:00:00Z", null),
            anExpense(1_000, "2026-09-29T10:00:00Z", ProjectId(UUID.randomUUID())),
        )

        assertThat(initialProjectChoice(listOf(japan, kitchen), transactions)).isEqualTo(japan)
    }

    @Test
    fun `without any transaction in a project it opens on the first project`()
    {
        assertThat(initialProjectChoice(listOf(kitchen, japan), emptyList())).isEqualTo(kitchen)
        assertThat(initialProjectChoice(listOf(kitchen, japan), listOf(anExpense(1_000, "2026-09-01T10:00:00Z", null))))
            .isEqualTo(kitchen)
    }

    @Test
    fun `without any project there is nothing to open on`()
    {
        assertThat(initialProjectChoice(emptyList(), listOf(anExpense(1_000, "2026-09-01T10:00:00Z")))).isNull()
    }

    // ------------------------------------------------------------------ the user's choice

    @Test
    fun `a project the user chose is kept, whatever the last transaction`()
    {
        val transactions = listOf(anExpense(1_000, "2026-09-20T10:00:00Z", KITCHEN_ID))

        assertThat(resolveSelectedProject(JAPAN_ID, listOf(japan, kitchen), transactions)).isEqualTo(japan)
    }

    @Test
    fun `a choice that no longer exists falls back to the default`()
    {
        val transactions = listOf(anExpense(1_000, "2026-09-20T10:00:00Z", KITCHEN_ID))

        assertThat(resolveSelectedProject(ProjectId(UUID.randomUUID()), listOf(japan, kitchen), transactions))
            .isEqualTo(kitchen)
    }

    @Test
    fun `no choice yet is the default`()
    {
        val transactions = listOf(anExpense(1_000, "2026-09-20T10:00:00Z", KITCHEN_ID))

        assertThat(resolveSelectedProject(null, listOf(japan, kitchen), transactions)).isEqualTo(kitchen)
    }

    // ------------------------------------------------------------------ the project just created

    // A new project has no transaction, so the default choice would never land on it: after the user creates one
    // from this tab, the tab shows it. The ids known when they asked tell which one is new.
    @Test
    fun `the project that was not there when the user asked is the new one`()
    {
        assertThat(newlyCreatedProject(setOf(JAPAN_ID), listOf(japan, kitchen))).isEqualTo(kitchen)
    }

    @Test
    fun `without a new project there is nothing to choose`()
    {
        assertThat(newlyCreatedProject(setOf(JAPAN_ID, KITCHEN_ID), listOf(japan, kitchen))).isNull()
    }

    @Test
    fun `a project that is gone since does not count`()
    {
        assertThat(newlyCreatedProject(setOf(JAPAN_ID, KITCHEN_ID), listOf(japan))).isNull()
    }

    // Two at once cannot be one creation from here (another screen added one meanwhile): no guess.
    @Test
    fun `several new projects at once are not guessed between`()
    {
        assertThat(newlyCreatedProject(emptySet(), listOf(japan, kitchen))).isNull()
    }

    // ------------------------------------------------------------------ where the money went

    @Test
    fun `the pie splits the expenses by subcategory, the biggest first`()
    {
        val transactions = listOf(
            anExpense(30_000, "2026-09-01T10:00:00Z", subcategory = FLIGHTS),
            anExpense(10_000, "2026-09-02T10:00:00Z", subcategory = FOOD),
            anExpense(20_000, "2026-09-03T10:00:00Z", subcategory = FLIGHTS),
        )

        val breakdown = projectBreakdown(transactions, listOf(FLIGHTS, FOOD))

        assertThat(breakdown.spending.total).isEqualTo(Money(60_000))
        assertThat(breakdown.spending.slices.map { it.label to it.amount }).containsExactly(
            SpendingSliceLabel.Named("Transport", "✈️") to Money(50_000),
            SpendingSliceLabel.Named("Restaurants", "💳") to Money(10_000),
        )
        assertThat(breakdown.spending.slices.first().fraction).isCloseTo(50_000f / 60_000f, Offset.offset(0.0001f))
    }

    @Test
    fun `expenses without a subcategory form their own slice`()
    {
        val transactions = listOf(
            anExpense(5_000, "2026-09-01T10:00:00Z"),
            anExpense(1_000, "2026-09-02T10:00:00Z", subcategory = FOOD),
        )

        val slices: List<SpendingSlice> = projectBreakdown(transactions, listOf(FOOD)).spending.slices

        assertThat(slices.map { it.label }).containsExactly(
            SpendingSliceLabel.Uncategorized,
            SpendingSliceLabel.Named("Restaurants", "💳"),
        )
    }

    // The refunds are told apart rather than netted: a refund carries an income subcategory (or none), which is
    // never the expense subcategory it pays back, so there is nothing to subtract it from.
    @Test
    fun `the refunds are kept apart and do not enter the pie`()
    {
        val transactions = listOf(
            anExpense(40_000, "2026-09-01T10:00:00Z", subcategory = FLIGHTS),
            aRefund(5_000, "2026-09-05T10:00:00Z"),
            aRefund(2_500, "2026-09-06T10:00:00Z"),
        )

        val breakdown = projectBreakdown(transactions, listOf(FLIGHTS))

        assertThat(breakdown.spending.total).isEqualTo(Money(40_000))
        assertThat(breakdown.spending.slices).hasSize(1)
        assertThat(breakdown.refunds).isEqualTo(Money(7_500))
    }

    @Test
    fun `a project without transactions has an empty pie and no refunds`()
    {
        val breakdown = projectBreakdown(emptyList(), listOf(FLIGHTS))

        assertThat(breakdown.spending.slices).isEmpty()
        assertThat(breakdown.refunds).isEqualTo(Money(0))
    }

    @Test
    fun `a project with only refunds has an empty pie`()
    {
        val breakdown = projectBreakdown(listOf(aRefund(5_000, "2026-09-05T10:00:00Z")), emptyList())

        assertThat(breakdown.spending.slices).isEmpty()
        assertThat(breakdown.refunds).isEqualTo(Money(5_000))
    }

    // ------------------------------------------------------------------ the biggest expenses

    @Test
    fun `the biggest expenses come first, up to the limit`()
    {
        val small = anExpense(1_000, "2026-09-01T10:00:00Z", title = "Petit")
        val big = anExpense(90_000, "2026-09-02T10:00:00Z", title = "Gros")
        val medium = anExpense(20_000, "2026-09-03T10:00:00Z", title = "Moyen")

        assertThat(topExpenses(listOf(small, big, medium), limit = 2)).containsExactly(big, medium)
    }

    @Test
    fun `refunds are not expenses`()
    {
        val expense = anExpense(1_000, "2026-09-01T10:00:00Z")
        val refund = aRefund(90_000, "2026-09-02T10:00:00Z")

        assertThat(topExpenses(listOf(expense, refund), limit = 5)).containsExactly(expense)
    }

    @Test
    fun `equal amounts show the most recent first`()
    {
        val older = anExpense(5_000, "2026-09-01T10:00:00Z", title = "Avant")
        val newer = anExpense(5_000, "2026-09-09T10:00:00Z", title = "Après")

        assertThat(topExpenses(listOf(older, newer), limit = 5)).containsExactly(newer, older)
    }

    @Test
    fun `fewer expenses than the limit are all listed`()
    {
        val only = anExpense(1_000, "2026-09-01T10:00:00Z")

        assertThat(topExpenses(listOf(only), limit = 5)).containsExactly(only)
        assertThat(topExpenses(emptyList(), limit = 5)).isEmpty()
    }

    @Test
    fun `the default limit is five`()
    {
        val seven = (1..7).map { anExpense(it * 1_000L, "2026-09-0${it}T10:00:00Z") }

        assertThat(topExpenses(seven)).hasSize(5)
        assertThat(topExpenses(seven).first().amount).isEqualTo(Money(7_000))
    }
}
