package com.kyovo.cents.crash

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class CrashReportTest
{
    private val device = DeviceInfo(versionName = "1.0", versionCode = 4, androidSdk = 34, model = "Google Pixel 8")
    private val at = Instant.parse("2026-10-14T19:00:00Z")

    /** A failure with the given message and a stack of "class#method:line" frames. */
    private fun failure(type: (String) -> Throwable, message: String, vararg frames: String): Throwable
    {
        val failure = type(message)
        failure.stackTrace = frames.map {
            val (className, methodAndLine) = it.split("#")
            val (method, line) = methodAndLine.split(":")
            StackTraceElement(className, method, "${className.substringAfterLast('.')}.kt", line.toInt())
        }.toTypedArray()
        return failure
    }

    @Test
    fun `says which version of the app, of Android and which device it comes from`()
    {
        // WHEN
        val report = crashReport(failure(::IllegalStateException, ""), at, device)

        // THEN
        assertThat(report).contains("Centimes 1.0 (4)")
        assertThat(report).contains("Android API 34")
        assertThat(report).contains("Google Pixel 8")
        assertThat(report).contains("2026-10-14T19:00:00Z")
    }

    @Test
    fun `gives the type of the failure and where it happened`()
    {
        // GIVEN
        val failure = failure(
            ::IllegalStateException, "",
            "com.kyovo.cents.ui.Foo#bar:12",
            "com.kyovo.cents.ui.Baz#qux:7",
        )

        // WHEN
        val report = crashReport(failure, at, device)

        // THEN the frames come in the order of the stack
        assertThat(report).contains("java.lang.IllegalStateException")
        assertThat(report).containsSubsequence(
            "    at com.kyovo.cents.ui.Foo.bar(Foo.kt:12)",
            "    at com.kyovo.cents.ui.Baz.qux(Baz.kt:7)",
        )
    }

    // A message can quote what the user typed or what a library read in their data (a backup file, a number, a date):
    // the report is sent by e-mail, so it keeps the types and the places, never the words.
    @Test
    fun `never gives the message of a failure`()
    {
        // GIVEN a message with a name and an amount
        val failure = failure(::IllegalStateException, "Compte Épargne d'Alice : 1234,56 €", "a.B#c:1")

        // WHEN
        val report = crashReport(failure, at, device)

        // THEN
        assertThat(report).doesNotContain("Alice").doesNotContain("1234").doesNotContain("Épargne")
    }

    @Test
    fun `follows the causes, without their messages either`()
    {
        // GIVEN a failure caused by another, itself with a message made of the data
        val cause = failure(::NumberFormatException, "For input string: \"12,5x\"", "a.Parser#parse:3")
        val failure = failure(::IllegalArgumentException, "Backup of Alice", "a.Import#run:9").apply { initCause(cause) }

        // WHEN
        val report = crashReport(failure, at, device)

        // THEN
        assertThat(report).contains("Caused by: java.lang.NumberFormatException")
        assertThat(report).contains("    at a.Parser.parse(Parser.kt:3)")
        assertThat(report).doesNotContain("12,5x").doesNotContain("Alice")
    }

    @Test
    fun `keeps a long stack to its first lines and says how many it left out`()
    {
        // GIVEN a stack of 100 frames
        val frames = (1..100).map { "a.B#m$it:$it" }.toTypedArray()

        // WHEN
        val report = crashReport(failure(::IllegalStateException, "", *frames), at, device)

        // THEN
        assertThat(report.lines().count { it.startsWith("    at ") }).isEqualTo(MAX_FRAMES)
        assertThat(report).contains("    ... ${100 - MAX_FRAMES} more")
        assertThat(report).contains("a.B.m1(B.kt:1)").doesNotContain("a.B.m100(B.kt:100)")
    }

    @Test
    fun `stops following causes after a few, so a loop of causes ends`()
    {
        // GIVEN two failures that are each other's cause
        val first = failure(::IllegalStateException, "", "a.B#first:1")
        val second = failure(::IllegalStateException, "", "a.B#second:2")
        first.initCause(second)
        second.initCause(first)

        // WHEN
        val report = crashReport(first, at, device)

        // THEN
        assertThat(report.lines().count { it.startsWith("Caused by: ") }).isEqualTo(MAX_CAUSES)
    }
}
