package com.kyovo.cents.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kyovo.cents.domain.port.input.RunAutomaticBackupUseCase
import java.io.IOException

/**
 * The weekly backup, as thin as the other worker: everything worth testing is in the use case. It is
 * scheduled whether or not the user chose a folder (the use case does nothing without one), so choosing
 * one needs no scheduling of its own. Built by [CentsWorkerFactory].
 */
class AutomaticBackupWorker(
    context: Context,
    params: WorkerParameters,
    private val runAutomaticBackup: RunAutomaticBackupUseCase,
) : CoroutineWorker(context, params)
{
    override suspend fun doWork(): Result
    {
        return try
        {
            runAutomaticBackup.run()
            Result.success()
        } catch (_: IOException)
        {
            // The folder could not be written (full, offline cloud provider, access revoked): try again
            // a little later, a few times, then leave it to the next week's run.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object
    {
        const val MAX_ATTEMPTS = 3
    }
}
