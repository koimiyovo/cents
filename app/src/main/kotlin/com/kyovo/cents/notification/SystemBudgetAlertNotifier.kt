package com.kyovo.cents.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.port.output.BudgetAlertNotifier
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji

private const val CHANNEL_ID = "budget_alerts"

/**
 * Posts a real Android notification for a budget alert — the only Android-framework-bound piece of the
 * WorkManager check, on purpose: which alerts to raise ([com.kyovo.cents.application.usecase.NotifyBudgetAlertsService])
 * is already fully tested without any of this. The wording needs the subcategory's name and emoji, which
 * [BudgetAlert] itself does not carry (only its id, like [com.kyovo.cents.domain.model.Budget] itself is
 * keyed) — resolved here, right before wording the notification, rather than widening the domain alert.
 *
 * `minSdk` is 30, so a notification channel always exists (no pre-26 fallback needed) and
 * `POST_NOTIFICATIONS` is only a real runtime permission from 33 on — [ContextCompat.checkSelfPermission]
 * answers granted on older versions without asking. A subcategory deleted between the check and this call
 * (its alerts should already be gone with it, but a slow-running check could still race it) is silently
 * skipped, same as elsewhere in the app.
 */
class SystemBudgetAlertNotifier(
    private val context: Context,
    private val subcategoryRepository: SubcategoryRepository,
) : BudgetAlertNotifier
{
    init
    {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.budget_alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    override suspend fun notify(alert: BudgetAlert)
    {
        val subcategory = subcategoryRepository.findById(alert.subcategoryId) ?: return
        val emoji = subcategory.emoji?.value ?: defaultSubcategoryEmoji(subcategory.kind)
        val bodyRes = when (alert.level)
        {
            BudgetAlertLevel.CLOSE_TO_LIMIT -> R.string.budget_alert_close_body
            BudgetAlertLevel.OVER -> R.string.budget_alert_over_body
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.budget_alert_title))
            .setContentText(context.getString(bodyRes, emoji, subcategory.name.value))
            .setContentIntent(openAppPendingIntent(context, NotificationTarget.Budget))
            .setAutoCancel(true)
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        {
            return
        }
        NotificationManagerCompat.from(context).notify(notificationId(alert), notification)
    }

    /** One notification per (subcategory, level): a later CLOSE_TO_LIMIT and OVER of the same
     * subcategory are two distinct notifications, but re-raising the same one replaces it rather
     * than piling up (not that NotifyBudgetAlertsService ever asks for the same crossing twice). */
    private fun notificationId(alert: BudgetAlert): Int =
        alert.subcategoryId.value.hashCode() * 31 + alert.level.ordinal
}
