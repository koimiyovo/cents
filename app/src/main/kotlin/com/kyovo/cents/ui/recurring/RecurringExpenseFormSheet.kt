package com.kyovo.cents.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.ui.common.AmountField
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.FormTextField
import com.kyovo.cents.ui.common.SectionLabel
import com.kyovo.cents.ui.common.SelectDropdown
import com.kyovo.cents.ui.common.SelectOption
import com.kyovo.cents.ui.common.SubmitButton
import com.kyovo.cents.ui.common.acceptsAmountInput
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.DestructiveButton
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.home.SubcategoryChip
import com.kyovo.cents.ui.transaction.SingleDatePickerDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom sheet to create or edit a recurring-expense rule: which account it pays out of (fixed
 * once created), an amount, a title, an optional expense subcategory, its pace (frequency +
 * interval) and a start date, with an optional end date. An edit also offers deletion — the rule
 * only, its already-generated transactions stay (see [RecurringExpensesViewModel]).
 * Stateless, like the other sheets: the form lives in [RecurringExpensesViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpenseFormSheet(
    accounts: List<Account>,
    subcategories: List<Subcategory>,
    form: RecurringExpenseFormState,
    errors: Set<RecurringExpenseFormError>,
    onFormChange: (RecurringExpenseFormState) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }
    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showEndDatePicker by rememberSaveable { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH) }

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
                text = stringResource(
                    if (form.isEditing) R.string.recurring_form_title_edit else R.string.recurring_form_title_new,
                ),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            if (!form.isEditing)
            {
                AccountPickerField(palette, accounts, form.accountId, onSelect = { onFormChange(form.withAccount(it)) })
                if (RecurringExpenseFormError.ACCOUNT_REQUIRED in errors)
                {
                    ErrorText(palette, stringResource(R.string.recurring_form_error_account))
                }
            }

            AmountField(
                palette = palette,
                value = form.amountText,
                onValueChange = { if (acceptsAmountInput(it)) onFormChange(form.withAmount(it)) },
                error = RecurringExpenseFormError.AMOUNT_INVALID in errors,
                focusRequester = amountFocus,
            )
            if (RecurringExpenseFormError.AMOUNT_INVALID in errors)
            {
                ErrorText(palette, stringResource(R.string.recurring_form_error_amount))
            }

            FormTextField(
                palette = palette,
                value = form.title,
                onValueChange = { onFormChange(form.withTitle(it)) },
                placeholder = stringResource(R.string.recurring_form_title_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
            if (RecurringExpenseFormError.TITLE_REQUIRED in errors)
            {
                ErrorText(palette, stringResource(R.string.recurring_form_error_title))
            }

            SubcategoryPickerField(
                palette = palette,
                subcategories = form.subcategoryChoices(subcategories),
                selected = form.subcategory,
                onSelect = { onFormChange(form.withSubcategory(it)) },
            )

            FrequencyField(palette, form.frequency, onSelect = { onFormChange(form.withFrequency(it)) })

            IntervalField(
                palette = palette,
                frequency = form.frequency,
                text = form.intervalText,
                onValueChange = { onFormChange(form.withInterval(it)) },
            )
            if (RecurringExpenseFormError.INTERVAL_INVALID in errors)
            {
                ErrorText(palette, stringResource(R.string.recurring_form_error_interval))
            }

            if (!form.isEditing)
            {
                DateField(
                    palette = palette,
                    label = stringResource(R.string.recurring_form_start_date_label),
                    date = form.startDate,
                    formatter = formatter,
                    onClick = { showStartDatePicker = true },
                )
            }

            EndDateField(
                palette = palette,
                hasEndDate = form.hasEndDate,
                endDate = form.endDate,
                formatter = formatter,
                onToggle = { onFormChange(form.withEndDateEnabled(it)) },
                onClick = { showEndDatePicker = true },
            )
            if (RecurringExpenseFormError.END_BEFORE_START in errors)
            {
                ErrorText(palette, stringResource(R.string.recurring_form_error_end_before_start))
            }

            if (RecurringExpenseFormError.ACCOUNT_GONE in errors)
            {
                ErrorText(palette, stringResource(R.string.recurring_form_error_account_gone))
            }
            if (RecurringExpenseFormError.SUBCATEGORY_GONE in errors)
            {
                ErrorText(palette, stringResource(R.string.recurring_form_error_subcategory_gone))
            }
            if (RecurringExpenseFormError.RULE_GONE in errors)
            {
                ErrorText(palette, stringResource(R.string.recurring_form_error_rule_gone))
            }

            SubmitButton(palette, stringResource(R.string.recurring_form_submit), onSubmit)
            if (form.isEditing)
            {
                DestructiveButton(palette, stringResource(R.string.recurring_form_delete), onDelete)
            }
        }
    }

    if (showStartDatePicker)
    {
        SingleDatePickerDialog(
            palette = palette,
            initialDay = form.startDate,
            allowFuture = true,
            onDismiss = { showStartDatePicker = false },
            onConfirm = { day ->
                showStartDatePicker = false
                onFormChange(form.withStartDate(day))
            },
        )
    }
    if (showEndDatePicker)
    {
        SingleDatePickerDialog(
            palette = palette,
            initialDay = form.endDate,
            allowFuture = true,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { day ->
                showEndDatePicker = false
                onFormChange(form.withEndDate(day))
            },
        )
    }
}

