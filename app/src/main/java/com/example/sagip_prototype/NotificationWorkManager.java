package com.example.sagip_prototype;

import android.content.Context;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class NotificationWorkManager {
    private static final String TAG = "NotificationWorkManager";
    private static final String WORK_NAME = "notification_check_work";
    
    public static void startNotificationMonitoring(Context context) {
        Log.d(TAG, "Starting WorkManager notification monitoring");
        
        // Create constraints for the work
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build();
        
        // Create periodic work request - runs every 15 minutes
        PeriodicWorkRequest notificationWork = new PeriodicWorkRequest.Builder(
            NotificationWorker.class,
            15, // repeat interval
            TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .addTag("notification_check")
        .build();
        
        // Enqueue the work
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            notificationWork
        );
        
        Log.d(TAG, "WorkManager notification monitoring started");
    }
    
    public static void stopNotificationMonitoring(Context context) {
        Log.d(TAG, "Stopping WorkManager notification monitoring");
        
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        
        Log.d(TAG, "WorkManager notification monitoring stopped");
    }
    
    public static void startImmediateCheck(Context context) {
        Log.d(TAG, "Starting immediate notification check");
        
        // Create constraints for immediate work
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        
        // Create one-time work request
        androidx.work.OneTimeWorkRequest immediateWork = new androidx.work.OneTimeWorkRequest.Builder(
            NotificationWorker.class
        )
        .setConstraints(constraints)
        .addTag("immediate_notification_check")
        .build();
        
        // Enqueue the work
        WorkManager.getInstance(context).enqueue(immediateWork);
        
        Log.d(TAG, "Immediate notification check started");
    }
}
