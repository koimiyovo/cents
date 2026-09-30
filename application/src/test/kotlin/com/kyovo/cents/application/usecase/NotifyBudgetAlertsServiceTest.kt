package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.port.input.CheckBudgetAlertsUseCase
import com.kyovo.cents.domain.port.output.BudgetAlertNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.YearMonth

/** Answers with a fixed list of alerts, whatever the month asked. */
private class FakeCheckBudgetAlerts(private val alerts: List<BudgetAlert>) : CheckBudgetAlertsUseCase
{
    override suspend fun check(month: YearMonth): List<BudgetAlert> = alerts
}

/** Records what it is asked to notify. */
private class RecordingNotifier : BudgetAlertNotifier
{
    val notified = mutableListOf<BudgetAlert>()

    override suspend fun notify(alert: BudgetAlert)
    {
        notified += alert
    }
}

/**
 * What the periodic WorkManager check actually does: ask CheckBudgetAlertsUseCase which crossings are
 * new this month, and notify each of them. This is the piece the worker itself calls — the worker stays
 * a thin, untested Android shell (like RoomPersistence.open or MainActivity), since this service is
 * where the real orchestration lives, testable without anything Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotifyBudgetAlertsServiceTest
{
    private val september = YearMonth.of(2026, 9)

    @Test
    fun `notifies every alert the check reports`() = runTest()
    {
        // GIVEN
        val close = BudgetAlert(
            aSubcategoryId("11111111-1111-1111-1111-111111111111"), september, BudgetAlertLevel.CLOSE_TO_LIMIT
        )
        val over = BudgetAlert(
            aSubcategoryId("22222222-2222-2222-2222-222222222222"), september, BudgetAlertLevel.OVER
        )
        val notifier = RecordingNotifier()
        val service = NotifyBudgetAlertsService(FakeCheckBudgetAlerts(listOf(close, over)), notifier)

        // WHEN
        service.notify(september)

        // THEN
        assertThat(notifier.notified).containsExactly(close, over)
    }

    @Test
    fun `notifies nothing when the check reports no alert`() = runTest()
    {
        // GIVEN
        val notifier = RecordingNotifier()
        val service = NotifyBudgetAlertsService(FakeCheckBudgetAlerts(emptyList()), notifier)

        // WHEN
        service.notify(september)

        // THEN
        assertThat(notifier.notified).isEmpty()
    }
}
