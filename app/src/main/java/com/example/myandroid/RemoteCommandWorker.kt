package com.example.myandroid

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RemoteCommandWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Delegate all logic to the central CommandProcessor.
        // This architecture allows both this background worker (15-min interval)
        // and the BeaconService (Turbo Mode) to share the same execution logic.
        CommandProcessor.checkAndExecute(applicationContext)
        return Result.success()
    }
}
