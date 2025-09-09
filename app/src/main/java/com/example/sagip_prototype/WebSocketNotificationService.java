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
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebSocketNotificationService extends Service {
    private static final String TAG = "WebSocketNotificationService";
    private static final String CHANNEL_ID = "websocket_notifications";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SERVICE_ID = 1002;
    
    private ScheduledExecutorService executor;
    private boolean isMonitoring = false;
    private String currentUserId;
    private String currentUserType;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketNotificationService created");
        
        // Create notification channel
        createNotificationChannel();
        
        // Get user info
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currentUserId = prefs.getString("user_id", null);
        currentUserType = prefs.getString("user_type", null);
        
        executor = Executors.newSingleThreadScheduledExecutor();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WebSocketNotificationService started");
        
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("start_monitoring".equals(action)) {
                startMonitoring();
            } else if ("stop_monitoring".equals(action)) {
                stopMonitoring();
            }
        }
        
        return START_STICKY;
    }
    
    private void startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring");
            return;
        }
        
        if (currentUserId == null) {
            Log.d(TAG, "No user ID available, trying to get from SharedPreferences");
            SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            currentUserId = prefs.getString("user_id", null);
            currentUserType = prefs.getString("user_type", null);
            
            if (currentUserId == null) {
                Log.d(TAG, "Still no user ID available, stopping service");
                stopSelf();
                return;
            }
        }
        
        Log.d(TAG, "Starting WebSocket monitoring for user: " + currentUserId);
        isMonitoring = true;
        
        // Start as foreground service
        startForeground(SERVICE_ID, createServiceNotification());
        
        // Start polling Firestore every 2 seconds for immediate notifications
        executor.scheduleAtFixedRate(this::checkForNotifications, 0, 2, TimeUnit.SECONDS);
    }
    
    private void stopMonitoring() {
        Log.d(TAG, "Stopping WebSocket monitoring");
        isMonitoring = false;
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        
        stopForeground(true);
        stopSelf();
    }
    
    private void checkForNotifications() {
        if (!isMonitoring || currentUserId == null) return;
        
        Log.d(TAG, "Checking for notifications via Firestore polling");
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collectionPath = "Sagip/users/" + currentUserType + "/" + currentUserId + "/notifications";
        
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
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
        intent.putExtra("notification_type", type);
        intent.putExtra("notification_id", notificationId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            notificationId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
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
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collectionPath = "Sagip/users/" + currentUserType + "/" + currentUserId + "/notifications";
        
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
    
    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAGIP Notifications")
            .setContentText("Monitoring for new notifications...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "WebSocket Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Real-time notifications via WebSocket");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WebSocketNotificationService destroyed");
        stopMonitoring();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
