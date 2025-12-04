package com.example.sagip_prototype;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * BootReceiver restarts all necessary background services after device boot
 * or app update to ensure emergency notifications continue working reliably.
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_SERVICE_RUNNING = "rescuerServiceRunning";
    private static final String KEY_BARANGAY_SERVICE_RUNNING = "barangayServiceRunning";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "🔄 BootReceiver received action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            
            Log.d(TAG, "Device boot completed or app updated, checking if services should restart");
            
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            
            String userId = sharedPreferences.getString(KEY_USER_ID, null);
            String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
            boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
            boolean isLoggedOut = userPrefs.getBoolean("user_logged_out", false);
            boolean serviceWasRunning = sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false);
            boolean barangayServiceWasRunning = sharedPreferences.getBoolean(KEY_BARANGAY_SERVICE_RUNNING, false);
            
            Log.d(TAG, "User ID: " + userId);
            Log.d(TAG, "User Type: " + userType);
            Log.d(TAG, "Is Logged In: " + isLoggedIn);
            Log.d(TAG, "Is Logged Out: " + isLoggedOut);
            Log.d(TAG, "Rescuer service was running: " + serviceWasRunning);
            Log.d(TAG, "Barangay service was running: " + barangayServiceWasRunning);
            
            // Don't restart services if user has logged out
            if (isLoggedOut || !isLoggedIn || userId == null) {
                Log.d(TAG, "User is logged out or not logged in, not restarting services");
                return;
            }
            
            // Restart services for rescuers
            if ("rescuer".equals(userType)) {
                Log.d(TAG, "🚨 Restarting rescuer services after boot");
                
                // Restart RescuerForegroundService
                try {
                    Intent rescuerIntent = new Intent(context, RescuerForegroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(rescuerIntent);
                    } else {
                        context.startService(rescuerIntent);
                    }
                    Log.d(TAG, "✅ RescuerForegroundService restart requested");
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to restart RescuerForegroundService: " + e.getMessage());
                }
                
                // Restart EmergencySOSBackgroundService
                try {
                    Intent emergencyIntent = new Intent(context, EmergencySOSBackgroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(emergencyIntent);
                    } else {
                        context.startService(emergencyIntent);
                    }
                    Log.d(TAG, "✅ EmergencySOSBackgroundService restart requested");
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to restart EmergencySOSBackgroundService: " + e.getMessage());
                }
            }
            
            // Restart services for barangay officials
            if ("barangay".equals(userType)) {
                Log.d(TAG, "🏢 Restarting barangay services after boot");
                
                try {
                    Intent barangayIntent = new Intent(context, BarangayForegroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(barangayIntent);
                    } else {
                        context.startService(barangayIntent);
                    }
                    Log.d(TAG, "✅ BarangayForegroundService restart requested");
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to restart BarangayForegroundService: " + e.getMessage());
                }
            }
            
            // Restart services for seniors
            if ("seniors".equals(userType) || "senior".equals(userType)) {
                Log.d(TAG, "👴 Restarting senior services after boot");
                
                try {
                    Intent seniorIntent = new Intent(context, SeniorForegroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(seniorIntent);
                    } else {
                        context.startService(seniorIntent);
                    }
                    Log.d(TAG, "✅ SeniorForegroundService restart requested");
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to restart SeniorForegroundService: " + e.getMessage());
                }
            }
            
            // Start WorkManager for periodic notification checks
            try {
                NotificationWorkManager.startNotificationMonitoring(context);
                NotificationWorkManager.startEmergencyMonitoring(context);
                Log.d(TAG, "✅ WorkManager notification monitoring started");
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to start WorkManager: " + e.getMessage());
            }
            
            // Schedule periodic service restart alarm
            try {
                ServiceRestartAlarmReceiver.scheduleAlarm(context);
                Log.d(TAG, "✅ Service restart alarm scheduled");
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to schedule service restart alarm: " + e.getMessage());
            }
            
            Log.d(TAG, "✅ Boot receiver completed - all services restart requested");
        }
    }
}