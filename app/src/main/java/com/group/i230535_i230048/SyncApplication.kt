package com.group.i230535_i230048 // Your main package

import android.app.Application
import androidx.work.*
import com.group.i230535_i230048.SyncWorker
import java.util.concurrent.TimeUnit

class SyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupRecurringWork()
    }

    private fun setupRecurringWork() {
        // Create constraints: only run when network is connected
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a periodic request to run our SyncWorker
        val syncRequest = PeriodicWorkRequest.Builder(
            SyncWorker::class.java,
            15, TimeUnit.MINUTES // Run every 15 minutes
        )
            .setConstraints(constraints)
            .build()

        // Schedule the unique work
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep the existing work
            syncRequest
        )
    }
}