package com.example.sagip_prototype;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_SERVICE_RUNNING = "rescuerServiceRunning";
    private static final String KEY_BARANGAY_SERVICE_RUNNING = "barangayServiceRunning";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "BootReceiver received action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            
            Log.d(TAG, "Device boot completed or app updated, checking if service should restart");
            
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String userId = sharedPreferences.getString(KEY_USER_ID, null);
            String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
            boolean serviceWasRunning = sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false);
            boolean barangayServiceWasRunning = sharedPreferences.getBoolean(KEY_BARANGAY_SERVICE_RUNNING, false);
            
            Log.d(TAG, "User ID: " + userId);
            Log.d(TAG, "User Type: " + userType);
            Log.d(TAG, "Rescuer service was running: " + serviceWasRunning);
            Log.d(TAG, "Barangay service was running: " + barangayServiceWasRunning);
            
            // Restart the rescuer foreground service if it was running before reboot
            if (serviceWasRunning && userId != null && "rescuer".equals(userType)) {
                Log.d(TAG, "Restarting RescuerForegroundService after boot");
                
                Intent serviceIntent = new Intent(context, RescuerForegroundService.class);
                context.startForegroundService(serviceIntent);
            }
            
            // Restart the barangay foreground service if it was running before reboot
            if (barangayServiceWasRunning && userId != null && "barangay".equals(userType)) {
                Log.d(TAG, " Restarting BarangayForegroundService after boot");
                
                Intent serviceIntent = new Intent(context, BarangayForegroundService.class);
                context.startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, "Not restarting service - conditions not met");
            }
        }
    }
}