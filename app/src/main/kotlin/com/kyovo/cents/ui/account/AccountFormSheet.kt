package com.kyovo.cents.ui.account

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.ui.common.AmountField
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.FormTextField
import com.kyovo.cents.ui.common.limitNameInput
import com.kyovo.cents.ui.common.SectionLabel
import com.kyovo.cents.ui.common.SubmitButton
import com.kyovo.cents.ui.common.acceptsAmountInput
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.home.SubcategoryChip
import com.kyovo.cents.ui.home.accountEmoji
import com.kyovo.cents.ui.home.accountTypeLabelRes

/**
 * Bottom sheet to open a new account: a name, a type, and optionally an opening balance and a
 * description. Same structure as the transaction sheet — stateless, the form lives in
 * [AccountFormViewModel] so it survives rotation, and errors show only once [showErrors] is set by
 * a first attempt to save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormSheet(
    form: AccountFormState,
    showErrors: Boolean,
    failure: AccountSubmitFailure?,
    onFormChange: (AccountFormState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val errors = if (showErrors)
    {
        (form.submit() as? AccountFormSubmission.Invalid)?.errors.orEmpty()
    } else
    {
        emptySet()
    }

    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

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
                text = stringResource(if (form.isEditing) R.string.account_form_title_edit else R.string.account_form_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )

            FormTextField(
                palette = palette,
                value = form.name,
                // Typing stops at the domain's limit: the field never holds a name it would refuse.
                onValueChange = { onFormChange(form.copy(name = limitNameInput(it, AccountName.MAX_LENGTH))) },
                placeholder = stringResource(R.string.account_form_name_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                focusRequester = nameFocus,
            )
            if (AccountFormError.NAME_REQUIRED in errors)
            {
                ErrorText(palette, stringResource(R.string.account_form_error_name))
            }
            if (failure == AccountSubmitFailure.DUPLICATE_NAME)
            {
                ErrorText(palette, stringResource(R.string.account_form_failure_duplicate_name))
            }

            TypePicker(palette, form.type) { onFormChange(form.copy(type = it)) }

            // The opening balance is only asked for when opening: afterwards it is a transaction.
            if (!form.isEditing) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(palette, stringResource(R.string.account_form_initial_amount_label))
                AmountField(
                    palette = palette,
                    value = form.initialAmountText,
                    // Edits that would break the amount's shape (letters, a third decimal...) are dropped.
                    onValueChange = { if (acceptsAmountInput(it)) onFormChange(form.copy(initialAmountText = it)) },
                    error = AccountFormError.INITIAL_AMOUNT_INVALID in errors,
                    textSize = 22.sp,
                )
                if (AccountFormError.INITIAL_AMOUNT_INVALID in errors)
                {
                    ErrorText(palette, stringResource(R.string.account_form_error_initial_amount))
                }
            }

            FormTextField(
                palette = palette,
                value = form.description,
                onValueChange = { onFormChange(form.copy(description = it)) },
                placeholder = stringResource(R.string.account_form_description_placeholder),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
            )

            SubmitButton(
                palette,
                stringResource(if (form.isEditing) R.string.account_form_submit_edit else R.string.account_form_submit),
                onSubmit,
            )
        }
    }
}

@Composable
private fun TypePicker(
    palette: AccountsPalette,
    selected: AccountType,
    onSelect: (AccountType) -> Unit,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, stringResource(R.string.account_form_type_label))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccountType.entries.forEach { type ->
                SubcategoryChip(
                    label = "${accountEmoji(type)} ${stringResource(accountTypeLabelRes(type))}",
                    selected = type == selected,
                    palette = palette,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}
