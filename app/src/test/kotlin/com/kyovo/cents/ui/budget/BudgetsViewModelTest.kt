package com.kyovo.cents.ui.budget

import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.InvalidBudgetSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.BudgetProjection
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.domain.port.input.SetBudgetCommand
import com.kyovo.cents.domain.port.input.SetBudgetUseCase
import com.kyovo.cents.ui.transaction.FUEL_SUBCATEGORY
import com.kyovo.cents.ui.transaction.GROCERIES_SUBCATEGORY
import com.kyovo.cents.ui.transaction.SALARY_SUBCATEGORY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** The subcategories, which a test can change while the view model is watching. */
private class FakeSubcategories(initial: List<Subcategory>) : ListSubcategoriesUseCase
{
    val all = MutableStateFlow(initial)

    override fun observe(kind: RecordableTransactionCategory?): Flow<List<Subcategory>> =
        all.map { list -> list.filter { kind == null || it.kind == kind } }
}

/** The progress of each month, which a test can change while the view model is watching. */
private class FakeProgress : GetBudgetProgressUseCase
{
    val byMonth = MutableStateFlow<Map<YearMonth, Map<SubcategoryId, BudgetProgress>>>(emptyMap())

    /** The months the view model asked about, in order. */
    val asked = mutableListOf<YearMonth>()

    override fun observeAll(month: YearMonth): Flow<Map<SubcategoryId, BudgetProgress>>
    {
        asked += month
        return byMonth.map { it[month] ?: emptyMap() }.distinctUntilChanged()
    }

    override fun observe(subcategoryId: SubcategoryId, month: YearMonth): Flow<BudgetProgress?> =
        observeAll(month).map { it[subcategoryId] }
}

/** What was spent per subcategory in each month, which a test can change while the view model is watching. */
private class FakeSpendingBreakdown : GetSpendingBreakdownUseCase
{
    val byMonth = MutableStateFlow<Map<YearMonth, Map<SubcategoryId?, Money>>>(emptyMap())

    override fun observe(month: YearMonth): Flow<Map<SubcategoryId?, Money>> =
        byMonth.map { it[month] ?: emptyMap() }.distinctUntilChanged()
}

/** Records what it is asked to set; can be told to refuse. */
private class RecordingSet : SetBudgetUseCase
{
    val commands = mutableListOf<SetBudgetCommand>()
    var failWith: RuntimeException? = null

    override suspend fun set(command: SetBudgetCommand): Budget
    {
        failWith?.let { throw it }
        commands += command
        return command.toBudget()
    }
}

/**
 * The state behind the budgets screens — the month shown, one row per expense subcategory with its progress,
 * and the form that sets a limit and an alert threshold. It survives configuration changes like the other
 * view models, and it is what both the budgets tab (to look) and the settings (to set) read.
 */
@ExtendWith(MainDispatcherExtension::class)
class BudgetsViewModelTest
{
    private val september = YearMonth.of(2026, 9)
    private val august = YearMonth.of(2026, 8)

