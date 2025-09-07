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

import java.util.concurrent.TimeUnit;

public class HospitalStatusNotificationService extends Service {
    
    private static final String TAG = "HospitalStatusService";
    private static final String CHANNEL_ID = "hospital_status_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_NOTIFICATION_SCHEDULED = "notificationScheduled";
    
    // Status update requirement (10 minutes)
    private static final long STATUS_UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10);
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private AlarmManager alarmManager;
    private SharedPreferences sharedPreferences;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("schedule_notification".equals(action)) {
                scheduleStatusUpdateNotification();
            } else if ("cancel_notification".equals(action)) {
                cancelStatusUpdateNotification();
            } else if ("check_status".equals(action)) {
                checkAndNotifyIfNeeded();
            }
        }
        
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
                "Hospital Status Updates",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for hospital status update reminders");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void scheduleStatusUpdateNotification() {
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (userId == null || !"hospital".equals(userType)) {
            Log.d(TAG, "Not a hospital user or no userId, skipping notification scheduling");
            return;
        }
        
        // Check current status and schedule next notification
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        com.google.firebase.Timestamp lastUpdated = documentSnapshot.getTimestamp("lastUpdated");
                        if (lastUpdated != null) {
                            long lastUpdateTime = lastUpdated.toDate().getTime();
                            long currentTime = System.currentTimeMillis();
                            long timeSinceLastUpdate = currentTime - lastUpdateTime;
                            
                            if (timeSinceLastUpdate < STATUS_UPDATE_INTERVAL_MS) {
                                // Schedule notification for when update is due
                                long timeUntilUpdate = STATUS_UPDATE_INTERVAL_MS - timeSinceLastUpdate;
                                scheduleNotification(timeUntilUpdate);
                                Log.d(TAG, "Scheduled notification in " + (timeUntilUpdate / (1000 * 60)) + " minutes");
                            } else {
                                // Update is overdue, show notification immediately
                                showStatusUpdateNotification();
                                Log.d(TAG, "Status update is overdue, showing notification immediately");
                            }
                        } else {
                            // No lastUpdated timestamp, show notification immediately
                            showStatusUpdateNotification();
                            Log.d(TAG, "No lastUpdated timestamp, showing notification immediately");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check hospital status", e);
                });
    }
    
    private void scheduleNotification(long delayMs) {
        Intent notificationIntent = new Intent(this, HospitalStatusNotificationService.class);
        notificationIntent.putExtra("action", "check_status");
        
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 
            NOTIFICATION_ID, 
            notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        long triggerTime = System.currentTimeMillis() + delayMs;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            );
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            );
        }
        
        // Mark notification as scheduled
        sharedPreferences.edit()
                .putBoolean(KEY_NOTIFICATION_SCHEDULED, true)
                .apply();
        
        Log.d(TAG, "Notification scheduled for " + new java.util.Date(triggerTime));
    }
    
    private void checkAndNotifyIfNeeded() {
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (userId == null || !"hospital".equals(userType)) {
            Log.d(TAG, "Not a hospital user or no userId, skipping notification check");
            return;
        }
        
        // Check if user is still logged in
        if (mAuth.getCurrentUser() == null) {
            Log.d(TAG, "User logged out, canceling notifications");
            cancelStatusUpdateNotification();
            return;
        }
        
        // Check current status
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        com.google.firebase.Timestamp lastUpdated = documentSnapshot.getTimestamp("lastUpdated");
                        if (lastUpdated != null) {
                            long lastUpdateTime = lastUpdated.toDate().getTime();
                            long currentTime = System.currentTimeMillis();
                            long timeSinceLastUpdate = currentTime - lastUpdateTime;
                            
                            if (timeSinceLastUpdate >= STATUS_UPDATE_INTERVAL_MS) {
                                // Time to update, show notification
                                showStatusUpdateNotification();
                                // Schedule next notification
                                scheduleNotification(STATUS_UPDATE_INTERVAL_MS);
                            } else {
                                // Not time yet, reschedule
                                long timeUntilUpdate = STATUS_UPDATE_INTERVAL_MS - timeSinceLastUpdate;
                                scheduleNotification(timeUntilUpdate);
                            }
                        } else {
                            // No timestamp, show notification
                            showStatusUpdateNotification();
                            scheduleNotification(STATUS_UPDATE_INTERVAL_MS);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check hospital status", e);
                });
    }
    
    private void showStatusUpdateNotification() {
        Intent intent = new Intent(this, Hospital_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Hospital Status Update Required")
                .setContentText("Your hospital status needs to be updated. Please update your current bed and doctor availability.")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
        
        Log.d(TAG, "Status update notification shown");
    }
    
    private void cancelStatusUpdateNotification() {
        Intent notificationIntent = new Intent(this, HospitalStatusNotificationService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        alarmManager.cancel(pendingIntent);
        
        // Cancel any existing notifications
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
        
        // Mark notification as not scheduled
        sharedPreferences.edit()
                .putBoolean(KEY_NOTIFICATION_SCHEDULED, false)
                .apply();
        
        Log.d(TAG, "Status update notifications canceled");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }
}
