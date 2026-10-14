package com.kyovo.cents.crash

import java.io.File

/**
 * Keeps the report of the last crash in a file of the app's private storage, until the user has been asked about
 * it: the process is gone when the app is opened again, so it has to be on disk. Only the last crash is kept.
 */
class CrashReportStore(private val file: File)
{
    fun save(report: String)
    {
        file.writeText(report)
    }

    fun read(): String?
    {
        return if (file.exists()) file.readText() else null
    }

    fun clear()
    {
        file.delete()
    }
}
