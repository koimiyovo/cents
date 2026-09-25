package com.kyovo.cents.ui.transaction

import com.kyovo.cents.ui.common.NameAndEmojiField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.SUBCATEGORY_EMOJIS
import com.kyovo.cents.ui.common.SubmitButton
import com.kyovo.cents.ui.common.limitNameInput
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.DialogCancelButton

/**
 * Creates a subcategory without leaving the transaction form: a name and an optional emoji, for the
 * kind the form records (the title says which). Stateless — the draft lives in [TransactionFormViewModel], so it
 * survives rotation and the refusals (blank or already-used name) are its rules, not the dialog's.
 */
@Composable
internal fun NewSubcategoryDialog(
    palette: AccountsPalette,
    kind: RecordableTransactionCategory,
    draft: NewSubcategoryDraft,
    onNameChange: (String) -> Unit,
    onEmojiChange: (String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    when (kind)
                    {
                        RecordableTransactionCategory.EXPENSE -> R.string.new_subcategory_title_expense
                        RecordableTransactionCategory.INCOME  -> R.string.new_subcategory_title_income
                    },
                ),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            NameAndEmojiField(
                palette = palette,
                name = draft.name,
                // Typing stops at the domain's limit: the field never holds a name it would refuse.
                onNameChange = { onNameChange(limitNameInput(it, SubcategoryName.MAX_LENGTH)) },
                placeholder = stringResource(R.string.new_subcategory_name_placeholder),
                emoji = draft.emoji,
                onEmojiChange = onEmojiChange,
                emojis = SUBCATEGORY_EMOJIS,
                emojiDescription = stringResource(R.string.new_subcategory_emoji_description),
                focusRequester = focus,
            )
            draft.error?.let {
                ErrorText(
                    palette = palette,
                    text = stringResource(
                        when (it)
                        {
                            NewSubcategoryError.NAME_REQUIRED -> R.string.new_subcategory_error_name_required
                            NewSubcategoryError.NAME_TAKEN    -> R.string.new_subcategory_error_name_taken
                            NewSubcategoryError.EMOJI_INVALID -> R.string.new_subcategory_error_emoji_invalid
                        },
                    ),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubmitButton(palette, stringResource(R.string.new_subcategory_create), onConfirm)
                DialogCancelButton(palette, onDismiss)
            }
        }
    }
}
