package dk.Android.Cpmonster.mgmanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dk.Android.Cpmonster.mgmanager.MGApp
import dk.Android.Cpmonster.mgmanager.queue.JobType
import java.util.concurrent.TimeUnit

/** Drains the command queue when the app isn't running. */
class QueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as MGApp).container.queue.drain()
        return Result.success()
    }

    companion object {
        const val NAME = "queue-drain"
    }
}

/** Background poll of car type, temperature and battery. */
class StatusPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as MGApp).container
        if (c.auth.isLoggedIn()) {
            c.queue.enqueue(JobType.REFRESH_STATUS)
            c.queue.drain()
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "status-poll"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StatusPollWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
