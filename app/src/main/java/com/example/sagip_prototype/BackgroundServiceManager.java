package com.example.sagip_prototype;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * BackgroundServiceManager handles starting and stopping background services
 * based on user type and context.
 */
public class BackgroundServiceManager {
    
    private static final String TAG = "BackgroundServiceManager";
    
    /**
     * Starts appropriate background services based on user type
     * @param context The application context
     * @param userType The type of user (rescuer, hospital, barangay, seniors)
     */
    public static void startBackgroundServicesForUser(Context context, String userType) {
        if (context == null || userType == null) {
            Log.w(TAG, "Context or userType is null, cannot start services");
            return;
        }
        
        Log.d(TAG, "Starting background services for user type: " + userType);
        
        try {
            switch (userType) {
                case "rescuer":
                    startRescuerServices(context);
                    break;
                case "hospital":
                    startHospitalServices(context);
                    break;
                case "barangay":
                    startBarangayServices(context);
                    break;
                case "seniors":
                case "senior":
                    startSeniorServices(context);
                    break;
                default:
                    Log.w(TAG, "Unknown user type: " + userType + ", starting default services");
                    startDefaultServices(context);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting background services for user type " + userType + ": " + e.getMessage());
        }
    }
    
    /**
     * Stops all background services
     * @param context The application context
     */
    public static void stopAllBackgroundServices(Context context) {
        Log.d(TAG, "Stopping all background services");
        
        try {
            // Stop RescuerForegroundService
            Intent rescuerForegroundIntent = new Intent(context, RescuerForegroundService.class);
            context.stopService(rescuerForegroundIntent);
            
            // CRITICAL FIX: Stop BarangayForegroundService
            Intent barangayForegroundIntent = new Intent(context, BarangayForegroundService.class);
            context.stopService(barangayForegroundIntent);
            
            // CRITICAL FIX: Stop SeniorForegroundService
            Intent seniorForegroundIntent = new Intent(context, SeniorForegroundService.class);
            context.stopService(seniorForegroundIntent);
            
            // CRITICAL FIX: Stop HospitalForegroundService
            Intent hospitalForegroundIntent = new Intent(context, HospitalForegroundService.class);
            context.stopService(hospitalForegroundIntent);
            
            // Stop EmergencySOSBackgroundService
            Intent emergencySOSIntent = new Intent(context, EmergencySOSBackgroundService.class);
            context.stopService(emergencySOSIntent);
            
            // Stop RescuerBackgroundNotificationService
            Intent rescuerBackgroundIntent = new Intent(context, RescuerBackgroundNotificationService.class);
            context.stopService(rescuerBackgroundIntent);
            
            // Stop BackgroundNotificationService
            Intent backgroundIntent = new Intent(context, BackgroundNotificationService.class);
            context.stopService(backgroundIntent);
            
            // Stop EmergencyNotificationService
            Intent emergencyIntent = new Intent(context, EmergencyNotificationService.class);
            context.stopService(emergencyIntent);
            
            // Stop HospitalStatusNotificationService
            Intent hospitalStatusIntent = new Intent(context, HospitalStatusNotificationService.class);
            context.stopService(hospitalStatusIntent);
            
            // Stop HospitalStatusReminderService
            Intent hospitalReminderIntent = new Intent(context, HospitalStatusReminderService.class);
            context.stopService(hospitalReminderIntent);
            
            // Stop WorkManager
            NotificationWorkManager.stopNotificationMonitoring(context);
            
            // Cancel service restart alarm
            ServiceRestartAlarmReceiver.cancelAlarm(context);
            
            Log.d(TAG, "✅ All background services stopped - including WorkManager and alarms");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping background services: " + e.getMessage(), e);
        }
    }
    
    /**
     * Starts services specific to rescuers
     */
    private static void startRescuerServices(Context context) {
        Log.d(TAG, "🚨 Starting rescuer-specific services");
        
        // Start rescuer foreground service for reliable notifications
        startService(context, RescuerForegroundService.class);
        
        // Start emergency SOS background service
        startService(context, EmergencySOSBackgroundService.class);
        
        // DISABLED: EmergencyNotificationService causes DUPLICATE notifications
        // EmergencySOSBackgroundService already handles all emergency notifications
        // Keeping both services running creates double alerts for rescuers
        Log.d(TAG, "✅ [DUPLICATE_FIX] EmergencyNotificationService DISABLED to prevent duplicate notifications");
        // startService(context, EmergencyNotificationService.class); // COMMENTED OUT
        
        // Start hospital status notification service
        startService(context, HospitalStatusNotificationService.class);
        
        // Start WorkManager for emergency monitoring (backup mechanism)
        NotificationWorkManager.startNotificationMonitoring(context);
        NotificationWorkManager.startEmergencyMonitoring(context);
        Log.d(TAG, "✅ WorkManager emergency monitoring started for rescuer");
        
        // Schedule periodic service restart alarm (bypasses Doze mode)
        ServiceRestartAlarmReceiver.scheduleAlarm(context);
        Log.d(TAG, "✅ Service restart alarm scheduled for rescuer");
        
        // Check and request battery optimization whitelist
        BatteryOptimizationHelper.logBatteryOptimizationStatus(context);
    }
    
    /**
     * Starts services specific to hospitals
     */
    private static void startHospitalServices(Context context) {
        Log.d(TAG, "🏥 Starting hospital-specific services");
        
        // CRITICAL: Start dedicated hospital foreground service for reliable incoming emergency notifications
        // This ensures hospitals receive alerts about incoming patients even when app is closed
        startService(context, HospitalForegroundService.class);
        
        // Start hospital status reminder service
        startService(context, HospitalStatusReminderService.class);
        
        // Start background notification service as backup
        startService(context, BackgroundNotificationService.class);
        
        // Start WorkManager for notification monitoring
        NotificationWorkManager.startNotificationMonitoring(context);
        Log.d(TAG, "✅ WorkManager notification monitoring started for hospital");
        
        // Schedule periodic service restart alarm (bypasses Doze mode)
        ServiceRestartAlarmReceiver.scheduleAlarm(context);
        Log.d(TAG, "✅ Service restart alarm scheduled for hospital");
        
        // Check and request battery optimization whitelist
        BatteryOptimizationHelper.logBatteryOptimizationStatus(context);
        
        Log.d(TAG, "✅ Hospital services started - includes foreground service, WorkManager, and alarm for reliability");
    }
    
    /**
     * Starts services specific to barangay
     */
    private static void startBarangayServices(Context context) {
        Log.d(TAG, "🏢 Starting barangay-specific services");
        
        // CRITICAL FIX: Start dedicated barangay foreground service for reliable notifications
        // This ensures barangay officials receive emergency alerts even when app is closed
        startService(context, BarangayForegroundService.class);
        
        // Start background notification service as backup
        startService(context, BackgroundNotificationService.class);
        
        // Start WorkManager for notification and emergency monitoring
        NotificationWorkManager.startNotificationMonitoring(context);
        NotificationWorkManager.startEmergencyMonitoring(context);
        Log.d(TAG, "✅ WorkManager emergency monitoring started for barangay");
        
        // Schedule periodic service restart alarm (bypasses Doze mode)
        ServiceRestartAlarmReceiver.scheduleAlarm(context);
        Log.d(TAG, "✅ Service restart alarm scheduled for barangay");
        
        // Check and request battery optimization whitelist
        BatteryOptimizationHelper.logBatteryOptimizationStatus(context);
        
        Log.d(TAG, "✅ Barangay services started - includes foreground service, WorkManager, and alarm for reliability");
    }
    
    /**
     * Starts services specific to seniors
     */
    private static void startSeniorServices(Context context) {
        Log.d(TAG, "👴 Starting senior-specific services");
        
        // CRITICAL FIX: Start dedicated senior foreground service for reliable notifications
        // This ensures seniors receive rescuer response notifications even when app is closed
        startService(context, SeniorForegroundService.class);
        
        // Start background notification service as backup
        startService(context, BackgroundNotificationService.class);
        
        // Start WorkManager for notification monitoring
        NotificationWorkManager.startNotificationMonitoring(context);
        Log.d(TAG, "✅ WorkManager notification monitoring started for senior");
        
        // Schedule periodic service restart alarm (bypasses Doze mode)
        ServiceRestartAlarmReceiver.scheduleAlarm(context);
        Log.d(TAG, "✅ Service restart alarm scheduled for senior");
        
        // Check and request battery optimization whitelist
        BatteryOptimizationHelper.logBatteryOptimizationStatus(context);
        
        Log.d(TAG, "✅ Senior services started - includes foreground service, WorkManager, and alarm for reliability");
    }
    
    /**
     * Starts default services for unknown user types
     */
    private static void startDefaultServices(Context context) {
        Log.d(TAG, "Starting default services");
        
        // Start basic background notification service
        startService(context, BackgroundNotificationService.class);
    }
    
    /**
     * Helper method to start a service
     * Uses startForegroundService() on Android O+ to comply with background execution limits
     */
    private static void startService(Context context, Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(context, serviceClass);
            
            // Use startForegroundService() on Android O (API 26) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
                Log.d(TAG, "Started foreground service (API 26+): " + serviceClass.getSimpleName());
            } else {
                context.startService(serviceIntent);
                Log.d(TAG, "Started service: " + serviceClass.getSimpleName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting service " + serviceClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to stop a service
     */
    private static void stopService(Context context, Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(context, serviceClass);
            context.stopService(serviceIntent);
            Log.d(TAG, "Stopped service: " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Error stopping service " + serviceClass.getSimpleName() + ": " + e.getMessage());
        }
    }
}