    // "Today" is in the middle of September 2026, in UTC.
    private val clock = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC)

    private val subcategories = FakeSubcategories(listOf(GROCERIES_SUBCATEGORY, SALARY_SUBCATEGORY, FUEL_SUBCATEGORY))
    private val progress = FakeProgress()
    private val spendingBreakdown = FakeSpendingBreakdown()
    private val set = RecordingSet()

    // Built once the main dispatcher is in place (the extension installs it before each test): this view
    // model starts observing as soon as it exists, unlike the ones that only touch their scope to write.
    private lateinit var viewModel: BudgetsViewModel

    @BeforeEach
    fun createTheViewModel()
    {
        viewModel = BudgetsViewModel(
            listSubcategories = subcategories,
            getBudgetProgress = progress,
            getSpendingBreakdown = spendingBreakdown,
            setBudget = set,
            clock = clock,
        )
    }

    private val state get() = viewModel.uiState.value

    private fun progressOf(limit: Long, spent: Long, threshold: Int = 80) =
        BudgetProgress(Money(limit), Money(spent), AlertThreshold(threshold))

    private fun givenProgress(month: YearMonth, vararg entries: Pair<Subcategory, BudgetProgress>)
    {
        progress.byMonth.value = progress.byMonth.value + (month to entries.associate { it.first.id to it.second })
    }

    private fun givenSpending(month: YearMonth, vararg entries: Pair<Subcategory?, Long>)
    {
        spendingBreakdown.byMonth.value = spendingBreakdown.byMonth.value +
                (month to entries.associate { it.first?.id to Money(it.second) })
    }

    @Test
    fun `starts on the current month, on the overview tab, with nothing open`()
    {
        assertThat(state.selector).isEqualTo(MonthSelector(september))
        assertThat(state.isCurrentMonth).isTrue()
        assertThat(state.tab).isEqualTo(BudgetTab.OVERVIEW)
        assertThat(state.form).isNull()
        assertThat(state.error).isNull()
    }

    @Test
    fun `selecting a tab switches it, and it survives moving to another month`()
    {
        // WHEN
        viewModel.selectTab(BudgetTab.BUDGETS)

        // THEN
        assertThat(state.tab).isEqualTo(BudgetTab.BUDGETS)

        // AND a change of month closes the form but leaves the tab as it was
        viewModel.nextMonth()
        assertThat(state.tab).isEqualTo(BudgetTab.BUDGETS)
    }

    @Test
    fun `the overview shows the spending breakdown of the month shown, budget or no budget`()
    {
        // GIVEN groceries and fuel both spent this month, though only groceries has a budget
        givenSpending(september, GROCERIES_SUBCATEGORY to 4_500L, FUEL_SUBCATEGORY to 2_000L)

        // THEN
        assertThat(state.breakdown).isEqualTo(
            spendingBreakdown(
                subcategories.all.value,
                mapOf(GROCERIES_SUBCATEGORY.id to Money(4_500), FUEL_SUBCATEGORY.id to Money(2_000)),
            )
        )
    }

    @Test
    fun `moving to another month shows the breakdown of that month`()
    {
        // GIVEN
        givenSpending(august, GROCERIES_SUBCATEGORY to 1_000L)
        givenSpending(september, GROCERIES_SUBCATEGORY to 5_000L)

        // WHEN
        viewModel.previousMonth()

        // THEN
        assertThat(state.breakdown.total).isEqualTo(Money(1_000))
        viewModel.nextMonth()
        assertThat(state.breakdown.total).isEqualTo(Money(5_000))
    }

    @Test
    fun `lists the expense subcategories with the progress of the month shown`()
    {
        // GIVEN groceries has a budget in September, fuel has none, and an income never has a row
        val groceries = progressOf(limit = 30_000, spent = 12_000)
        givenProgress(september, GROCERIES_SUBCATEGORY to groceries)

        // THEN "today" (the 15th) also gives groceries its projection to the month's end
        assertThat(state.rows).isEqualTo(
            listOf(
                BudgetRow(GROCERIES_SUBCATEGORY, groceries, BudgetProjection(Money(30_000), Money(12_000), Money(24_000))),
                BudgetRow(FUEL_SUBCATEGORY, null),
            )
        )
    }

    @Test
    fun `follows the progress and the subcategories as they change`()
    {
        // GIVEN
        givenProgress(september, GROCERIES_SUBCATEGORY to progressOf(30_000, 12_000))

        // WHEN an expense is recorded, and a subcategory is deleted
        givenProgress(september, GROCERIES_SUBCATEGORY to progressOf(30_000, 27_000))
        subcategories.all.value = listOf(GROCERIES_SUBCATEGORY, SALARY_SUBCATEGORY)

        // THEN the screen needs no refresh, and the projection follows the new amount spent
        assertThat(state.rows).isEqualTo(
            listOf(
                BudgetRow(
                    GROCERIES_SUBCATEGORY,
                    progressOf(30_000, 27_000),
                    BudgetProjection(Money(30_000), Money(27_000), Money(54_000)),
                )
            )
        )
        assertThat(state.rows.single().status).isEqualTo(BudgetStatus.CLOSE_TO_LIMIT)
        assertThat(state.rows.single().projection?.isPacingToExceed).isTrue()
    }

    @Test
    fun `moving to another month shows the rows of that month`()
    {
        // GIVEN a budget in August and another in September
        givenProgress(august, GROCERIES_SUBCATEGORY to progressOf(25_000, 5_000))
        givenProgress(september, GROCERIES_SUBCATEGORY to progressOf(30_000, 12_000))

        // WHEN
        viewModel.previousMonth()

        // THEN a month other than the current one has no projection, whatever its progress
        assertThat(state.selector.month).isEqualTo(august)
        assertThat(state.isCurrentMonth).isFalse()
        assertThat(state.rows.first().progress).isEqualTo(progressOf(25_000, 5_000))
        assertThat(state.rows.first().projection).isNull()

        // AND forward again, and beyond: there are no bounds
        viewModel.nextMonth()
        assertThat(state.rows.first().progress).isEqualTo(progressOf(30_000, 12_000))
        viewModel.nextMonth()
        assertThat(state.selector.month).isEqualTo(YearMonth.of(2026, 10))
        assertThat(state.rows.map { it.progress }).containsOnlyNulls()
    }

    @Test
    fun `can always return to the current month`()
    {
        // GIVEN far from today
        repeat(5) { viewModel.previousMonth() }
        assertThat(state.isCurrentMonth).isFalse()

        // WHEN
        viewModel.goToCurrentMonth()

        // THEN
        assertThat(state.selector.month).isEqualTo(september)
        assertThat(state.isCurrentMonth).isTrue()
    }

    // ------------------------------------------------------------------ the form

    @Test
    fun `opening a row starts its form for the month shown, pre-filled with the budget in force`()
    {
        // GIVEN
        givenProgress(september, GROCERIES_SUBCATEGORY to progressOf(30_000, 12_000, threshold = 60))

        // WHEN
        viewModel.openForm(state.rows.first())

        // THEN
        assertThat(state.form).isEqualTo(BudgetFormState(GROCERIES_SUBCATEGORY, september, "300,00", 60))
        assertThat(state.error).isNull()
    }

    @Test
    fun `opening a row that has no budget starts an empty form with the default threshold`()
    {
        // WHEN
        viewModel.openForm(state.rows.first())

        // THEN
        assertThat(state.form).isEqualTo(BudgetFormState(GROCERIES_SUBCATEGORY, september, "", 80))
    }

    @Test
    fun `typing the limit and moving the slider change the form`()
    {
        // GIVEN
        viewModel.openForm(state.rows.first())

        // WHEN
        viewModel.updateLimit("450,5")
        viewModel.updateThreshold(63)

        // THEN what was typed is filtered, and the slider is brought to its steps
        assertThat(state.form?.limitText).isEqualTo("450,5")
        assertThat(state.form?.thresholdPercent).isEqualTo(65)
        viewModel.updateLimit("450,5x")
        assertThat(state.form?.limitText).isEqualTo("450,5")
    }

    @Test
    fun `editing does nothing while no form is open`()
    {
        // WHEN
        viewModel.updateLimit("450")
        viewModel.updateThreshold(60)

        // THEN
        assertThat(state.form).isNull()
    }

    @Test
    fun `closing the form discards it`()
    {
        // GIVEN
        viewModel.openForm(state.rows.first())
        viewModel.updateLimit("450")

        // WHEN
        viewModel.closeForm()

        // THEN
        assertThat(state.form).isNull()
        assertThat(state.error).isNull()
    }

    @Test
    fun `moving to another month closes the form, which was for the month left`()
    {
        // GIVEN
        viewModel.openForm(state.rows.first())

        // WHEN
        viewModel.nextMonth()

        // THEN
        assertThat(state.form).isNull()
    }

    // ------------------------------------------------------------------ saving

    @Test
    fun `saving sets the budget of the subcategory for the month, with its limit and threshold, and closes the form`()
    {
        // GIVEN
        viewModel.openForm(state.rows.first())
        viewModel.updateLimit("450")
        viewModel.updateThreshold(60)

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(set.commands).containsExactly(
            SetBudgetCommand(GROCERIES_SUBCATEGORY.id, september, Money(45_000), AlertThreshold(60))
        )
        assertThat(state.form).isNull()
        assertThat(state.error).isNull()
    }

    @Test
    fun `a limit that is empty or zero is refused, the form stays open and nothing is set`()
    {
        // GIVEN
        viewModel.openForm(state.rows.first())

        // WHEN
        viewModel.submit()
        viewModel.updateLimit("0")
        viewModel.submit()

        // THEN
        assertThat(state.error).isEqualTo(BudgetFormError.LIMIT_INVALID)
        assertThat(state.form).isNotNull()
        assertThat(set.commands).isEmpty()
    }

    @Test
    fun `an error goes away as soon as the form is edited again`()
    {
        // GIVEN a refused limit
        viewModel.openForm(state.rows.first())
        viewModel.submit()
        assertThat(state.error).isEqualTo(BudgetFormError.LIMIT_INVALID)

        // WHEN
        viewModel.updateLimit("450")

        // THEN
        assertThat(state.error).isNull()
    }

    // The subcategory can be deleted, or turn out not to be an expense, between opening the form and
    // saving: a domain refusal is an answer on screen, never a crash.
    @Test
    fun `a subcategory that is gone or not an expense is reported, and the form stays open`()
    {
        // GIVEN
        viewModel.openForm(state.rows.first())
        viewModel.updateLimit("450")

        for (refusal in listOf(SubcategoryNotFoundException(), InvalidBudgetSubcategoryException()))
        {
            // WHEN
            set.failWith = refusal
            viewModel.submit()

            // THEN
            assertThat(state.error).isEqualTo(BudgetFormError.SUBCATEGORY_UNAVAILABLE)
            assertThat(state.form).isNotNull()
        }
        assertThat(set.commands).isEmpty()
    }

    @Test
    fun `saving with no form open does nothing`()
    {
        // WHEN
        viewModel.submit()

        // THEN
        assertThat(set.commands).isEmpty()
        assertThat(state.error).isNull()
    }
}
