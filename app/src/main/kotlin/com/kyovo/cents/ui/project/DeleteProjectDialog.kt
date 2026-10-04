package com.kyovo.cents.ui.project

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
 * The confirmation before deleting a project. It says what happens: the project goes, and its transactions
 * are kept, without a project. Nothing gentler to offer, so a plain yes/no - but an informed one.
 */
@Composable
internal fun DeleteProjectDialog(
    palette: AccountsPalette,
    project: ProjectToDelete,
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
                text = stringResource(R.string.project_delete_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (project.transactionCount == 0)
                {
                    stringResource(R.string.project_delete_body_unused, project.name)
                } else
                {
                    pluralStringResource(
                        R.plurals.project_delete_body_used,
                        project.transactionCount,
                        project.name,
                        project.transactionCount,
                    )
                },
                color = palette.textSecondary,
                fontSize = 14.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DestructiveButton(palette, stringResource(R.string.project_delete_confirm), onConfirm)
                DialogCancelButton(palette, onDismiss)
            }
        }
    }
}
