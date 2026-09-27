package com.kyovo.cents.ui.budget

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.AmountField
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.SectionLabel
import com.kyovo.cents.ui.common.SubmitButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette
import kotlin.math.roundToInt

/**
 * Bottom sheet that sets one subcategory's budget for a month: the monthly limit, and the alert threshold — the
 * share of the limit from which the budget counts as "close" — moved with a slider. Says from which month the
 * limit holds. Stateless, like the other sheets: the form lives in [BudgetsViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormSheet(
    form: BudgetFormState,
    error: BudgetFormError?,
    onLimitChange: (String) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val limitFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { limitFocus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.background,
        contentColor = palette.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.budget_form_title, form.subcategory.name.value),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            // A budget holds from a month on, not for that month only: said, so it is no surprise.
            Text(
                text = stringResource(R.string.budget_form_applies_from, MonthSelector(form.month).label),
                color = palette.textMuted,
                fontSize = 13.sp,
            )

            SectionLabel(palette, stringResource(R.string.budget_form_limit_label))
            AmountField(
                palette = palette,
                value = form.limitText,
                onValueChange = onLimitChange,
                error = error == BudgetFormError.LIMIT_INVALID,
                focusRequester = limitFocus,
            )

            SectionLabel(palette, stringResource(R.string.budget_form_threshold_label, form.thresholdPercent))
            Slider(
                value = form.thresholdPercent.toFloat(),
                // The form snaps what the slider reports to its steps, so the thumb never rests between two.
                onValueChange = { onThresholdChange(it.roundToInt()) },
                valueRange = THRESHOLD_SLIDER_MIN_PERCENT.toFloat()..THRESHOLD_SLIDER_MAX_PERCENT.toFloat(),
                steps = THRESHOLD_SLIDER_STEPS,
                colors = SliderDefaults.colors(
                    thumbColor = palette.iconToneGreen,
                    activeTrackColor = palette.iconToneGreen,
                    inactiveTrackColor = palette.divider,
                    activeTickColor = palette.heroOnCardPrimary,
                    inactiveTickColor = palette.textMuted,
                ),
            )
            Text(
                text = stringResource(R.string.budget_form_threshold_hint, form.thresholdPercent),
                color = palette.textMuted,
                fontSize = 13.sp,
            )

            when (error)
            {
                BudgetFormError.LIMIT_INVALID           -> ErrorText(palette, stringResource(R.string.budget_form_error_limit))
                BudgetFormError.SUBCATEGORY_UNAVAILABLE -> ErrorText(palette, stringResource(R.string.budget_form_error_unavailable))
                null                                    -> Unit
            }
            SubmitButton(palette, stringResource(R.string.transaction_form_submit), onSubmit)
        }
    }
}
