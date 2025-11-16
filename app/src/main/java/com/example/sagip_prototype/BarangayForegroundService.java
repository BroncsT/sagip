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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Dedicated foreground service for barangay officials to ensure they receive emergency notifications
 * even when the app is completely closed or the device is in deep sleep
 */
public class BarangayForegroundService extends Service {
    
    private static final String TAG = "BarangayForegroundService";
    private static final String CHANNEL_ID = "barangay_foreground_service";
    private static final String CHANNEL_NAME = "Barangay Emergency Service";
    private static final String CHANNEL_DESCRIPTION = "Ensures barangay officials receive emergency alerts when app is closed";
    private static final int FOREGROUND_NOTIFICATION_ID = 6001;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;
    private ListenerRegistration emergencyListener;
    private long listenerStartTime = 0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚨 BarangayForegroundService created");
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        createNotificationChannel();
        
        // CRITICAL: Start foreground IMMEDIATELY to prevent crash
        try {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
            Log.d(TAG, "✅ BarangayForegroundService started in foreground mode");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start foreground service: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚨 BarangayForegroundService started");
        
        // Check if user has logged out - if so, don't restart
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (isLoggedOut) {
            Log.w(TAG, "⚠️ User has logged out, stopping BarangayForegroundService");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Get current user data
        String userType = prefs.getString("user_type", null);
        if (userType == null) {
            // Try SagipAppPrefs as alternative
            SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
            userType = sagipPrefs.getString("userType", null);
        }
        
        String userIdFromPrefs = prefs.getString("user_id", null);
        if (userIdFromPrefs == null) {
            // Try SagipAppPrefs as alternative
            SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
            userIdFromPrefs = sagipPrefs.getString("userId", null);
        }
        
        // Get current user from Firebase Auth
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        } else {
            userId = userIdFromPrefs;
        }
        
        Log.d(TAG, "🔍 [BARANGAY_SERVICE] userType: " + userType + ", userId: " + userId);
        
        // Check if user is still logged in and is a barangay official
        if (userType == null || !userType.equals("barangay") || userId == null) {
            Log.w(TAG, "⚠️ Invalid user session (userType: " + userType + ", userId: " + userId + "), stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Start emergency notification listener
        startEmergencyNotificationListener();
        
        // Update foreground notification
        updateForegroundNotification();
        
        // Mark service as running
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("barangayServiceRunning", true).apply();
        
        Log.d(TAG, "✅ Barangay foreground service running - emergency notifications will work when app is closed");
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "🛑 BarangayForegroundService destroyed");
        
        // Stop emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Clear service running flag
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("barangayServiceRunning", false).apply();
        
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
        Intent notificationIntent = new Intent(this, Barangay_Dashboard.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚨 SAGIPP Barangay Service")
                .setContentText("Monitoring for emergency notifications...")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .build();
    }
    
    /**
     * Updates the foreground notification
     */
    private void updateForegroundNotification() {
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                Notification updatedNotification = createForegroundNotification();
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, updatedNotification);
                Log.d(TAG, "✅ Foreground notification updated");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update foreground notification: " + e.getMessage(), e);
        }
    }
    
    /**
     * Starts listening for emergency notifications
     */
    private void startEmergencyNotificationListener() {
        if (userId == null) {
            Log.w(TAG, "Cannot start emergency listener - userId is null");
            return;
        }
        
        // Remove any existing listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Set listener start time to filter out old notifications
        listenerStartTime = System.currentTimeMillis();
        
        Log.d(TAG, "🚨 Starting barangay emergency notification listener");
        Log.d(TAG, "🚨 Listener path: Sagip/users/barangay/" + userId + "/emergencyNotifications");
        Log.d(TAG, "⏰ Listener start time: " + listenerStartTime);
        
        // Listen for emergency notifications in real-time
        // Only get notifications created AFTER service start to avoid duplicates
        emergencyListener = db.collection("Sagip")
                .document("users")
                .collection("barangay")
                .document(userId)
                .collection("emergencyNotifications")
                .whereGreaterThan("timestamp", listenerStartTime)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to emergency notifications: " + error.getMessage(), error);
                        return;
                    }
                    
                    // Check if user is still a barangay official
                    SharedPreferences currentPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                    String currentUserType = currentPrefs.getString("user_type", null);
                    if (currentUserType == null) {
                        SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
                        currentUserType = sagipPrefs.getString("userType", null);
                    }
                    boolean isLoggedOut = currentPrefs.getBoolean("user_logged_out", false);
                    
                    if (isLoggedOut || currentUserType == null || !currentUserType.equals("barangay")) {
                        Log.w(TAG, "⚠️ User is no longer a barangay official or has logged out, stopping service");
                        stopSelf();
                        return;
                    }
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        Log.d(TAG, "🔔 Received " + querySnapshot.size() + " new emergency notifications");
                        
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            handleEmergencyNotification(document);
                        }
                    }
                });
    }
    
    /**
     * Handles an emergency notification
     */
    private void handleEmergencyNotification(QueryDocumentSnapshot document) {
        try {
            String type = document.getString("type");
            String seniorName = document.getString("seniorName");
            String seniorPhone = document.getString("seniorPhone");
            String locationAddress = document.getString("locationAddress");
            String requestId = document.getString("requestId");
            String emergencyType = document.getString("emergencyType");
            
            Log.d(TAG, "🚨 Processing emergency notification: " + seniorName + " (Request ID: " + requestId + ")");
            
            if ("EMERGENCY_ALERT".equals(type) && seniorName != null) {
                // Show high-priority emergency notification
                showEmergencyNotification(seniorName, seniorPhone, locationAddress, requestId, emergencyType);
                
                // Mark notification as processed
                document.getReference().update("processedByForegroundService", true)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Emergency notification marked as processed");
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "⚠️ Failed to mark notification as processed: " + e.getMessage());
                        });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling emergency notification: " + e.getMessage(), e);
        }
    }
    
    /**
     * Shows emergency notification to barangay official
     */
    private void showEmergencyNotification(String seniorName, String seniorPhone, String locationAddress, String requestId, String emergencyType) {
        Log.d(TAG, "🔔 Showing emergency notification for barangay official: " + seniorName);
        
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Notification permission denied - cannot show notification");
                return;
            }
        }
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent for notification click
        Intent notificationIntent = new Intent(this, Barangay_Dashboard.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra("emergency_notification", true);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("senior_phone", seniorPhone);
        notificationIntent.putExtra("location_address", locationAddress);
        notificationIntent.putExtra("request_id", requestId);
        notificationIntent.putExtra("emergency_type", emergencyType);
        notificationIntent.putExtra("from_foreground_service", true);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create call intent
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(android.net.Uri.parse("tel:" + seniorPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis() + 1,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String bigText = "🚨 URGENT: Senior needs immediate help!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + seniorPhone + "\n" +
                        "📍 Location: " + locationAddress + "\n" +
                        "🆘 Emergency: " + emergencyType + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        Notification notification = new NotificationCompat.Builder(this, "barangay_emergency_channel")
                .setSmallIcon(R.drawable.ic_emergency)
                .setContentTitle("🚨 EMERGENCY ALERT 🚨")
                .setContentText(seniorName + " needs immediate help!")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000)
                .setFullScreenIntent(pendingIntent, true)
                .addAction(android.R.drawable.ic_menu_call, "📞 CALL", callPendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build();
        
        notificationManager.notify(8001, notification);
        
        Log.d(TAG, "🔔 Emergency notification sent to barangay official for: " + seniorName);
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
            
            Log.d(TAG, "✅ Barangay foreground service notification channel created");
        }
    }
}
