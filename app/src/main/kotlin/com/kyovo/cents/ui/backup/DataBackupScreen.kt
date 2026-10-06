package com.kyovo.cents.ui.backup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.OutlinedFormButton
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.BackButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.DestructiveButton
import com.kyovo.cents.ui.home.DialogCancelButton
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * Exporting writes everything the user owns to a file they choose; importing replaces everything with
 * the content of a file. Both go through the system's file picker, which hands back the one place the
 * user picked: the app never needs access to the rest of the storage.
 */
@Composable
fun DataBackupScreen(
    state: DataBackupUiState,
    suggestedFileName: String,
    onBack: () -> Unit,
    onExportTo: (String) -> Unit,
    onAskToImport: () -> Unit,
    onDismissImportConfirmation: () -> Unit,
    onImportFrom: (String) -> Unit,
    onDismissResult: () -> Unit,
    automatic: AutomaticBackupUiState,
    onChooseAutomaticFolder: (String) -> Unit,
    onBackUpNow: () -> Unit,
    onDisableAutomatic: () -> Unit,
    onDismissAutomaticOutcome: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    val context = LocalContext.current
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette

    // A picker that is closed without choosing gives back null: nothing to do then.
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { onExportTo(it.toString()) } }
    // Any type is offered: a file saved from a cloud app or a file manager is not always labelled
    // "application/json". What is picked is checked completely before anything is replaced.
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onImportFrom(it.toString()) } }

    // The grant to a picked folder lasts only until the app restarts unless it is made persistent: without
    // it the weekly worker could write nowhere. A picker closed without choosing gives back null.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onChooseAutomaticFolder(it.toString())
        }
    }

    val busy = state.operation != BackupOperation.IDLE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(12.dp))
            HomeTopBar(palette, stringResource(R.string.backup_title))
        }

        state.result?.let {
            val failed = it is BackupResult.ExportFailed || it is BackupResult.ImportFailed
            ResultCard(palette, backupResultMessage(it), failed, onDismissResult)
        }

        automatic.outcome?.let {
            ResultCard(
                palette,
                if (it == AutomaticBackupOutcome.BACKED_UP) R.string.backup_auto_done else R.string.backup_auto_failed,
                failed = it == AutomaticBackupOutcome.FAILED,
                onDismiss = onDismissAutomaticOutcome,
            )
        }

        Card(palette) {
            Text(
                text = stringResource(R.string.backup_auto_title),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.backup_auto_hint),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
            if (automatic.folder == null)
            {
                OutlinedFormButton(palette, stringResource(R.string.backup_auto_choose)) { folderPicker.launch(null) }
            } else
            {
                Text(
                    text = stringResource(R.string.backup_auto_folder, folderDisplayName(automatic.folder)),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                )
                if (automatic.running)
                {
                    Progress(palette, stringResource(R.string.backup_auto_running))
                } else
                {
                    OutlinedFormButton(palette, stringResource(R.string.backup_auto_now), onClick = onBackUpNow)
                }
                OutlinedFormButton(palette, stringResource(R.string.backup_auto_change)) { folderPicker.launch(null) }
                OutlinedFormButton(palette, stringResource(R.string.backup_auto_disable), onClick = onDisableAutomatic)
            }
        }

        Card(palette) {
            Text(
                text = stringResource(R.string.backup_export_title),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.backup_export_hint),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
            if (state.operation == BackupOperation.EXPORTING)
            {
                Progress(palette, stringResource(R.string.backup_exporting))
            } else
            {
                OutlinedFormButton(palette, stringResource(R.string.backup_export_button))
                {
                    if (!busy) exportPicker.launch(suggestedFileName)
                }
            }
        }

        Card(palette) {
            Text(
                text = stringResource(R.string.backup_import_title),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.backup_import_hint),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
            if (state.operation == BackupOperation.IMPORTING)
            {
                Progress(palette, stringResource(R.string.backup_importing))
            } else
            {
                OutlinedFormButton(palette, stringResource(R.string.backup_import_button))
                {
                    if (!busy) onAskToImport()
                }
            }
        }
    }

    if (state.confirmingImport)
    {
        ImportConfirmationDialog(
            palette = palette,
            onConfirm = {
                onDismissImportConfirmation()
                importPicker.launch(arrayOf("*/*"))
            },
            onDismiss = onDismissImportConfirmation,
        )
    }
}

@Composable
private fun ImportConfirmationDialog(palette: AccountsPalette, onConfirm: () -> Unit, onDismiss: () -> Unit)
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
                text = stringResource(R.string.backup_import_confirm_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.backup_import_confirm_body),
                color = palette.textSecondary,
                fontSize = 14.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DestructiveButton(palette, stringResource(R.string.backup_import_confirm_button), onConfirm)
                DialogCancelButton(palette, onDismiss)
            }
        }
    }
}

@Composable
private fun ResultCard(palette: AccountsPalette, @StringRes message: Int, failed: Boolean, onDismiss: () -> Unit)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(message),
            color = if (failed) palette.error else palette.textPrimary,
            fontSize = 14.sp,
        )
        Text(
            text = stringResource(R.string.backup_result_dismiss),
            color = palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

internal fun backupResultMessage(result: BackupResult): Int = when (result)
{
    BackupResult.Exported -> R.string.backup_result_exported
    // The home screen words an import with its summary; this is the short form, for a card.
    is BackupResult.Imported -> R.string.backup_result_imported
    BackupResult.ExportFailed -> R.string.backup_result_export_failed
    is BackupResult.ImportFailed -> when (result.reason)
    {
        ImportFailure.NOT_A_BACKUP -> R.string.backup_result_import_not_a_backup
        ImportFailure.FILE_UNREADABLE -> R.string.backup_result_import_unreadable
        ImportFailure.STORAGE_FAILED -> R.string.backup_result_import_storage
    }
}

@Composable
private fun Progress(palette: AccountsPalette, label: String)
{
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(text = label, color = palette.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun Card(palette: AccountsPalette, content: @Composable () -> Unit)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
