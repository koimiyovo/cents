package com.kyovo.cents.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.OutlinedFormButton
import com.kyovo.cents.ui.common.SelectDropdown
import com.kyovo.cents.ui.common.SelectOption
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.BackButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.transaction.SingleDatePickerDialog
import java.time.LocalDate
import java.time.YearMonth

/**
 * Where a budget month starts. It is the day the money comes in, which is not always the 1st, and not
 * always the same day: so there is a usual day (1 to 28), and a start can be declared for one cycle, by
 * telling the app on which day the pay came — the date alone says which cycle it opens.
 */
@Composable
fun BudgetCycleScreen(
    state: BudgetCycleUiState,
    onBack: () -> Unit,
    onChangeDefaultDay: (Int) -> Unit,
    onDeclare: (LocalDate) -> Unit,
    onClear: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    var picking by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(12.dp))
            HomeTopBar(palette, stringResource(R.string.budget_cycle_title))
        }
        Text(
            text = stringResource(R.string.budget_cycle_intro),
            color = palette.textMuted,
            fontSize = 13.sp,
        )

        Card(palette) {
            Text(
                text = stringResource(R.string.budget_cycle_current, state.currentMonthLabel),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.currentRange ?: stringResource(R.string.budget_cycle_calendar_month),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }

        Card(palette) {
            Text(
                text = stringResource(R.string.budget_cycle_default_title),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.budget_cycle_default_hint),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
            SelectDropdown(
                palette = palette,
                options = (1..28).map { SelectOption(it, stringResource(R.string.budget_cycle_day_option, it)) },
                selected = state.defaultStartDay,
                onSelect = onChangeDefaultDay,
                fillWidth = true,
            )
        }

        Card(palette) {
            Text(
                text = stringResource(R.string.budget_cycle_declare_title),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.budget_cycle_declare_hint),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
            OutlinedFormButton(palette, stringResource(R.string.budget_cycle_declare_button)) { picking = true }
        }

        if (state.declared.isNotEmpty())
        {
            Card(palette) {
                Text(
                    text = stringResource(R.string.budget_cycle_declared_title),
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                state.declared.forEach { row -> DeclaredStart(palette, row, onClear) }
            }
        }
    }

    if (picking)
    {
        SingleDatePickerDialog(
            palette = palette,
            initialDay = LocalDate.now(),
            onDismiss = { picking = false },
            onConfirm = { date ->
                picking = false
                onDeclare(date)
            },
        )
    }
}

@Composable
private fun Card(palette: AccountsPalette, content: @Composable () -> Unit)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun DeclaredStart(palette: AccountsPalette, row: DeclaredStartRow, onClear: (YearMonth) -> Unit)
{
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)
    {
        Text(
            text = stringResource(R.string.budget_cycle_declared_row, row.monthLabel, row.startLabel),
            color = palette.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.budget_cycle_clear),
            color = palette.error,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClear(row.month) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
