package com.kyovo.cents.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyovo.cents.domain.exception.InvalidBudgetStartDayException
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.port.input.ClearBudgetCycleStartUseCase
import com.kyovo.cents.domain.port.input.GetBudgetCalendarUseCase
import com.kyovo.cents.domain.port.input.SetBudgetCycleStartUseCase
import com.kyovo.cents.domain.port.input.SetDefaultBudgetStartDayUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the budget cycle settings show: the day cycles start on when nothing else says so, the cycle open today
 * (its month's name and the days it covers) and the starts the user declared, each of which can be taken back.
 */
data class BudgetCycleUiState(
    val defaultStartDay: Int = 1,
    val currentMonthLabel: String = "",
    val currentRange: String? = null,
    val declared: List<DeclaredStartRow> = emptyList(),
)

/** The state behind the budget cycle settings: it observes the calendar and writes the three ways to shape it. */
class BudgetCycleViewModel(
    getBudgetCalendar: GetBudgetCalendarUseCase,
    private val setDefaultStartDay: SetDefaultBudgetStartDayUseCase,
    private val setCycleStart: SetBudgetCycleStartUseCase,
    private val clearCycleStart: ClearBudgetCycleStartUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel()
{
    val uiState: StateFlow<BudgetCycleUiState> = getBudgetCalendar.observe()
        .map { calendar ->
            val current = calendar.cycleOf(LocalDate.now(clock))
            BudgetCycleUiState(
                defaultStartDay = calendar.defaultStartDay.value,
                currentMonthLabel = MonthSelector(current).label,
                currentRange = budgetCycleRangeLabel(calendar, current),
                declared = declaredStartRows(calendar),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BudgetCycleUiState())

    fun changeDefaultStartDay(day: Int)
    {
        viewModelScope.launch {
            try
            {
                setDefaultStartDay.set(BudgetStartDay(day))
            } catch (_: InvalidBudgetStartDayException)
            {
                // Not reachable from the picker, which offers 1..28 only: nothing to change, and not a crash.
            }
        }
    }

    /** The pay came on [date]: the cycle that date opens starts then. */
    fun declareStart(date: LocalDate)
    {
        viewModelScope.launch { setCycleStart.set(date) }
    }

    fun clearStart(month: YearMonth)
    {
        viewModelScope.launch { clearCycleStart.clear(month) }
    }
}
