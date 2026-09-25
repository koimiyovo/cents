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
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.ui.common.AmountField
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.FormTextField
import com.kyovo.cents.ui.common.OutlinedFormButton
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
import com.kyovo.cents.ui.home.datePickerColorScheme
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
    subcategories: List<Subcategory>,
    form: TransactionFormState,
    showErrors: Boolean,
    failure: SubmitFailure?,
    onFormChange: (TransactionFormState) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
    onCreateSubcategory: () -> Unit,
    onCreateAccount: (AccountField) -> Unit,
    onDismiss: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val selectable = remember(accounts, form.originalAccountId) { form.accountChoices(accounts) }

    // Opens straight on the details when the edited transaction already has some.
    var showDetails by rememberSaveable { mutableStateOf(form.subcategory != null || form.description.isNotBlank()) }
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
                text = stringResource(
                    if (form.isEditing) R.string.transaction_form_title_edit else R.string.transaction_form_title,
                ),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            val archivedOriginal = remember(accounts, form.originalAccountId) { form.archivedOriginalAccount(accounts) }
            if (archivedOriginal != null)
            {
                ArchivedAccountNotice(palette, archivedOriginal.name.value)
            }
            TypeSelector(
                palette = palette,
                types = if (form.isEditing) listOf(TransactionFormType.EXPENSE, TransactionFormType.INCOME)
                else TransactionFormType.entries,
                selected = form.type,
            ) { onFormChange(form.withType(it)) }

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
                // Nothing to record against yet: say so, and offer the way out right here.
                Text(
                    text = stringResource(R.string.transaction_form_no_active_account),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                )
                OutlinedFormButton(
                    palette,
                    stringResource(R.string.transaction_form_create_account),
                ) { onCreateAccount(AccountField.SOURCE) }
            } else
            {
                val isTransfer = form.type == TransactionFormType.TRANSFER
                AccountPicker(
                    palette = palette,
                    label = stringResource(
                        if (isTransfer) R.string.transaction_form_account_from_label
                        else R.string.transaction_form_account_label,
                    ),
                    accounts = form.sourceChoices(selectable),
                    selectedId = form.accountId,
                    onSelect = { onFormChange(form.copy(accountId = it)) },
                    onCreate = { onCreateAccount(AccountField.SOURCE) },
                )
                FieldError(palette, FormError.ACCOUNT_REQUIRED in errors, FormError.ACCOUNT_REQUIRED)
                if (isTransfer)
                {
                    AccountPicker(
                        palette = palette,
                        label = stringResource(R.string.transaction_form_account_to_label),
                        accounts = form.destinationChoices(selectable),
                        selectedId = form.toAccountId,
                        onSelect = { onFormChange(form.copy(toAccountId = it)) },
                        onCreate = { onCreateAccount(AccountField.DESTINATION) },
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
                        subcategories = form.subcategoryChoices(subcategories),
                        selected = form.subcategory,
                        onSelect = { onFormChange(form.copy(subcategory = it)) },
                        onCreate = onCreateSubcategory,
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
                ErrorText(
                    palette = palette,
                    text = stringResource(
                        when (it)
                        {
                            SubmitFailure.ACCOUNT_NOT_FOUND -> R.string.transaction_form_failure_account_not_found
                            SubmitFailure.ARCHIVED_ACCOUNT  -> R.string.transaction_form_failure_archived
                            SubmitFailure.SAME_ACCOUNT      -> R.string.transaction_form_error_same_account
                            SubmitFailure.TRANSACTION_UNAVAILABLE -> R.string.transaction_form_failure_unavailable
                        },
                    ),
                )
            }

            SubmitButton(palette, stringResource(R.string.transaction_form_submit), onSubmit)
            // Only for a transaction that exists: there is nothing to delete in a new one.
            if (form.isEditing)
            {
                DestructiveButton(palette, stringResource(R.string.transaction_form_delete), onDelete)
            }
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

@Composable
private fun TypeSelector(
    palette: AccountsPalette,
    types: List<TransactionFormType>,
    selected: TransactionFormType,
    onSelect: (TransactionFormType) -> Unit,
)
{
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { type ->
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

/** Editing is allowed on an archived account, but it is a closed one: the user should know. */
@Composable
private fun ArchivedAccountNotice(palette: AccountsPalette, accountName: String)
{
    Text(
        text = "🗄️ " + stringResource(R.string.transaction_form_archived_notice, accountName),
        color = palette.kicker,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun AccountPicker(
    palette: AccountsPalette,
    label: String,
    accounts: List<Account>,
    selectedId: AccountId?,
    onSelect: (AccountId) -> Unit,
    onCreate: () -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, label)
        // A dropdown, not chips: the number of accounts is up to the user, and the trigger always
        // shows the current choice (a preselected account can't hide off-screen in a scrolling row).
        SelectDropdown(
            palette = palette,
            options = accounts.map { SelectOption<AccountId?>(it.id, it.name.value) },
            selected = selectedId,
            onSelect = { it?.let(onSelect) },
            fillWidth = true,
            footerLabel = stringResource(R.string.transaction_form_account_create),
            onFooterClick = onCreate,
            placeholder = stringResource(R.string.transaction_form_account_placeholder),
        )
    }
}

@Composable
private fun SubcategoryPicker(
    palette: AccountsPalette,
    subcategories: List<Subcategory>,
    selected: Subcategory?,
    onSelect: (Subcategory?) -> Unit,
    onCreate: () -> Unit,
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
            footerLabel = stringResource(R.string.transaction_form_subcategory_create),
            onFooterClick = onCreate,
        )
    }
}

@Composable
private fun FieldError(palette: AccountsPalette, visible: Boolean, error: FormError)
{
    if (!visible) return
    ErrorText(
        palette = palette,
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
    )
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
