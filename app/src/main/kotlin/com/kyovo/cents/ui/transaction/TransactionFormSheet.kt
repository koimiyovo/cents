package com.kyovo.cents.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.ExpenseSubcategory
import com.kyovo.cents.domain.model.IncomeSubcategory
import com.kyovo.cents.domain.model.TransactionSubcategory
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.home.SubcategoryChip
import com.kyovo.cents.ui.home.datePickerColorScheme
import com.kyovo.cents.ui.home.subcategoryLabel
import com.kyovo.cents.ui.home.toEpochMillisUtc
import com.kyovo.cents.ui.home.toLocalDateUtc
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom sheet to record an expense, an income or a transfer. Amount and title come first (with
 * the amount focused on open, keyboard up) so the common case is a few taps; date is one tap away
 * (Aujourd'hui / Hier / a picked day), subcategory and description sit behind "Plus de détails".
 *
 * Stateless: the form lives in [TransactionFormViewModel] (so it survives rotation) and this only
 * renders it — every edit goes out through [onFormChange], and saving through [onSubmit]. Validation
 * errors appear only once [showErrors] is set by a first attempt to save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    accounts: List<Account>,
    form: TransactionFormState,
    showErrors: Boolean,
    failure: SubmitFailure?,
    onFormChange: (TransactionFormState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val selectable = remember(accounts) { selectableAccounts(accounts) }

    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val errors = if (showErrors)
    {
        (form.submit() as? FormSubmission.Invalid)?.errors.orEmpty()
    } else
    {
        emptySet()
    }

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The form is tall once the keyboard is up: open fully instead of stopping half-way.
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
                text = stringResource(R.string.transaction_form_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            TypeSelector(palette, form.type) { onFormChange(form.withType(it)) }

            AmountField(
                palette = palette,
                value = form.amountText,
                // Edits that would break the amount's shape (letters, a third decimal...) are dropped.
                onValueChange = { if (acceptsAmountInput(it)) onFormChange(form.copy(amountText = it)) },
                error = FormError.AMOUNT_INVALID in errors,
                focusRequester = amountFocus,
            )
            FieldError(palette, FormError.AMOUNT_INVALID in errors, FormError.AMOUNT_INVALID)

            FormTextField(
                palette = palette,
                value = form.title,
                onValueChange = { onFormChange(form.copy(title = it)) },
                placeholder = stringResource(R.string.transaction_form_title_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
            FieldError(palette, FormError.TITLE_REQUIRED in errors, FormError.TITLE_REQUIRED)

            DateRow(
                palette = palette,
                selectedDay = form.day(),
                onPickToday = { onFormChange(form.withDay(LocalDate.now(), Instant.now())) },
                onPickYesterday = {
                    onFormChange(form.withDay(LocalDate.now().minusDays(1), Instant.now()))
                },
                onPickOther = { showDatePicker = true },
            )

            if (selectable.isEmpty())
            {
                Text(
                    text = stringResource(R.string.transaction_form_no_active_account),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                )
            } else
            {
                val isTransfer = form.type == TransactionFormType.TRANSFER
                AccountPicker(
                    palette = palette,
                    label = stringResource(
                        if (isTransfer) R.string.transaction_form_account_from_label
                        else R.string.transaction_form_account_label,
                    ),
                    accounts = selectable,
                    selectedId = form.accountId,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                )
                FieldError(palette, FormError.ACCOUNT_REQUIRED in errors, FormError.ACCOUNT_REQUIRED)
                if (isTransfer)
                {
                    AccountPicker(
                        palette = palette,
                        label = stringResource(R.string.transaction_form_account_to_label),
                        accounts = selectable,
                        selectedId = form.toAccountId,
                        onSelect = { onFormChange(form.copy(toAccountId = it)) },
                    )
                    FieldError(
                        palette,
                        FormError.DESTINATION_ACCOUNT_REQUIRED in errors,
                        FormError.DESTINATION_ACCOUNT_REQUIRED,
                    )
                    FieldError(palette, FormError.SAME_ACCOUNT in errors, FormError.SAME_ACCOUNT)
                }
            }

            // A transfer has no subcategory or description (its command carries neither).
            if (form.type != TransactionFormType.TRANSFER)
            {
                Text(
                    text = stringResource(
                        if (showDetails) R.string.transaction_form_less_details
                        else R.string.transaction_form_more_details,
                    ),
                    color = palette.kicker,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showDetails = !showDetails },
                )
                if (showDetails)
                {
                    SubcategoryPicker(
                        palette = palette,
                        subcategories = subcategoriesFor(form.type),
                        selected = form.subcategory,
                        onSelect = { onFormChange(form.copy(subcategory = it)) },
                    )
                    FieldError(
                        palette,
                        FormError.SUBCATEGORY_MISMATCH in errors,
                        FormError.SUBCATEGORY_MISMATCH,
                    )
                    FormTextField(
                        palette = palette,
                        value = form.description,
                        onValueChange = { onFormChange(form.copy(description = it)) },
                        placeholder = stringResource(R.string.transaction_form_description_placeholder),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        singleLine = false,
                    )
                }
            }

            failure?.let {
                Text(
                    text = stringResource(
                        when (it)
                        {
                            SubmitFailure.ACCOUNT_NOT_FOUND -> R.string.transaction_form_failure_account_not_found
                            SubmitFailure.ARCHIVED_ACCOUNT  -> R.string.transaction_form_failure_archived
                            SubmitFailure.SAME_ACCOUNT      -> R.string.transaction_form_error_same_account
                        },
                    ),
                    color = palette.error,
                    fontSize = 13.sp,
                )
            }

            SubmitButton(palette, onSubmit)
        }
    }

    if (showDatePicker)
    {
        SingleDatePickerDialog(
            palette = palette,
            initialDay = form.day(),
            onDismiss = { showDatePicker = false },
            onConfirm = { day ->
                showDatePicker = false
                onFormChange(form.withDay(day, Instant.now()))
            },
        )
    }
}

private fun subcategoriesFor(type: TransactionFormType): List<TransactionSubcategory> = when (type)
{
    TransactionFormType.EXPENSE  -> ExpenseSubcategory.entries
    TransactionFormType.INCOME   -> IncomeSubcategory.entries
    TransactionFormType.TRANSFER -> emptyList()
}

@Composable
private fun TypeSelector(
    palette: AccountsPalette,
    selected: TransactionFormType,
    onSelect: (TransactionFormType) -> Unit,
)
{
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionFormType.entries.forEach { type ->
            SubcategoryChip(
                label = stringResource(
                    when (type)
                    {
                        TransactionFormType.EXPENSE  -> R.string.transaction_form_type_expense
                        TransactionFormType.INCOME   -> R.string.transaction_form_type_income
                        TransactionFormType.TRANSFER -> R.string.transaction_form_type_transfer
                    },
                ),
                selected = type == selected,
                palette = palette,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun AmountField(
    palette: AccountsPalette,
    value: String,
    onValueChange: (String) -> Unit,
    error: Boolean,
    focusRequester: FocusRequester,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = if (error) palette.error else palette.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(palette.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { innerField ->
                Box {
                    if (value.isEmpty())
                    {
                        Text(
                            text = stringResource(R.string.transaction_form_amount_placeholder),
                            color = palette.textMuted,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    innerField()
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(text = "€", color = palette.textSecondary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

/** Same look as the transactions screen's search field, minus the icon. */
@Composable
private fun FormTextField(
    palette: AccountsPalette,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    singleLine: Boolean = true,
)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty())
        {
            Text(text = placeholder, color = palette.textMuted, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.textPrimary),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AccountPicker(
    palette: AccountsPalette,
    label: String,
    accounts: List<Account>,
    selectedId: AccountId?,
    onSelect: (AccountId) -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, label)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accounts.forEach { account ->
                SubcategoryChip(
                    label = account.name.value,
                    selected = account.id == selectedId,
                    palette = palette,
                    onClick = { onSelect(account.id) },
                )
            }
        }
    }
}

@Composable
private fun SubcategoryPicker(
    palette: AccountsPalette,
    subcategories: List<TransactionSubcategory>,
    selected: TransactionSubcategory?,
    onSelect: (TransactionSubcategory?) -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, stringResource(R.string.transaction_form_subcategory_label))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubcategoryChip(
                label = stringResource(R.string.transaction_form_subcategory_none),
                selected = selected == null,
                palette = palette,
                onClick = { onSelect(null) },
            )
            subcategories.forEach { subcategory ->
                SubcategoryChip(
                    label = subcategoryLabel(subcategory),
                    selected = subcategory == selected,
                    palette = palette,
                    onClick = { onSelect(subcategory) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(palette: AccountsPalette, text: String)
{
    Text(text = text, color = palette.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun FieldError(palette: AccountsPalette, visible: Boolean, error: FormError)
{
    if (!visible) return
    Text(
        text = stringResource(
            when (error)
            {
                FormError.AMOUNT_INVALID                -> R.string.transaction_form_error_amount
                FormError.TITLE_REQUIRED                -> R.string.transaction_form_error_title
                FormError.ACCOUNT_REQUIRED              -> R.string.transaction_form_error_account
                FormError.DESTINATION_ACCOUNT_REQUIRED  -> R.string.transaction_form_error_destination
                FormError.SAME_ACCOUNT                  -> R.string.transaction_form_error_same_account
                FormError.SUBCATEGORY_MISMATCH          -> R.string.transaction_form_error_subcategory
            },
        ),
        color = palette.error,
        fontSize = 12.sp,
    )
}

@Composable
private fun SubmitButton(palette: AccountsPalette, onClick: () -> Unit)
{
    // Filled with iconToneGreen like the selected chips: palette.primaryButtonBackground is an
    // outlined white button in light mode, too discreet for the form's one main action.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.iconToneGreen)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.transaction_form_submit),
            color = palette.heroOnCardPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Today / yesterday chips plus a third one that opens the calendar (and shows the picked day). */
@Composable
private fun DateRow(
    palette: AccountsPalette,
    selectedDay: LocalDate,
    onPickToday: () -> Unit,
    onPickYesterday: () -> Unit,
    onPickOther: () -> Unit,
)
{
    val today = LocalDate.now()
    val isOtherDay = selectedDay != today && selectedDay != today.minusDays(1)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubcategoryChip(
            label = stringResource(R.string.transactions_today),
            selected = selectedDay == today,
            palette = palette,
            onClick = onPickToday,
        )
        SubcategoryChip(
            label = stringResource(R.string.transactions_yesterday),
            selected = selectedDay == today.minusDays(1),
            palette = palette,
            onClick = onPickYesterday,
        )
        SubcategoryChip(
            label = if (isOtherDay)
            {
                "📅 " + selectedDay.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH))
            } else
            {
                stringResource(R.string.transaction_form_date_other)
            },
            selected = isOtherDay,
            palette = palette,
            onClick = onPickOther,
        )
    }
}

/**
 * Single-day picker, themed with the same scoped color scheme as the transactions range picker.
 * Future days aren't selectable: a transaction is recorded once it has happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerDialog(
    palette: AccountsPalette,
    initialDay: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
)
{
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay.toEpochMillisUtc(),
        selectableDates = object : SelectableDates
        {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        },
    )
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH) }
    MaterialTheme(colorScheme = datePickerColorScheme(palette)) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { state.selectedDateMillis?.let { onConfirm(it.toLocalDateUtc()) } },
                    enabled = state.selectedDateMillis != null,
                ) { Text(stringResource(R.string.transaction_form_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.transaction_form_date_cancel))
                }
            },
        ) {
            DatePicker(
                state = state,
                title = {
                    Text(
                        text = stringResource(R.string.transaction_form_date_title),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
                // Replaced for the same reasons as in the range picker: the default headline is
                // sized for a full-screen dialog and isn't French.
                headline = {
                    Text(
                        text = state.selectedDateMillis?.toLocalDateUtc()?.format(formatter).orEmpty(),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
            )
        }
    }
}
