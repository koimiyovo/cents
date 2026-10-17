package com.kyovo.cents.ui.budget

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.ExportIcon
import com.kyovo.cents.ui.home.AccountsPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * The export icon of a chart, the same round button as the top bars': a tap offers "Exporter en image" and
 * "Exporter en PDF", each opening the system's "save as" screen and then calling [write] on the chosen file,
 * with the outcome said in the snackbar ([onMessage]). It sits at the top of the chart, where it is always in
 * reach, however long the legend below is. What is drawn is [write]'s business, which is why the cash-flow
 * diagram and the spending ring share it.
 */
@Composable
internal fun ChartExportMenu(
    palette: AccountsPalette,
    fileName: String,
    onMessage: (Int) -> Unit,
    write: (OutputStream, CashFlowFormat) -> Unit,
)
{
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    // The pickers' callbacks live as long as the screen: they must call the latest lambdas, not the first ones.
    val currentWrite by rememberUpdatedState(write)
    val currentOnMessage by rememberUpdatedState(onMessage)

    fun save(uri: Uri?, format: CashFlowFormat)
    {
        if (uri == null) return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = checkNotNull(resolver.openOutputStream(uri, "wt")) { "No stream for the file" }
                    stream.use { currentWrite(it, format) }
                }.isSuccess
            }
            currentOnMessage(if (saved) R.string.cashflow_exported else R.string.cashflow_export_failed)
        }
    }

    // Sharing writes the chart in the cache, where the file provider can hand it to the share sheet.
    val context = LocalContext.current
    fun share(format: CashFlowFormat)
    {
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(context.cacheDir, "shared_charts").apply { deleteRecursively(); mkdirs() }
                    val file = File(directory, "$fileName.${format.extension}")
                    file.outputStream().use { currentWrite(it, format) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull()
            }
            if (uri == null)
            {
                currentOnMessage(R.string.cashflow_export_failed)
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null))
        }
    }

    val pngPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CashFlowFormat.PNG.mimeType)) {
        save(it, CashFlowFormat.PNG)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CashFlowFormat.PDF.mimeType)) {
        save(it, CashFlowFormat.PDF)
    }

    val description = stringResource(R.string.chart_export_description)
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            ExportIcon(tint = palette.textPrimary, modifier = Modifier.size(24.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = palette.surface) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cashflow_export_png), color = palette.textPrimary, fontSize = 15.sp) },
                onClick = {
                    open = false
                    pngPicker.launch("$fileName.${CashFlowFormat.PNG.extension}")
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cashflow_export_pdf), color = palette.textPrimary, fontSize = 15.sp) },
                onClick = {
                    open = false
                    pdfPicker.launch("$fileName.${CashFlowFormat.PDF.extension}")
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chart_share_png), color = palette.textPrimary, fontSize = 15.sp) },
                onClick = {
                    open = false
                    share(CashFlowFormat.PNG)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chart_share_pdf), color = palette.textPrimary, fontSize = 15.sp) },
                onClick = {
                    open = false
                    share(CashFlowFormat.PDF)
                },
            )
        }
    }
}
