package com.kyovo.cents.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.InvalidBudgetSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
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
import java.time.YearMonth

/** What went wrong with the last attempt to save a budget. */
enum class BudgetFormError
{
    /** The limit is empty, zero, or not an amount. */
    LIMIT_INVALID,

    /** The subcategory was deleted, or is not an expense, since the form was opened. */
    SUBCATEGORY_UNAVAILABLE,
}

/**
 * Everything the budgets screens show: the month ([selector]), one [rows] entry per expense subcategory
 * with its progress for that month, and the [form] that sets a limit and an alert threshold — null while
 * it is closed, so "is it open" and its content can't disagree. [isCurrentMonth] tells the screen whether
 * to offer a way back to today. The rows are always those of the month in [selector].
 */
data class BudgetsUiState(
    val selector: MonthSelector,
    val rows: List<BudgetRow> = emptyList(),
    val form: BudgetFormState? = null,
    val error: BudgetFormError? = null,
    val isCurrentMonth: Boolean = true,
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
    private val setBudget: SetBudgetUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel()
{
    /** What the user chose: the month and the form. Not what is read from the storage. */
    private data class Chosen(
        val selector: MonthSelector,
        val form: BudgetFormState? = null,
        val error: BudgetFormError? = null,
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
            ) { subcategories, progress -> month to budgetRows(subcategories, progress) }
        }

    // A state is only built once the rows are those of the month chosen: right after a change of month, the
    // rows of the month left are still there for an instant, and must not appear under the new month's name.
    val uiState: StateFlow<BudgetsUiState> = combine(chosen, rowsOfTheMonth) { chosen, (month, rows) ->
        if (month != chosen.selector.month) null
        else BudgetsUiState(
            selector = chosen.selector,
            rows = rows,
            form = chosen.form,
            error = chosen.error,
            isCurrentMonth = chosen.selector.isCurrent(currentMonth()),
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
