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
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.TimeUnit;

public class NotificationWorker extends Worker {
    private static final String TAG = "NotificationWorker";
    private static final String CHANNEL_ID = "workmanager_notifications";
    
    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "NotificationWorker started");
        
        try {
            // Get user info
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("user_id", null);
            String userType = prefs.getString("user_type", null);
            
            if (userId == null || userType == null) {
                Log.d(TAG, "No user info available, skipping notification check");
                return Result.success();
            }
            
            // Check for notifications
            checkForNotifications(userId, userType);
            
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in NotificationWorker: " + e.getMessage());
            return Result.retry();
        }
    }
    
    private void checkForNotifications(String userId, String userType) {
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
        
        // Create and show notification
        showNotification(notificationId, title, message, type);
        
        // Mark as read
        markNotificationAsRead(notificationId);
    }
    
    private void showNotification(String notificationId, String title, String message, String type) {
        Context context = getApplicationContext();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create notification channel
        createNotificationChannel(context, notificationManager);
        
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
            .build();
        
        notificationManager.notify(notificationId.hashCode(), notification);
        Log.d(TAG, "Notification displayed: " + title);
    }
    
    private void markNotificationAsRead(String notificationId) {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
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
    
    private void createNotificationChannel(Context context, NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "WorkManager Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Background notifications via WorkManager");
            notificationManager.createNotificationChannel(channel);
        }
    }
}
