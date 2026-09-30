package com.kyovo.cents.work

import java.time.Duration
import java.time.ZonedDateTime

/**
 * How long to wait from [now] until the next [hour] o'clock (local time of [now]'s zone): today's if it
 * is still ahead, tomorrow's otherwise — exactly [hour]:00 counts as already passed, so a run that fires
 * on the dot is not scheduled again for the same instant. Used to have the daily check run in the
 * morning instead of at whatever time the app was first opened.
 */
fun delayUntilNext(hour: Int, now: ZonedDateTime): Duration
{
    val today = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
    val next = if (today.isAfter(now)) today else today.plusDays(1)
    return Duration.between(now, next)
}
