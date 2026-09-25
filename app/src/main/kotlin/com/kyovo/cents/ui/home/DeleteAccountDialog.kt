package com.kyovo.cents.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.SubmitButton

/** What the deletion dialog has to offer for one account. */
internal data class DeleteChoices(
    /** Deleting takes the account's transactions with it (the domain must be told so explicitly). */
    val deletesTransactions: Boolean,
    /** Archiving is proposed as the gentler way out. */
    val offersArchive: Boolean,
)

/**
 * An account without transactions has no history to lose: deleting it is a plain yes/no, and
 * archiving would only be noise. One with transactions is different — deleting erases them for good
 * — so the dialog says so, and proposes archiving (which keeps everything) — unless the account is
 * already archived, where that would be no alternative at all.
 */
internal fun deleteChoices(transactionCount: Int, alreadyArchived: Boolean): DeleteChoices
{
    val hasTransactions = transactionCount > 0
    return DeleteChoices(
        deletesTransactions = hasTransactions,
        offersArchive = hasTransactions && !alreadyArchived,
    )
}

/**
 * The confirmation before deleting an account. With transactions, three ways out, stacked so that
 * none is squeezed: the safe one first and prominent ("Archiver à la place"), the destructive one
 * spelled out in the error colour, and an explicit "Annuler" — with three choices, tapping outside
 * the dialog is not an obvious way to say no.
 */
@Composable
internal fun DeleteAccountDialog(
    palette: AccountsPalette,
    accountName: String,
    transactionCount: Int,
    alreadyArchived: Boolean,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
)
{
    val choices = deleteChoices(transactionCount, alreadyArchived)
    val resources = LocalContext.current.resources
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
                text = stringResource(R.string.account_delete_title),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (choices.deletesTransactions)
                {
                    resources.getQuantityString(
                        R.plurals.account_delete_body_transactions, transactionCount, accountName, transactionCount,
                    )
                } else
                {
                    stringResource(R.string.account_delete_body_empty, accountName)
                },
                color = palette.textSecondary,
                fontSize = 14.sp,
            )
            if (choices.offersArchive)
            {
                Text(
                    text = stringResource(R.string.account_delete_archive_hint),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (choices.offersArchive)
                {
                    SubmitButton(palette, stringResource(R.string.account_delete_archive_instead), onArchive)
                }
                DestructiveButton(
                    palette = palette,
                    label = stringResource(
                        if (choices.deletesTransactions) R.string.account_delete_confirm_all
                        else R.string.account_delete_confirm,
                    ),
                    onClick = onDelete,
                )
                DialogCancelButton(palette, onDismiss)
            }
        }
    }
}

/** The plain "Annuler" that closes a confirmation dialog without doing anything. */
@Composable
internal fun DialogCancelButton(palette: AccountsPalette, onClick: () -> Unit)
{
    Text(
        text = stringResource(R.string.account_archive_dialog_cancel),
        color = palette.textSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

/** Outlined rather than filled: it must be clearly the dangerous one, not the inviting one. */
@Composable
internal fun DestructiveButton(palette: AccountsPalette, label: String, onClick: () -> Unit)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.5.dp, palette.error), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = palette.error,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
