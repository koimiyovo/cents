package com.kyovo.cents.crash

/**
 * Writes the report of a crash, then hands the crash on to the handler that was there before ([next], the system's,
 * which closes the app and tells Android vitals), so nothing else changes. Reporting must never get in the way: any
 * failure while building or writing the report is dropped, and [next] always receives the original failure.
 */
class CrashHandler(
    private val store: CrashReportStore,
    private val report: (Throwable) -> String,
    private val next: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler
{
    override fun uncaughtException(thread: Thread, failure: Throwable)
    {
        try
        {
            store.save(report(failure))
        } catch (_: Throwable)
        {
            // Nothing more to do: the crash itself matters more than its report.
        }
        next?.uncaughtException(thread, failure)
    }
}
