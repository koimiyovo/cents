package com.kyovo.cents.ui.transaction

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.AmountField
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.SubmitButton
import com.kyovo.cents.ui.common.acceptsAmountInput
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * Bottom sheet to correct the amount of an account's opening deposit — nothing else about it can
 * change. Stateless, like [TransactionFormSheet]: the form lives in [InitialDepositFormViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialDepositFormSheet(
    form: InitialDepositFormState,
    showErrors: Boolean,
    failure: InitialDepositFailure?,
    onFormChange: (InitialDepositFormState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val amountInvalid = showErrors && form.submit() is InitialDepositSubmission.Invalid

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

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
                text = stringResource(R.string.initial_deposit_form_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.initial_deposit_form_hint),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
            AmountField(
                palette = palette,
                value = form.amountText,
                // Edits that would break the amount's shape (letters, a third decimal...) are dropped.
                onValueChange = { if (acceptsAmountInput(it)) onFormChange(form.copy(amountText = it)) },
                error = amountInvalid,
                focusRequester = amountFocus,
            )
            if (amountInvalid)
            {
                ErrorText(palette, stringResource(R.string.transaction_form_error_amount))
            }
            failure?.let {
                ErrorText(
                    palette = palette,
                    text = stringResource(
                        when (it)
                        {
                            InitialDepositFailure.DEPOSIT_UNAVAILABLE -> R.string.initial_deposit_form_failure_unavailable
                        },
                    ),
                )
            }
            SubmitButton(palette, stringResource(R.string.transaction_form_submit), onSubmit)
        }
    }
}
