package workers

import android.content.Context
import androidx.work.*

object UploadScheduler {
    private const val UNIQUE_NAME = "upload_scans"

    fun enqueueOnce(context: Context){
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun shedulePeriodic(context: Context){
        val request = PeriodicWorkRequestBuilder<UploadWorker>(
            repeatInterval = java.time.Duration.ofMinutes(15)
        )
            .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "${UNIQUE_NAME}_periodic",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}