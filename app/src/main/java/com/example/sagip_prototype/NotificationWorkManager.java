package com.example.sagip_prototype;

import android.content.Context;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/**
 * Manages WorkManager tasks for notification monitoring.
 * This provides a reliable backup mechanism for receiving notifications
 * even when foreground services are killed by the system.
 */
public class NotificationWorkManager {
    private static final String TAG = "NotificationWorkManager";
    private static final String WORK_NAME = "notification_check_work";
    private static final String EMERGENCY_WORK_NAME = "emergency_notification_check_work";
    
    /**
     * Start regular notification monitoring (every 15 minutes)
     */
    public static void startNotificationMonitoring(Context context) {
        Log.d(TAG, "Starting WorkManager notification monitoring");
        
        // Create constraints for the work
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
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
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing to avoid resets
            notificationWork
        );
        
        Log.d(TAG, "✅ WorkManager notification monitoring started (every 15 min)");
    }
    
    /**
     * Start emergency notification monitoring (every 15 minutes - minimum allowed)
     * This specifically checks for emergency SOS notifications and has NO battery constraints
     * to ensure rescuers always receive emergency alerts.
     */
    public static void startEmergencyMonitoring(Context context) {
        Log.d(TAG, "🚨 Starting WorkManager EMERGENCY notification monitoring");
        
        // CRITICAL: No battery constraints for emergency notifications
        // We only require network connectivity
        Constraints emergencyConstraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // NO setRequiresBatteryNotLow() - emergencies can't wait for battery!
            // NO setRequiresCharging() - emergencies can happen anytime!
            .build();
        
        // Create periodic work request for emergency checks
        // Minimum interval for PeriodicWorkRequest is 15 minutes
        PeriodicWorkRequest emergencyWork = new PeriodicWorkRequest.Builder(
            EmergencyNotificationWorker.class,
            15, // minimum allowed interval
            TimeUnit.MINUTES
        )
        .setConstraints(emergencyConstraints)
        .addTag("emergency_notification_check")
        .build();
        
        // Enqueue the emergency work
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EMERGENCY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing to avoid resets
            emergencyWork
        );
        
        Log.d(TAG, "✅ WorkManager EMERGENCY notification monitoring started (every 15 min, no battery constraints)");
    }
    
    /**
     * Stop all notification monitoring
     */
    public static void stopNotificationMonitoring(Context context) {
        Log.d(TAG, "Stopping WorkManager notification monitoring");
        
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        WorkManager.getInstance(context).cancelUniqueWork(EMERGENCY_WORK_NAME);
        
        Log.d(TAG, "✅ WorkManager notification monitoring stopped");
    }
    
    /**
     * Stop only emergency monitoring (called on logout)
     */
    public static void stopEmergencyMonitoring(Context context) {
        Log.d(TAG, "Stopping WorkManager EMERGENCY notification monitoring");
        
        WorkManager.getInstance(context).cancelUniqueWork(EMERGENCY_WORK_NAME);
        
        Log.d(TAG, "✅ WorkManager EMERGENCY notification monitoring stopped");
    }
    
    /**
     * Trigger an immediate notification check
     */
    public static void startImmediateCheck(Context context) {
        Log.d(TAG, "Starting immediate notification check");
        
        // Create constraints for immediate work
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        
        // Create one-time work request for regular notifications
        androidx.work.OneTimeWorkRequest immediateWork = new androidx.work.OneTimeWorkRequest.Builder(
            NotificationWorker.class
        )
        .setConstraints(constraints)
        .addTag("immediate_notification_check")
        .build();
        
        // Create one-time work request for emergency notifications
        androidx.work.OneTimeWorkRequest immediateEmergencyWork = new androidx.work.OneTimeWorkRequest.Builder(
            EmergencyNotificationWorker.class
        )
        .setConstraints(constraints)
        .addTag("immediate_emergency_check")
        .build();
        
        // Enqueue both work requests
        WorkManager.getInstance(context).enqueue(immediateWork);
        WorkManager.getInstance(context).enqueue(immediateEmergencyWork);
        
        Log.d(TAG, "✅ Immediate notification checks started (regular + emergency)");
    }
    
    /**
     * Trigger an immediate emergency check only
     */
    public static void startImmediateEmergencyCheck(Context context) {
        Log.d(TAG, "🚨 Starting immediate EMERGENCY notification check");
        
        // No constraints for emergency - we want it to run ASAP
        androidx.work.OneTimeWorkRequest immediateEmergencyWork = new androidx.work.OneTimeWorkRequest.Builder(
            EmergencyNotificationWorker.class
        )
        .addTag("immediate_emergency_check")
        .build();
        
        WorkManager.getInstance(context).enqueue(immediateEmergencyWork);
        
        Log.d(TAG, "✅ Immediate EMERGENCY notification check started");
    }
}
