package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Alternative notification manager that doesn't rely on FCM
 * Uses Firestore polling + local notifications
 */
public class AlternativeNotificationManager {
    private static final String TAG = "AlternativeNotificationManager";
    private static final String CHANNEL_ID = "alternative_notifications";
    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_LAST_CHECK = "last_notification_check";
    
    private static AlternativeNotificationManager instance;
    private ScheduledExecutorService executor;
    private boolean isMonitoring = false;
    private Context context;
    
    private AlternativeNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }
    
    public static synchronized AlternativeNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new AlternativeNotificationManager(context);
        }
        return instance;
    }
    
    public void startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring notifications");
            return;
        }
        
        Log.d(TAG, "Starting alternative notification monitoring");
        isMonitoring = true;
        
        executor = Executors.newSingleThreadScheduledExecutor();
        
        // Check every 3 seconds for immediate notifications
        executor.scheduleAtFixedRate(this::checkForNotifications, 0, 3, TimeUnit.SECONDS);
    }
    
    public void stopMonitoring() {
        Log.d(TAG, "Stopping alternative notification monitoring");
        isMonitoring = false;
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    private void checkForNotifications() {
        if (!isMonitoring) return;
        
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("user_id", null);
        String userType = prefs.getString("user_type", null);
        
        if (userId == null || userType == null) {
            Log.d(TAG, "No user info available for notification check, stopping monitoring");
            stopMonitoring();
            return;
        }
        
        Log.d(TAG, "Checking for notifications for user: " + userId);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collectionPath = "Sagip/users/" + userType + "/" + userId + "/notifications";
        
        db.collection(collectionPath)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!querySnapshot.isEmpty()) {
                    Log.d(TAG, "Found " + querySnapshot.size() + " unread notifications");
                    
                    for (var doc : querySnapshot.getDocuments()) {
                        handleNotification(doc.getId(), doc.getData());
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking notifications: " + e.getMessage());
            });
    }
    
    private void handleNotification(String notificationId, java.util.Map<String, Object> data) {
        String type = (String) data.get("type");
        String title = (String) data.get("title");
        String message = (String) data.get("message");
        
        Log.d(TAG, "Handling notification: " + type + " - " + title);
        
        // Show local notification
        showLocalNotification(notificationId, title, message, type);
        
        // Mark as read
        markNotificationAsRead(notificationId);
    }
    
    private void showLocalNotification(String notificationId, String title, String message, String type) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        Intent intent = new Intent(context, Rescuer_Dashboard.class);
        intent.putExtra("notification_type", type);
        intent.putExtra("notification_id", notificationId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            notificationId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(new long[]{0, 500, 200, 500})
            .build();
        
        notificationManager.notify(notificationId.hashCode(), notification);
        Log.d(TAG, "Local notification displayed: " + title);
    }
    
    private void markNotificationAsRead(String notificationId) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("user_id", null);
        String userType = prefs.getString("user_type", null);
        
        if (userId == null || userType == null) return;
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collectionPath = "Sagip/users/" + userType + "/" + userId + "/notifications";
        
        db.collection(collectionPath)
            .document(notificationId)
            .update("read", true)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Notification marked as read: " + notificationId);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error marking notification as read: " + e.getMessage());
            });
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Alternative Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications without FCM");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    public void sendTestNotification() {
        Log.d(TAG, "Sending test notification");
        showLocalNotification(
            "test_" + System.currentTimeMillis(),
            "Test Notification",
            "This is a test notification without FCM",
            "test"
        );
    }
}
