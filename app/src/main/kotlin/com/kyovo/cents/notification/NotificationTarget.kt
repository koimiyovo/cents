package com.kyovo.cents.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kyovo.cents.MainActivity

/**
 * Where a tap on one of the app's notifications lands: the tab that shows what the notification is about.
 * Carried as the enum's name in an intent extra, since an intent can only hold plain values.
 */
enum class NotificationTarget
{
    /** A budget alert: the Budget tab. */
    Budget,

    /** A recurring transaction that was just recorded: the Transactions tab, where it now is. */
    Transactions;

    companion object
    {
        const val EXTRA = "com.kyovo.cents.notification_target"

        /** The target an intent carried, or null when it carried none or one this version does not know. */
        fun from(name: String?): NotificationTarget?
        {
            return entries.firstOrNull { it.name == name }
        }
    }
}

/**
 * What a notification runs when tapped: the app's activity, told which tab to show. One request code per
 * target, or the system would hand back the first pending intent for both and drop the second one's extra.
 * `SINGLE_TOP` + `CLEAR_TOP` reuse the running activity ([MainActivity.onNewIntent]) instead of stacking a new one.
 */
fun openAppPendingIntent(context: Context, target: NotificationTarget): PendingIntent
{
    val intent = Intent(context, MainActivity::class.java)
        .putExtra(NotificationTarget.EXTRA, target.name)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
        context,
        target.ordinal,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
