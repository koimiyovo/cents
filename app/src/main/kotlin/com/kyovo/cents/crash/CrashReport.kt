package com.kyovo.cents.crash

import java.time.Instant

/** Frames kept per failure: the top of a stack says where it happened, the rest is the framework. */
internal const val MAX_FRAMES = 30

/** Causes followed after the failure itself: a chain is rarely longer, and two failures may be each other's cause. */
internal const val MAX_CAUSES = 5

/** What a report says about the phone and the version of the app, to find the code (and the mapping file) again. */
data class DeviceInfo(val versionName: String, val versionCode: Int, val androidSdk: Int, val model: String)

/**
 * The text of a crash report: which version, on which phone, then the type of the failure and of its causes with
 * the lines of code they went through. **The messages are left out on purpose**: a library may quote in them what
 * it was reading (a backup file, a number or a date typed by the user), and the report goes out by e-mail.
 */
fun crashReport(failure: Throwable, at: Instant, device: DeviceInfo): String
{
    val report = StringBuilder()
    report.appendLine("Centimes ${device.versionName} (${device.versionCode})")
    report.appendLine("Android API ${device.androidSdk}, ${device.model}")
    report.appendLine(at)
    report.appendLine()

    var current: Throwable? = failure
    var causes = 0
    while (current != null && causes <= MAX_CAUSES)
    {
        report.appendLine(if (causes == 0) current.javaClass.name else "Caused by: ${current.javaClass.name}")
        current.stackTrace.take(MAX_FRAMES).forEach { report.appendLine("    at $it") }
        val left = current.stackTrace.size - MAX_FRAMES
        if (left > 0) report.appendLine("    ... $left more")
        current = current.cause
        causes++
    }
    return report.toString()
}
