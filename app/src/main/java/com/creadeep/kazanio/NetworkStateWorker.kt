package com.creadeep.kazanio

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Used to perform tasks when special conditions are met (e.g. network connected).
 */
class NetworkStateWorker(val context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {

    override fun doWork(): Result {
        // Check results by triggering AlarmReceiver
        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra("requestCode", 1)
        context.sendBroadcast(intent)
        return Result.success()
    }
}
