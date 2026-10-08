package com.kyovo.cents.ui.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.kyovo.cents.domain.port.output.BackupFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The folder the user picked with the system's folder picker (a `content://` tree Uri, as text), read and
 * written through [DocumentsContract]. The app only holds the grant for that folder (it must have been
 * persisted with `takePersistableUriPermission`, or it is gone after a restart), so it needs no storage
 * permission. Plain [DocumentsContract] rather than the `DocumentFile` library: four calls do not justify
 * a dependency. A refusal by the provider (a revoked grant, a folder deleted since) is an [IOException].
 */
class AndroidBackupFolder(private val resolver: ContentResolver) : BackupFolder
{
    override suspend fun write(folder: String, name: String, text: String)
    {
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(folder)
            // An existing file is overwritten in place: asking the provider to create a second one of the
            // same name would give "name (1).json".
            val file = find(tree, name)
                ?: guarded {
                    DocumentsContract.createDocument(resolver, rootOf(tree), "application/json", name)
                }
            // "wt": write and truncate, or a longer older content would leave its tail behind.
            val stream = guarded { resolver.openOutputStream(file, "wt") }
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    override suspend fun names(folder: String, startingWith: String): List<String>
    {
        return withContext(Dispatchers.IO) {
            children(Uri.parse(folder)).map { it.second }.filter { it.startsWith(startingWith) }
        }
    }

    override suspend fun delete(folder: String, name: String)
    {
        withContext(Dispatchers.IO) {
            val file = find(Uri.parse(folder), name) ?: return@withContext
            val deleted = guarded { DocumentsContract.deleteDocument(resolver, file) }
            if (!deleted) throw IOException("The file could not be deleted")
        }
    }

    private fun rootOf(tree: Uri): Uri
    {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun find(tree: Uri, name: String): Uri?
    {
        return children(tree).firstOrNull { it.second == name }?.first
    }

    /** The files directly inside the folder, each as its Uri and its display name. */
    private fun children(tree: Uri): List<Pair<Uri, String>>
    {
        val listing = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = guarded { resolver.query(listing, columns, null, null, null) }
        return cursor.use {
            buildList {
                while (it.moveToNext())
                {
                    add(DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)) to it.getString(1))
                }
            }
        }
    }

    // The provider may refuse in several ways, or hand back nothing: all of them are "the folder cannot be
    // used", which is what the caller handles.
    private fun <T> guarded(call: () -> T?): T
    {
        try
        {
            return call() ?: throw IOException("The folder could not be used")
        } catch (e: SecurityException)
        {
            throw IOException(e)
        } catch (e: IllegalArgumentException)
        {
            throw IOException(e)
        }
    }
}
