package com.kyovo.cents.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyovo.cents.R
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.DestructiveButton
import com.kyovo.cents.ui.home.DialogCancelButton

/**
 * The confirmation before deleting a recurring-expense rule. It says what happens: only the rule
 * goes, the transactions it already generated stay as ordinary transactions — nothing to lose there,
 * so a plain yes/no, but an informed one (same reasoning as [com.kyovo.cents.ui.subcategory.DeleteSubcategoryDialog]).
 */
@Composable
internal fun DeleteRecurringTransactionDialog(
    palette: AccountsPalette,
    rule: RecurringTransactionToDelete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
{
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.recurring_delete_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.recurring_delete_body, rule.title),
                color = palette.textSecondary,
                fontSize = 14.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DestructiveButton(palette, stringResource(R.string.recurring_delete_confirm), onConfirm)
                DialogCancelButton(palette, onDismiss)
            }
        }
    }
}
