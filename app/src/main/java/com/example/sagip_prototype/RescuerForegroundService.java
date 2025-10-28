package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Dedicated foreground service for rescuers to ensure they receive notifications
 * even when the app is completely closed
 */
public class RescuerForegroundService extends Service {
    
    private static final String TAG = "RescuerForegroundService";
    private static final String CHANNEL_ID = "rescuer_foreground_service";
    private static final String CHANNEL_NAME = "Rescuer Background Service";
    private static final String CHANNEL_DESCRIPTION = "Ensures rescuers receive notifications when app is closed";
    private static final int FOREGROUND_NOTIFICATION_ID = 5001;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚨 RescuerForegroundService created");
        
        createNotificationChannel();
        
        // CRITICAL: Start foreground IMMEDIATELY in onCreate() to prevent crash
        try {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
            Log.d(TAG, "✅ RescuerForegroundService started in foreground mode");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start foreground service: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚨 RescuerForegroundService started");
        
        // Check if user has logged out - if so, don't restart
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (isLoggedOut) {
            Log.w(TAG, "⚠️ User has logged out, stopping RescuerForegroundService");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Check if user is a rescuer
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        String userType = sharedPreferences.getString("userType", null);
        
        if (!"rescuer".equals(userType)) {
            Log.d(TAG, "User is not a rescuer, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Foreground notification already started in onCreate()
        // Just update it if needed
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update foreground notification: " + e.getMessage(), e);
        }
        
        // Start rescuer notification monitoring
        RescuerNotificationManager.startMonitoring(this);
        
        // Mark service as running in SharedPreferences
        sharedPreferences.edit().putBoolean("rescuerServiceRunning", true).apply();
        
        Log.d(TAG, "✅ Rescuer foreground service running - notifications will work when app is closed");
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "🛑 RescuerForegroundService destroyed");
        
        // Stop notification monitoring
        RescuerNotificationManager.stopMonitoring();
        
        // Clear service running flag
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("rescuerServiceRunning", false).apply();
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Creates the foreground notification to keep the service running
     */
    private Notification createForegroundNotification() {
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚨 SAGIPP Rescuer Service")
                .setContentText("Monitoring for emergency notifications...")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }
    
    /**
     * Creates notification channel for the foreground service
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            
            Log.d(TAG, "✅ Rescuer foreground service notification channel created");
        }
    }
}
