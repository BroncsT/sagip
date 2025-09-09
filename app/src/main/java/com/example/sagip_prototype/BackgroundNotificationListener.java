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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.Map;

public class BackgroundNotificationListener extends Service {
    
    private static final String TAG = "BackgroundNotificationListener";
    private static final String CHANNEL_ID = "background_notifications";
    private static final int NOTIFICATION_ID = 4001;
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    
    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;
    private SharedPreferences sharedPreferences;
    private String currentUserId;
    private String currentUserType;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BackgroundNotificationListener created");
        
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "BackgroundNotificationListener started");
        
        // Get current user info
        currentUserId = sharedPreferences.getString(KEY_USER_ID, null);
        currentUserType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (currentUserId != null && currentUserType != null && "rescuer".equals(currentUserType)) {
            Log.d(TAG, "Starting notification listener for rescuer: " + currentUserId);
            
            // Start as foreground service
            startForegroundService();
            
            // Start listening for notifications
            startNotificationListener();
        } else {
            Log.d(TAG, "Not a rescuer user or no user info, stopping service");
            stopSelf();
        }
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "BackgroundNotificationListener destroyed");
        
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }
    
    private void startForegroundService() {
        // Create a persistent notification to keep the service running
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SAGIP Background Service")
                .setContentText("Listening for emergency notifications...")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
        
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "Started as foreground service");
    }
    
    private void startNotificationListener() {
        Log.d(TAG, "Starting Firestore notification listener for user: " + currentUserId);
        
        // Listen to the user's notifications collection
        Query query = db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(currentUserId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1);
        
        notificationListener = query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error listening to notifications: " + error.getMessage());
                return;
            }
            
            if (snapshot != null) {
                for (DocumentChange dc : snapshot.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        Log.d(TAG, "New notification detected");
                        handleNewNotification(dc.getDocument().getData());
                    }
                }
            }
        });
        
        Log.d(TAG, "Notification listener started successfully");
    }
    
    private void handleNewNotification(Map<String, Object> notificationData) {
        Log.d(TAG, "Handling new notification: " + notificationData);
        
        String type = (String) notificationData.get("type");
        if ("hospital_status_update".equals(type)) {
            String hospitalName = (String) notificationData.get("hospitalName");
            String hospitalStatus = (String) notificationData.get("hospitalStatus");
            Object availableBedsObj = notificationData.get("availableBeds");
            Object availableDoctorsObj = notificationData.get("availableDoctors");
            
            if (hospitalName != null && hospitalStatus != null && 
                availableBedsObj != null && availableDoctorsObj != null) {
                
                int availableBeds = ((Number) availableBedsObj).intValue();
                int availableDoctors = ((Number) availableDoctorsObj).intValue();
                
                showHospitalUpdateNotification(hospitalName, hospitalStatus, availableBeds, availableDoctors);
            }
        }
    }
    
    private void showHospitalUpdateNotification(String hospitalName, String hospitalStatus, 
                                             int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "Showing hospital update notification: " + hospitalName);
        
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
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
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Background Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Background notifications for rescuers when app is closed");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
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
}
