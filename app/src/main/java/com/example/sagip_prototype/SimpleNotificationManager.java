package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple notification manager for testing without Firestore dependency
 */
public class SimpleNotificationManager {
    private static final String TAG = "SimpleNotificationManager";
    private static final String CHANNEL_ID = "simple_notifications";
    private static final int NOTIFICATION_ID = 2001;
    
    private static SimpleNotificationManager instance;
    private ScheduledExecutorService executor;
    private boolean isRunning = false;
    private Context context;
    private int notificationCount = 0;
    
    private SimpleNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }
    
    public static synchronized SimpleNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new SimpleNotificationManager(context);
        }
        return instance;
    }
    
    public void startTestNotifications() {
        if (isRunning) {
            Log.d(TAG, "Test notifications already running");
            return;
        }
        
        Log.d(TAG, "Starting test notifications");
        isRunning = true;
        
        executor = Executors.newSingleThreadScheduledExecutor();
        
        // Send a test notification every 30 seconds
        executor.scheduleAtFixedRate(this::sendTestNotification, 10, 30, TimeUnit.SECONDS);
    }
    
    public void stopTestNotifications() {
        Log.d(TAG, "Stopping test notifications");
        isRunning = false;
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    private void sendTestNotification() {
        if (!isRunning) return;
        
        notificationCount++;
        String title = "Test Notification #" + notificationCount;
        String message = "This is a test notification without FCM - " + System.currentTimeMillis();
        
        Log.d(TAG, "Sending test notification: " + title);
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        Intent intent = new Intent(context, Rescuer_Dashboard.class);
        intent.putExtra("notification_type", "test");
        intent.putExtra("notification_id", "test_" + notificationCount);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 
            notificationCount, 
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
        
        notificationManager.notify(notificationCount, notification);
        Log.d(TAG, "Test notification displayed: " + title);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Simple Test Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Simple test notifications without FCM");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    public void sendImmediateTestNotification() {
        Log.d(TAG, "Sending immediate test notification");
        sendTestNotification();
    }
}
