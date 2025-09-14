package com.example.sagip_prototype;

import android.app.AlarmManager;
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

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class RescuerBackgroundNotificationService extends Service {
    
    private static final String TAG = "RescuerBackgroundService";
    private static final String CHANNEL_ID = "rescuer_background_notifications";
    private static final int NOTIFICATION_ID = 2001;
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_LAST_CHECK_TIME = "lastNotificationCheckTime";
    
    // Check for new notifications every 10 seconds
    private static final long CHECK_INTERVAL_MS = 10 * 1000; // 10 seconds
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private AlarmManager alarmManager;
    private SharedPreferences sharedPreferences;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "RescuerBackgroundNotificationService created");
        
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "RescuerBackgroundNotificationService started with flags: " + flags + ", startId: " + startId);
        
        if (intent != null) {
            String action = intent.getStringExtra("action");
            Log.d(TAG, "Service action: " + action);
            
            if ("start_monitoring".equals(action)) {
                startNotificationMonitoring();
            } else if ("stop_monitoring".equals(action)) {
                stopNotificationMonitoring();
            } else if ("check_notifications".equals(action)) {
                checkForNewNotifications();
            } else {
                // If no action specified, assume we should start monitoring
                Log.d(TAG, "No action specified, starting monitoring by default");
                startNotificationMonitoring();
            }
        } else {
            // If intent is null (service restarted by system), restart monitoring
            Log.d(TAG, "Intent is null, service likely restarted by system - restarting monitoring");
            startNotificationMonitoring();
        }
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rescuer Background Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Background notifications for rescuers when app is closed");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void startNotificationMonitoring() {
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (userId == null || !"rescuer".equals(userType)) {
            Log.d(TAG, "Not a rescuer user or no userId, stopping service");
            stopSelf();
            return;
        }
        
        // Check if user is still logged in
        if (mAuth.getCurrentUser() == null) {
            Log.d(TAG, "User logged out, stopping service");
            stopSelf();
            return;
        }
        
        Log.d(TAG, "Starting notification monitoring for rescuer: " + userId);
        
        // Mark service as running
        sharedPreferences.edit().putBoolean("rescuerServiceRunning", true).apply();
        
        // Start as foreground service
        startForegroundService();
        
        // Schedule periodic checks
        schedulePeriodicChecks();
        
        // Do initial check
        checkForNewNotifications();
    }
    
    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SAGIPP Rescuer")
                .setContentText("Monitoring for hospital status updates")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        Log.d(TAG, "Started as foreground service");
    }
    
    private void schedulePeriodicChecks() {
        Intent checkIntent = new Intent(this, RescuerBackgroundNotificationService.class);
        checkIntent.putExtra("action", "check_notifications");
        
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 
            NOTIFICATION_ID, 
            checkIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Use setRepeating for continuous monitoring
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + CHECK_INTERVAL_MS,
                CHECK_INTERVAL_MS,
                pendingIntent
            );
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + CHECK_INTERVAL_MS,
                CHECK_INTERVAL_MS,
                pendingIntent
            );
        }
        
        Log.d(TAG, "Scheduled repeating checks every " + (CHECK_INTERVAL_MS / 1000) + " seconds");
    }
    
    private void checkForNewNotifications() {
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (userId == null || !"rescuer".equals(userType)) {
            Log.d(TAG, "Not a rescuer user, stopping service");
            stopSelf();
            return;
        }
        
        // Check if user is still logged in
        if (mAuth.getCurrentUser() == null) {
            Log.d(TAG, "User logged out, stopping service");
            stopSelf();
            return;
        }
        
        Log.d(TAG, "Checking for new notifications for rescuer: " + userId);
        
        // Get last check time
        long lastCheckTime = sharedPreferences.getLong(KEY_LAST_CHECK_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        // Update last check time
        sharedPreferences.edit().putLong(KEY_LAST_CHECK_TIME, currentTime).apply();
        
        // Get unread notifications
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("read", false)
                .whereEqualTo("type", "hospital_status_update")
                .limit(5)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " unread notifications");
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String hospitalName = document.getString("hospitalName");
                        String hospitalStatus = document.getString("hospitalStatus");
                        Long availableBeds = document.getLong("availableBeds");
                        Long availableDoctors = document.getLong("availableDoctors");
                        String notificationId = document.getId();
                        
                        if (hospitalName != null && hospitalStatus != null && availableBeds != null && availableDoctors != null) {
                            // Show notification
                            showHospitalUpdateNotification(hospitalName, hospitalStatus, 
                                    availableBeds.intValue(), availableDoctors.intValue());
                            
                            // Mark as read
                            markNotificationAsRead(userId, notificationId);
                        }
                    }
                    
                    Log.d(TAG, "Notification check completed, next check will be automatic");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check notifications: " + e.getMessage());
                    Log.d(TAG, "Next check will be automatic via repeating alarm");
                });
    }
    
    private void showHospitalUpdateNotification(String hospitalName, String hospitalStatus, int availableBeds, int availableDoctors) {
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        // Add hospital data to intent for notification click handling
        intent.putExtra("notification_type", "hospital_status_update");
        intent.putExtra("hospital_name", hospitalName);
        intent.putExtra("hospital_status", hospitalStatus);
        intent.putExtra("available_beds", availableBeds);
        intent.putExtra("available_doctors", availableDoctors);
        intent.putExtra("highlight_hospital", hospitalName);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(), // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get status emoji
        String statusEmoji = getStatusEmoji(hospitalStatus);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🏥 Hospital Status Updated")
                .setContentText(hospitalName + " is now " + statusEmoji + " " + hospitalStatus.toUpperCase())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(hospitalName + " has updated their status to " + statusEmoji + " " + hospitalStatus.toUpperCase() + 
                                "\n\n📊 Available Beds: " + availableBeds + 
                                "\n👨‍⚕️ Available Doctors: " + availableDoctors +
                                "\n\nThis information will help with emergency response planning."))
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setLights(0xFF2196F3, 1000, 1000)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), notification);
        
        Log.d(TAG, "Hospital update notification shown: " + hospitalName);
    }
    
    private void markNotificationAsRead(String rescuerId, String notificationId) {
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("notifications")
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Notification marked as read: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark notification as read: " + notificationId, e);
                });
    }
    
    private void stopNotificationMonitoring() {
        // Mark service as not running
        sharedPreferences.edit().putBoolean("rescuerServiceRunning", false).apply();
        
        // Cancel scheduled checks
        Intent checkIntent = new Intent(this, RescuerBackgroundNotificationService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            checkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        alarmManager.cancel(pendingIntent);
        
        // Stop foreground service
        stopForeground(true);
        stopSelf();
        
        Log.d(TAG, "Notification monitoring stopped");
    }
    
    private String getStatusEmoji(String status) {
        switch (status.toLowerCase()) {
            case "available":
                return "🟢";
            case "busy":
                return "🟡";
            case "full":
                return "🔴";
            default:
                return "⚪";
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "RescuerBackgroundNotificationService destroyed");
    }
}
