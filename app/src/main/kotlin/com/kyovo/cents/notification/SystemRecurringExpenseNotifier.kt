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
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.port.output.RecurringExpenseNotifier
import com.kyovo.cents.ui.common.formatEuroCents
import java.time.LocalDate

private const val CHANNEL_ID = "recurring_expenses"

/**
 * Posts a real Android notification for a recurring expense that falls today — the Android-bound half of
 * the daily check (which rules are due is [com.kyovo.cents.application.usecase.NotifyDueRecurringExpensesService],
 * tested without any of this). Same shape as [SystemBudgetAlertNotifier]: the channel is created up front,
 * and without the `POST_NOTIFICATIONS` permission (a runtime permission from Android 13 on, answered as
 * granted before that) nothing is posted — the expense itself is recorded either way.
 *
 * One notification per rule and day ([recurringExpenseNotificationId]), `setOnlyAlertOnce`: asking twice
 * on the same day replaces the notification silently, and that is all the deduplication there is.
 */
class SystemRecurringExpenseNotifier(
    private val context: Context,
    private val today: () -> LocalDate = { LocalDate.now() },
) : RecurringExpenseNotifier
{
    init
    {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.recurring_expense_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    override suspend fun notify(recurringExpense: RecurringExpense)
    {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        {
            return
        }

        val day = today()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.recurring_expense_title))
            .setContentText(
                context.getString(
                    R.string.recurring_expense_body,
                    recurringExpense.title.value,
                    formatEuroCents(recurringExpense.amount.value),
                ),
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(recurringExpenseNotificationId(recurringExpense.id.value, day), notification)
    }
}
