package com.kyovo.cents.crash

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyovo.cents.BuildConfig
import com.kyovo.cents.R
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * Asked at the first opening after a crash. Nothing leaves the phone by itself: "Send" opens the user's e-mail
 * app with the report already written in, and it is the user who sends it, after reading it.
 */
@Composable
fun CrashReportDialog(
    onSend: () -> Unit,
    onIgnore: () -> Unit,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    AlertDialog(
        onDismissRequest = onIgnore,
        containerColor = palette.background,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = { Text(stringResource(R.string.crash_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.crash_report_intro))
                listOf(
                    R.string.crash_report_item_app,
                    R.string.crash_report_item_android,
                    R.string.crash_report_item_device,
                    R.string.crash_report_item_trace,
                ).forEach { item ->
                    Row {
                        Text("•", modifier = Modifier.width(20.dp))
                        Text(stringResource(item))
                    }
                }
                Text(stringResource(R.string.crash_report_outro))
            }
        },
        confirmButton = {
            TextButton(onClick = onSend) {
                Text(text = stringResource(R.string.crash_report_send), color = palette.textPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onIgnore) {
                Text(text = stringResource(R.string.crash_report_ignore), color = palette.textSecondary)
            }
        }
    )
}

/** Opens the e-mail app with [report] as the body, to the support address of the build (it may be empty). */
fun sendCrashReportByEmail(context: Context, report: String)
{
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SUPPORT_EMAIL).filter { it.isNotBlank() }.toTypedArray())
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject, BuildConfig.VERSION_NAME))
        putExtra(Intent.EXTRA_TEXT, report)
    }
    try
    {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException)
    {
        Toast.makeText(context, R.string.crash_report_no_mail_app, Toast.LENGTH_LONG).show()
    }
}