@Composable
private fun AccountPickerField(
    palette: AccountsPalette,
    accounts: List<Account>,
    selectedId: AccountId?,
    onSelect: (AccountId) -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, stringResource(R.string.recurring_form_account_label))
        SelectDropdown(
            palette = palette,
            options = accounts.map { SelectOption(it.id, it.name.value) },
            selected = selectedId,
            onSelect = { it?.let(onSelect) },
            fillWidth = true,
            placeholder = stringResource(R.string.recurring_form_account_placeholder),
        )
    }
}

@Composable
private fun SubcategoryPickerField(
    palette: AccountsPalette,
    subcategories: List<Subcategory>,
    selected: Subcategory?,
    onSelect: (Subcategory?) -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, stringResource(R.string.transaction_form_subcategory_label))
        val noneLabel = stringResource(R.string.transaction_form_subcategory_none)
        SelectDropdown(
            palette = palette,
            options = listOf(SelectOption<Subcategory?>(null, noneLabel)) +
                subcategories.map { SelectOption<Subcategory?>(it, it.name.value) },
            selected = selected,
            onSelect = onSelect,
            fillWidth = true,
        )
    }
}

@Composable
private fun FrequencyField(
    palette: AccountsPalette,
    selected: RecurrenceFrequency,
    onSelect: (RecurrenceFrequency) -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, stringResource(R.string.recurring_form_frequency_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurrenceFrequency.entries.forEach { frequency ->
                SubcategoryChip(
                    label = stringResource(
                        when (frequency)
                        {
                            RecurrenceFrequency.WEEKLY  -> R.string.recurring_form_frequency_weekly
                            RecurrenceFrequency.MONTHLY -> R.string.recurring_form_frequency_monthly
                            RecurrenceFrequency.YEARLY  -> R.string.recurring_form_frequency_yearly
                        },
                    ),
                    selected = frequency == selected,
                    palette = palette,
                    onClick = { onSelect(frequency) },
                )
            }
        }
    }
}

@Composable
private fun IntervalField(
    palette: AccountsPalette,
    frequency: RecurrenceFrequency,
    text: String,
    onValueChange: (String) -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(
            palette,
            stringResource(
                when (frequency)
                {
                    RecurrenceFrequency.WEEKLY  -> R.string.recurring_form_interval_label_weekly
                    RecurrenceFrequency.MONTHLY -> R.string.recurring_form_interval_label_monthly
                    RecurrenceFrequency.YEARLY  -> R.string.recurring_form_interval_label_yearly
                },
            ),
        )
        FormTextField(
            palette = palette,
            value = text,
            onValueChange = onValueChange,
            placeholder = "1",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun DateField(
    palette: AccountsPalette,
    label: String,
    date: LocalDate,
    formatter: DateTimeFormatter,
    onClick: () -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(text = date.format(formatter), color = palette.textPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun EndDateField(
    palette: AccountsPalette,
    hasEndDate: Boolean,
    endDate: LocalDate,
    formatter: DateTimeFormatter,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(palette, stringResource(R.string.recurring_form_end_date_label))
            }
            Switch(
                checked = hasEndDate,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = palette.iconToneGreen),
            )
        }
        if (hasEndDate)
        {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surface)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(text = endDate.format(formatter), color = palette.textPrimary, fontSize = 15.sp)
            }
        } else
        {
            Text(
                text = stringResource(R.string.recurring_form_end_date_none),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
    }
}
