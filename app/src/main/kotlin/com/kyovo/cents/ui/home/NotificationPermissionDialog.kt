package com.kyovo.cents.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kyovo.cents.R

@Composable
internal fun NotificationPermissionDialog(
    title: String,
    body: String,
    palette: AccountsPalette,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
{
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.background,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.budgets_notification_confirm),
                    color = palette.textPrimary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.budgets_notification_delay),
                    color = palette.textSecondary
                )
            }
        }
    )
}