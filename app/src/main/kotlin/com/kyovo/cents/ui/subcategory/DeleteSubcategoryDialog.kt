package com.kyovo.cents.ui.subcategory

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
import androidx.compose.ui.res.pluralStringResource
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
 * The confirmation before deleting a subcategory. It says what happens: the subcategory goes, and the
 * transactions that used it are kept, without a subcategory. Nothing gentler to offer (unlike an
 * account, a subcategory has no history to protect), so a plain yes/no — but an informed one.
 */
@Composable
internal fun DeleteSubcategoryDialog(
    palette: AccountsPalette,
    subcategory: SubcategoryToDelete,
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
                text = stringResource(R.string.subcategory_delete_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (subcategory.transactionCount == 0)
                {
                    stringResource(R.string.subcategory_delete_body_unused, subcategory.name)
                } else
                {
                    pluralStringResource(
                        R.plurals.subcategory_delete_body_used,
                        subcategory.transactionCount,
                        subcategory.name,
                        subcategory.transactionCount,
                    )
                },
                color = palette.textSecondary,
                fontSize = 14.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DestructiveButton(palette, stringResource(R.string.subcategory_delete_confirm), onConfirm)
                DialogCancelButton(palette, onDismiss)
            }
        }
    }
}
