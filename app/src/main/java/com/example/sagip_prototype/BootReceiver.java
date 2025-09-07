package com.example.sagip_prototype;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_NOTIFICATION_SCHEDULED = "notificationScheduled";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device boot completed, checking if notifications need to be rescheduled");
            
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
            boolean notificationScheduled = sharedPreferences.getBoolean(KEY_NOTIFICATION_SCHEDULED, false);
            
            // Only reschedule if user is a hospital and notifications were previously scheduled
            if ("hospital".equals(userType) && notificationScheduled) {
                Log.d(TAG, "Rescheduling hospital status notifications after boot");
                
                Intent serviceIntent = new Intent(context, HospitalStatusNotificationService.class);
                serviceIntent.putExtra("action", "schedule_notification");
                context.startService(serviceIntent);
            } else {
                Log.d(TAG, "No need to reschedule notifications - userType: " + userType + ", scheduled: " + notificationScheduled);
            }
        }
    }
}
