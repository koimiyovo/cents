package com.kyovo.cents.notification

import java.time.LocalDate
import java.util.UUID

/**
 * The notification id of a recurring expense's occurrence on [day]: the same for the same rule and day, so
 * that telling the user twice on one day replaces the notification instead of piling up; different from
 * the next day's, so that a notification nobody dismissed yesterday is not silently replaced by today's
 * (a replacement does not alert again).
 */
// ponytail: a 32-bit hash, so two rules colliding on the same day would share a notification (a
// handful of rules make that very improbable); derive the id from a counter if that ever matters.
fun recurringTransactionNotificationId(ruleId: UUID, day: LocalDate): Int =
    ruleId.hashCode() * 31 + day.toEpochDay().toInt()
