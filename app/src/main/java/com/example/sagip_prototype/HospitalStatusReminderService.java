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
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service to remind hospital users to update their status when the timer expires
 * Works even when the app is closed
 */
public class HospitalStatusReminderService extends Service {
    private static final String TAG = "HospitalStatusReminderService";
    private static final String CHANNEL_ID = "hospital_status_reminder";
    private static final String COUNTDOWN_CHANNEL_ID = "hospital_countdown_channel";
    private static final int NOTIFICATION_ID = 3001;
    private static final int SERVICE_ID = 3002;
    private static final int COUNTDOWN_NOTIFICATION_ID = 3003;
    
    private ScheduledExecutorService executor;
    private boolean isMonitoring = false;
    private String currentUserId;
    private String hospitalName;
    private long lastStatusUpdateTime = 0;
    private static final long STATUS_UPDATE_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
    private boolean countdownNotificationShown = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "HospitalStatusReminderService created");
        
        // Create notification channels
        createNotificationChannel();
        createCountdownNotificationChannel();
        
        // Get user info
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currentUserId = prefs.getString("user_id", null);
        
        executor = Executors.newSingleThreadScheduledExecutor();
        
        // CRITICAL: Start foreground IMMEDIATELY in onCreate() to prevent crash
        try {
            startForeground(SERVICE_ID, createServiceNotification());
            Log.d(TAG, "✅ HospitalStatusReminderService started in foreground mode");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start foreground service: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "HospitalStatusReminderService started");
        
        // Foreground notification already started in onCreate()
        // Just update it if needed
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(SERVICE_ID, createServiceNotification());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update foreground notification: " + e.getMessage(), e);
        }
        
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("start_monitoring".equals(action)) {
                startMonitoring();
            } else if ("stop_monitoring".equals(action)) {
                stopMonitoring();
            } else if ("update_status_time".equals(action)) {
                // Update the last status update time
                lastStatusUpdateTime = intent.getLongExtra("last_update_time", System.currentTimeMillis());
                Log.d(TAG, "Updated last status update time: " + lastStatusUpdateTime);
                // Immediately update countdown notification with new time
                long timeRemaining = STATUS_UPDATE_INTERVAL_MS;
                updateCountdownNotification(timeRemaining);
            } else if ("cancel_countdown".equals(action)) {
                // Cancel countdown notification (e.g., when logging out)
                cancelCountdownNotification();
            }
        }
        
        return START_STICKY;
    }
    
    private void startMonitoring() {
        if (isMonitoring || currentUserId == null) {
            Log.d(TAG, "Already monitoring or no user ID");
            return;
        }
        
        // Check if current user is a hospital - if not, stop the service
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userType = prefs.getString("user_type", null);
        
        if (userType == null || !userType.equals("hospital")) {
            Log.w(TAG, "⚠️ User is not a hospital (userType: " + userType + "), stopping HospitalStatusReminderService");
            stopSelf();
            return;
        }
        
        Log.d(TAG, "Starting hospital status reminder monitoring for user: " + currentUserId);
        isMonitoring = true;
        
        // Foreground service already started in onCreate()
        // Just update the notification to show monitoring status
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(SERVICE_ID, createServiceNotification());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update foreground notification: " + e.getMessage(), e);
        }
        
        // Get hospital name and last update time
        getHospitalInfo();
        
        // Start checking every 30 seconds for more accurate countdown
        executor.scheduleAtFixedRate(this::checkStatusUpdateTime, 0, 30, TimeUnit.SECONDS);
    }
    
    private void stopMonitoring() {
        Log.d(TAG, "Stopping hospital status reminder monitoring");
        isMonitoring = false;
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        
        // Cancel countdown notification when stopping
        cancelCountdownNotification();
        
        stopForeground(true);
        stopSelf();
    }
    
    private void getHospitalInfo() {
        if (currentUserId == null) return;
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Sagip")
            .document("users")
            .collection("hospital")
            .document(currentUserId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    hospitalName = documentSnapshot.getString("hospitalName");
                    com.google.firebase.Timestamp lastUpdated = documentSnapshot.getTimestamp("lastUpdated");
                    if (lastUpdated != null) {
                        lastStatusUpdateTime = lastUpdated.toDate().getTime();
                        Log.d(TAG, "Hospital info loaded - Name: " + hospitalName + ", Last update: " + lastStatusUpdateTime);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting hospital info: " + e.getMessage());
            });
    }
    
    private void checkStatusUpdateTime() {
        if (!isMonitoring || currentUserId == null) return;
        
        // Always fetch the latest update time from Firestore to stay in sync
        // This is critical when app is closed - we need accurate data
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Sagip")
            .document("users")
            .collection("hospital")
            .document(currentUserId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    com.google.firebase.Timestamp lastUpdated = documentSnapshot.getTimestamp("lastUpdated");
                    if (lastUpdated != null) {
                        lastStatusUpdateTime = lastUpdated.toDate().getTime();
                        hospitalName = documentSnapshot.getString("hospitalName");
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLastUpdate = currentTime - lastStatusUpdateTime;
                    long timeRemaining = STATUS_UPDATE_INTERVAL_MS - timeSinceLastUpdate;
                    
                    Log.d(TAG, "Checking status update time - Time since last update: " + (timeSinceLastUpdate / 60000) + " minutes, Time remaining: " + (timeRemaining / 60000) + " minutes");
                    
                    // Check if it's time to update status (10 minutes)
                    if (timeSinceLastUpdate >= STATUS_UPDATE_INTERVAL_MS) {
                        Log.d(TAG, "Time to update status! Sending reminder notification");
                        sendStatusUpdateReminder();
                        // Cancel countdown notification when expired
                        cancelCountdownNotification();
                    } else {
                        // Show/update countdown notification while timer is running
                        updateCountdownNotification(timeRemaining);
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking status update time: " + e.getMessage());
            });
    }
    
    private void sendStatusUpdateReminder() {
        Log.d(TAG, "Sending status update reminder notification");
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        Intent intent = new Intent(this, Hospital_Dashboard.class);
        intent.putExtra("show_status_update_dialog", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            NOTIFICATION_ID, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String hospitalDisplayName = hospitalName != null ? hospitalName : "Hospital";
        
        // Get alarm sound for the notification
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_status_update_required))
            .setContentText(String.format(getString(R.string.notification_status_update_text), hospitalDisplayName))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(alarmSound)
            .setVibrate(new long[]{0, 500, 200, 500})
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(false)
            .build();
        
        notificationManager.notify(NOTIFICATION_ID, notification);
        Log.d(TAG, "Status update reminder notification sent");
    }
    
    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_hospital_monitoring))
            .setContentText(getString(R.string.notification_monitoring_reminders))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Hospital Status Reminders",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders to update hospital status");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            
            // Set alarm sound for the notification channel
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();
            channel.setSound(alarmSound, audioAttributes);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void createCountdownNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                COUNTDOWN_CHANNEL_ID,
                "Status Update Countdown",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows time remaining until next status update is required");
            channel.enableVibration(false);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Update the countdown notification showing time remaining
     */
    private void updateCountdownNotification(long timeRemainingMs) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Calculate minutes and seconds
        long totalSeconds = timeRemainingMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        String hospitalDisplayName = hospitalName != null ? hospitalName : "Hospital";
        String timeText = String.format("%d:%02d", minutes, seconds);
        
        // Intent to open Hospital Dashboard
        Intent intent = new Intent(this, Hospital_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            COUNTDOWN_NOTIFICATION_ID, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Build the countdown notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, COUNTDOWN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏱️ Status Update Countdown")
            .setContentText("Time remaining: " + timeText + " - Tap to update now")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(hospitalDisplayName + "\nNext status update required in: " + timeText + "\nTap to update your hospital status now."))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        // Change color based on urgency
        if (minutes <= 2) {
            // Urgent - red/orange tone
            builder.setColor(0xFFFF5722);
            builder.setContentTitle("⚠️ Status Update Soon!");
        } else if (minutes <= 5) {
            // Warning - orange tone
            builder.setColor(0xFFFF9800);
        } else {
            // Normal - blue tone
            builder.setColor(0xFF2196F3);
        }
        
        notificationManager.notify(COUNTDOWN_NOTIFICATION_ID, builder.build());
        countdownNotificationShown = true;
        
        Log.d(TAG, "📱 Countdown notification updated: " + timeText + " remaining");
    }
    
    /**
     * Cancel the countdown notification
     */
    private void cancelCountdownNotification() {
        if (countdownNotificationShown) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID);
            countdownNotificationShown = false;
            Log.d(TAG, "📱 Countdown notification cancelled");
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "HospitalStatusReminderService destroyed");
        stopMonitoring();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
