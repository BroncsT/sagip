package com.example.sagip_prototype;

import android.content.Context;
import android.content.Intent;
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
        if (context == null) {
            Log.w(TAG, "Context is null, cannot stop services");
            return;
        }
        
        Log.d(TAG, "Stopping all background services");
        
        try {
            // Stop all possible background services
            stopService(context, RescuerForegroundService.class);
            stopService(context, EmergencySOSBackgroundService.class);
            stopService(context, EmergencyNotificationService.class);
            stopService(context, HospitalStatusNotificationService.class);
            stopService(context, HospitalStatusReminderService.class);
            stopService(context, BackgroundNotificationService.class);
            stopService(context, FCMNotificationService.class);
            stopService(context, WebSocketNotificationService.class);
            stopService(context, RescuerBackgroundNotificationService.class);
            
            Log.d(TAG, "All background services stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping background services: " + e.getMessage());
        }
    }
    
    /**
     * Starts services specific to rescuers
     */
    private static void startRescuerServices(Context context) {
        Log.d(TAG, "Starting rescuer-specific services");
        
        // Start rescuer foreground service for reliable notifications
        startService(context, RescuerForegroundService.class);
        
        // Start emergency SOS background service
        startService(context, EmergencySOSBackgroundService.class);
        
        // Start emergency notification service for real-time SOS alerts
        startService(context, EmergencyNotificationService.class);
        
        // Start hospital status notification service
        startService(context, HospitalStatusNotificationService.class);
    }
    
    /**
     * Starts services specific to hospitals
     */
    private static void startHospitalServices(Context context) {
        Log.d(TAG, "Starting hospital-specific services");
        
        // Start hospital status reminder service
        startService(context, HospitalStatusReminderService.class);
        
        // Start background notification service
        startService(context, BackgroundNotificationService.class);
    }
    
    /**
     * Starts services specific to barangay
     */
    private static void startBarangayServices(Context context) {
        Log.d(TAG, "Starting barangay-specific services");
        
        // Start background notification service
        startService(context, BackgroundNotificationService.class);
    }
    
    /**
     * Starts services specific to seniors
     */
    private static void startSeniorServices(Context context) {
        Log.d(TAG, "Starting senior-specific services");
        
        // Start background notification service
        startService(context, BackgroundNotificationService.class);
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
     */
    private static void startService(Context context, Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(context, serviceClass);
            context.startService(serviceIntent);
            Log.d(TAG, "Started service: " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Error starting service " + serviceClass.getSimpleName() + ": " + e.getMessage());
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
