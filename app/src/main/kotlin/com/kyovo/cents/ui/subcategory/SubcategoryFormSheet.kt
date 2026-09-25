package com.kyovo.cents.ui.subcategory

import com.kyovo.cents.ui.common.NameAndEmojiField
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.SUBCATEGORY_EMOJIS
import com.kyovo.cents.ui.common.SubmitButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.DestructiveButton
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * Bottom sheet to create or edit a subcategory: a name and an optional emoji. An edit also shows
 * the (fixed) kind, says how many transactions a rename reaches, and offers the deletion.
 * Stateless, like the other sheets: the form lives in [SubcategoriesViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubcategoryFormSheet(
    form: SubcategoryFormState,
    errors: Set<SubcategoryFormError>,
    editedTransactionCount: Int?,
    onFormChange: (SubcategoryFormState) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    val isExpense = form.kind == RecordableTransactionCategory.EXPENSE

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
                    when
                    {
                        form.isEditing -> R.string.subcategory_form_title_edit
                        isExpense      -> R.string.new_subcategory_title_expense
                        else           -> R.string.new_subcategory_title_income
                    },
                ),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (form.isEditing)
            {
                // The kind is chosen when a subcategory is created and never changes: shown, not editable.
                Text(
                    text = stringResource(
                        R.string.subcategory_form_kind,
                        stringResource(
                            if (isExpense) R.string.transaction_form_type_expense else R.string.transaction_form_type_income,
                        ),
                    ),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                )
                if (editedTransactionCount != null && editedTransactionCount > 0)
                {
                    Text(
                        text = pluralStringResource(
                            R.plurals.subcategory_form_rename_hint,
                            editedTransactionCount,
                            editedTransactionCount,
                        ),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            NameAndEmojiField(
                palette = palette,
                name = form.name,
                // Typing stops at the domain's limit (see SubcategoryFormState.withName).
                onNameChange = { onFormChange(form.withName(it)) },
                placeholder = stringResource(R.string.new_subcategory_name_placeholder),
                emoji = form.emoji,
                onEmojiChange = { onFormChange(form.withEmoji(it)) },
                emojis = SUBCATEGORY_EMOJIS,
                emojiDescription = stringResource(R.string.new_subcategory_emoji_description),
                focusRequester = nameFocus,
            )
            errors.forEach { error ->
                ErrorText(
                    palette = palette,
                    text = when (error)
                    {
                        SubcategoryFormError.NAME_REQUIRED     -> stringResource(R.string.new_subcategory_error_name_required)
                        SubcategoryFormError.NAME_TOO_LONG     ->
                            stringResource(R.string.subcategory_form_error_name_too_long, SubcategoryName.MAX_LENGTH)

                        SubcategoryFormError.EMOJI_INVALID     -> stringResource(R.string.new_subcategory_error_emoji_invalid)
                        SubcategoryFormError.NAME_TAKEN        -> stringResource(R.string.new_subcategory_error_name_taken)
                        SubcategoryFormError.SUBCATEGORY_GONE  -> stringResource(R.string.subcategory_form_error_gone)
                    },
                )
            }
            SubmitButton(
                palette,
                stringResource(if (form.isEditing) R.string.transaction_form_submit else R.string.new_subcategory_create),
                onSubmit,
            )
            // Only for a subcategory that exists: there is nothing to delete in a new one.
            if (form.isEditing)
            {
                DestructiveButton(palette, stringResource(R.string.subcategory_form_delete), onDelete)
            }
        }
    }
}
