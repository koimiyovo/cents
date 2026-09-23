package com.kyovo.cents.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.home.AccountsPalette

// Field building blocks shared by the bottom-sheet forms (new transaction, new account).

@Composable
internal fun AmountField(
    palette: AccountsPalette,
    value: String,
    onValueChange: (String) -> Unit,
    error: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    textSize: TextUnit = 32.sp,
)
{
    Row(
        modifier = modifier
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
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(palette.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { innerField ->
                Box {
                    if (value.isEmpty())
                    {
                        Text(
                            text = stringResource(R.string.transaction_form_amount_placeholder),
                            color = palette.textMuted,
                            fontSize = textSize,
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
internal fun FormTextField(
    palette: AccountsPalette,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    singleLine: Boolean = true,
    focusRequester: FocusRequester? = null,
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
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

@Composable
internal fun SectionLabel(palette: AccountsPalette, text: String)
{
    Text(text = text, color = palette.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun ErrorText(palette: AccountsPalette, text: String)
{
    Text(text = text, color = palette.error, fontSize = 12.sp)
}

@Composable
internal fun SubmitButton(palette: AccountsPalette, label: String, onClick: () -> Unit)
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
            text = label,
            color = palette.heroOnCardPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
