package com.kyovo.cents.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.InvalidBudgetSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.port.input.GetBudgetCalendarUseCase
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase
import com.kyovo.cents.domain.port.input.GetSpendingTrendUseCase
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

/** Which of the budgets screen's three tabs is shown. [OVERVIEW] comes first — it is the one that answers
 * "where do things stand" at a glance, with no budget required to say something. */
enum class BudgetTab
{
    OVERVIEW,
    BUDGETS,
    TRENDS,
}

/**
 * Everything the budgets screens show: the month ([selector]), the account the tab is scoped to
 * ([selectedAccountId], null meaning every account — the same "no filter" convention as Mouvements's own
 * account filter), one [rows] entry per expense subcategory with its progress for that month and account,
 * the month's spending [breakdown] (the overview tab's pie), the last few months' totals ([trend], the
 * trends tab's bars), and the [form] that sets a limit and an alert threshold — null while it is closed, so
 * "is it open" and its content can't disagree. [isCurrentMonth] tells the screen whether to offer a way
 * back to today. [rows], [breakdown] and [trend] are always those of the month and account in [selector] /
 * [selectedAccountId] (the trend's window ends at the month).
 */
data class BudgetsUiState(
    val selector: MonthSelector,
    val selectedAccountId: AccountId? = null,
    val rows: List<BudgetRow> = emptyList(),
    val breakdown: SpendingBreakdown = SpendingBreakdown(Money(0), emptyList()),
    val trend: SpendingTrend = SpendingTrend(emptyList(), null, null),
    val form: BudgetFormState? = null,
    val error: BudgetFormError? = null,
    val isCurrentMonth: Boolean = true,
    /** The days the month covers ("25 sept. – 27 oct."), null when it is a plain calendar month. */
    val cycleRange: String? = null,
    val tab: BudgetTab = BudgetTab.OVERVIEW,
    val askNotificationPermission: Boolean = false
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
    getSpendingTrend: GetSpendingTrendUseCase,
    getBudgetCalendar: GetBudgetCalendarUseCase,
    private val setBudget: SetBudgetUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel()
{
    /** What the user chose: the month, the account filter, the tab and the form. Not what is read from storage. */
    private data class Chosen(
        /** The month picked with the arrows; null follows the cycle open today, whichever the calendar says. */
        val month: YearMonth? = null,
        val accountId: AccountId? = null,
        val form: BudgetFormState? = null,
        val error: BudgetFormError? = null,
        val tab: BudgetTab = BudgetTab.OVERVIEW,
        val askNotificationPermission: Boolean = false
    )

    /** What is being looked at: the month and the account filter together, since both restart every reading. */
    private data class Scope(val month: YearMonth, val accountId: AccountId?)

    /** The budget month open today: a cycle may start on the 25th, so it is not always the calendar month. */
    private val currentCycle: StateFlow<YearMonth> = getBudgetCalendar.observe()
        .map { it.cycleOf(LocalDate.now(clock)) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, YearMonth.now(clock))

    private val calendar: StateFlow<BudgetCalendar> = getBudgetCalendar.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, BudgetCalendar())

    private val chosen = MutableStateFlow(Chosen())
    private val scope: Flow<Scope> =
        combine(chosen, currentCycle) { chosen, today -> Scope(chosen.month ?: today, chosen.accountId) }
            .distinctUntilChanged()

    /** What the user chose, with what the month is relative to: today's cycle and the calendar's days. */
    private data class Looking(val chosen: Chosen, val today: YearMonth, val calendar: BudgetCalendar)

    /**
     * The rows of each month/account asked for, tagged with that scope. Only the month or the account
     * filter restarts the reading: typing in the form must not query the storage again at each keystroke.
     */
    private val rowsOfTheMonth: Flow<Pair<Scope, List<BudgetRow>>> = scope
        .flatMapLatest { scope ->
            combine(
                listSubcategories.observe(RecordableTransactionCategory.EXPENSE),
                getBudgetProgress.observeAll(scope.month, scope.accountId),
            ) { subcategories, progress ->
                scope to budgetRows(subcategories, progress, scope.month, LocalDate.now(clock))
            }
        }

    /** The overview tab's pie: where the month's money went, budget or no budget. */
    private val breakdownOfTheMonth: Flow<Pair<Scope, SpendingBreakdown>> = scope
        .flatMapLatest { scope ->
            combine(
                listSubcategories.observe(RecordableTransactionCategory.EXPENSE),
                getSpendingBreakdown.observe(scope.month, scope.accountId),
            ) { subcategories, spent -> scope to spendingBreakdown(subcategories, spent) }
        }

    /** The trends tab's bars: the last few months' totals, this one's window ending at the month shown. */
    private val trendOfTheMonth: Flow<Pair<Scope, SpendingTrend>> = scope
        .flatMapLatest { scope ->
            getSpendingTrend.observe(scope.month, accountId = scope.accountId)
                .map { monthly -> scope to spendingTrend(monthly) }
        }

    // A state is only built once the rows, the breakdown and the trend are those of the scope chosen: right
    // after a change of month or account, the previous scope's data is still there for an instant, and must
    // not appear under the new scope's name.
    val uiState: StateFlow<BudgetsUiState> =
        combine(
            combine(chosen, currentCycle, calendar, ::Looking),
            rowsOfTheMonth, breakdownOfTheMonth, trendOfTheMonth
        )
        { (chosen, today, calendar), (rowsScope, rows), (breakdownScope, breakdown), (trendScope, trend) ->
            val month = chosen.month ?: today
            val wanted = Scope(month, chosen.accountId)
            if (rowsScope != wanted || breakdownScope != wanted || trendScope != wanted) null
            else BudgetsUiState(
                selector = MonthSelector(month),
                selectedAccountId = chosen.accountId,
                rows = rows,
                breakdown = breakdown,
                trend = trend,
                form = chosen.form,
                error = chosen.error,
                isCurrentMonth = MonthSelector(month).isCurrent(today),
                cycleRange = budgetCycleRangeLabel(calendar, month),
                tab = chosen.tab,
                askNotificationPermission = chosen.askNotificationPermission
            )
        }
            .filterNotNull()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                BudgetsUiState(MonthSelector(currentCycle.value), isCurrentMonth = true),
            )

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
        chosen.update { it.copy(month = null, form = null, error = null) }
    }

    fun selectTab(tab: BudgetTab)
    {
        chosen.update { it.copy(tab = tab) }
    }

    /** Narrows the tab to one account's spending (null: every account). The form is left as it is — a
     * budget's limit is not scoped to an account, unlike its progress. */
    fun selectAccount(accountId: AccountId?)
    {
        chosen.update { it.copy(accountId = accountId) }
    }

    /** A form is for the month it was opened in, so leaving that month closes it. */
    private fun moveTo(target: (MonthSelector) -> MonthSelector)
    {
        chosen.update {
            it.copy(month = target(MonthSelector(it.month ?: currentCycle.value)).month, form = null, error = null)
        }
    }

    /** Opens the form on [row], for the month shown, pre-filled with the budget in force if there is one. */
    fun openForm(row: BudgetRow)
    {
        chosen.update {
            it.copy(
                form = BudgetFormState.setting(row, it.month ?: currentCycle.value),
                error = null
            )
        }
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

            is BudgetSubmission.Set     ->
            {
                try
                {
                    setBudget.set(submission.command)
                    chosen.update { it.copy(askNotificationPermission = true) }
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

    fun dismissNotificationPermissionAsk()
    {
        chosen.update { it.copy(askNotificationPermission = false) }
    }
}
