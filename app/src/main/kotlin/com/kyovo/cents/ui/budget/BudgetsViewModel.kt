package com.kyovo.cents.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.InvalidBudgetSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.domain.port.input.SetBudgetUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/** What went wrong with the last attempt to save a budget. */
enum class BudgetFormError
{
    /** The limit is empty, zero, or not an amount. */
    LIMIT_INVALID,

    /** The subcategory was deleted, or is not an expense, since the form was opened. */
    SUBCATEGORY_UNAVAILABLE,
}

/** Which of the budgets screen's two tabs is shown: where the money went, or the budgets themselves.
 * [OVERVIEW] comes first — it is the one that answers "where do things stand" at a glance, with no
 * budget required to say something. */
enum class BudgetTab
{
    OVERVIEW,
    BUDGETS,
}

/**
 * Everything the budgets screens show: the month ([selector]), one [rows] entry per expense subcategory
 * with its progress for that month, the month's spending [breakdown] (the overview tab's pie), and the
 * [form] that sets a limit and an alert threshold — null while it is closed, so "is it open" and its content
 * can't disagree. [isCurrentMonth] tells the screen whether to offer a way back to today. [rows] and
 * [breakdown] are always those of the month in [selector].
 */
data class BudgetsUiState(
    val selector: MonthSelector,
    val rows: List<BudgetRow> = emptyList(),
    val breakdown: SpendingBreakdown = SpendingBreakdown(Money(0), emptyList()),
    val form: BudgetFormState? = null,
    val error: BudgetFormError? = null,
    val isCurrentMonth: Boolean = true,
    val tab: BudgetTab = BudgetTab.OVERVIEW,
)

/**
 * Holds the month shown and the limit form of the budgets across configuration changes, like the other
 * view models; the budgets tab reads the rows to see where things stand, and the settings screen uses the
 * form to set the limits. Nothing here touches Compose or Android. The rows are observed, so they follow
 * every budget and every expense by themselves; the write is launched in `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(
    listSubcategories: ListSubcategoriesUseCase,
    getBudgetProgress: GetBudgetProgressUseCase,
    getSpendingBreakdown: GetSpendingBreakdownUseCase,
    private val setBudget: SetBudgetUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel()
{
    /** What the user chose: the month, the tab and the form. Not what is read from the storage. */
    private data class Chosen(
        val selector: MonthSelector,
        val form: BudgetFormState? = null,
        val error: BudgetFormError? = null,
        val tab: BudgetTab = BudgetTab.OVERVIEW,
    )

    private val chosen = MutableStateFlow(Chosen(MonthSelector(currentMonth())))

    /**
     * The rows of each month asked for, tagged with that month. Only the month restarts the reading: typing
     * in the form must not query the storage again at each keystroke.
     */
    private val rowsOfTheMonth: Flow<Pair<YearMonth, List<BudgetRow>>> = chosen
        .map { it.selector.month }
        .distinctUntilChanged()
        .flatMapLatest { month ->
            combine(
                listSubcategories.observe(RecordableTransactionCategory.EXPENSE),
                getBudgetProgress.observeAll(month),
            ) { subcategories, progress ->
                month to budgetRows(subcategories, progress, month, LocalDate.now(clock))
            }
        }

    /** The overview tab's pie: where the month's money went, budget or no budget. */
    private val breakdownOfTheMonth: Flow<Pair<YearMonth, SpendingBreakdown>> = chosen
        .map { it.selector.month }
        .distinctUntilChanged()
        .flatMapLatest { month ->
            combine(
                listSubcategories.observe(RecordableTransactionCategory.EXPENSE),
                getSpendingBreakdown.observe(month),
            ) { subcategories, spent -> month to spendingBreakdown(subcategories, spent) }
        }

    // A state is only built once the rows and the breakdown are those of the month chosen: right after a
    // change of month, the previous month's data is still there for an instant, and must not appear under
    // the new month's name.
    val uiState: StateFlow<BudgetsUiState> = combine(chosen, rowsOfTheMonth, breakdownOfTheMonth)
    { chosen, (rowsMonth, rows), (breakdownMonth, breakdown) ->
        if (rowsMonth != chosen.selector.month || breakdownMonth != chosen.selector.month) null
        else BudgetsUiState(
            selector = chosen.selector,
            rows = rows,
            breakdown = breakdown,
            form = chosen.form,
            error = chosen.error,
            isCurrentMonth = chosen.selector.isCurrent(currentMonth()),
            tab = chosen.tab,
        )
    }
        .filterNotNull()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            BudgetsUiState(chosen.value.selector, isCurrentMonth = true),
        )

    private fun currentMonth(): YearMonth = YearMonth.now(clock)

    fun previousMonth()
    {
        moveTo { it.previous() }
    }

    fun nextMonth()
    {
        moveTo { it.next() }
    }

    fun goToCurrentMonth()
    {
        moveTo { MonthSelector(currentMonth()) }
    }

    fun selectTab(tab: BudgetTab)
    {
        chosen.update { it.copy(tab = tab) }
    }

    /** A form is for the month it was opened in, so leaving that month closes it. */
    private fun moveTo(target: (MonthSelector) -> MonthSelector)
    {
        chosen.update { it.copy(selector = target(it.selector), form = null, error = null) }
    }

    /** Opens the form on [row], for the month shown, pre-filled with the budget in force if there is one. */
    fun openForm(row: BudgetRow)
    {
        chosen.update { it.copy(form = BudgetFormState.setting(row, it.selector.month), error = null) }
    }

    /** An edit of the limit; what was reported about the last attempt is stale. */
    fun updateLimit(text: String)
    {
        edit { it.withLimit(text) }
    }

    /** The slider moved; it snaps to its steps. */
    fun updateThreshold(percent: Int)
    {
        edit { it.withThreshold(percent) }
    }

    private fun edit(change: (BudgetFormState) -> BudgetFormState)
    {
        chosen.update { if (it.form == null) it else it.copy(form = change(it.form), error = null) }
    }

    fun closeForm()
    {
        chosen.update { it.copy(form = null, error = null) }
    }

    /** Saves the form. On success it closes; otherwise the state says why not. */
    fun submit()
    {
        viewModelScope.launch { save() }
    }

    private suspend fun save()
    {
        val form = chosen.value.form ?: return
        when (val submission = form.submit())
        {
            is BudgetSubmission.Invalid ->
            {
                chosen.update { it.copy(error = BudgetFormError.LIMIT_INVALID) }
                return
            }

            is BudgetSubmission.Set ->
            {
                try
                {
                    setBudget.set(submission.command)
                } catch (_: SubcategoryNotFoundException)
                {
                    chosen.update { it.copy(error = BudgetFormError.SUBCATEGORY_UNAVAILABLE) }
                    return
                } catch (_: InvalidBudgetSubcategoryException)
                {
                    chosen.update { it.copy(error = BudgetFormError.SUBCATEGORY_UNAVAILABLE) }
                    return
                }
            }
        }

        // Close only once the write went through, and only the form that was saved: the user may have
        // moved to another month, and opened another form, while it was being written.
        chosen.update { if (it.form == form) it.copy(form = null, error = null) else it }
    }
}
