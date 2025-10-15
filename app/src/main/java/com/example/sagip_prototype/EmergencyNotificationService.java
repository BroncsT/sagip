package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Background service that listens for emergency notifications from seniors
 * This service runs continuously to ensure rescuers receive emergency alerts immediately
 */
public class EmergencyNotificationService extends Service {
    
    private static final String TAG = "EmergencyNotificationService";
    private static final String CHANNEL_ID = "emergency_notification_channel";
    private static final String CHANNEL_NAME = "Emergency Notifications";
    private static final String CHANNEL_DESCRIPTION = "Real-time emergency alerts from seniors";
    
    private FirebaseFirestore db;
    private ListenerRegistration emergencyListener;
    private String userId;
    private String userType;
    private double currentLat = 0.0;
    private double currentLong = 0.0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚨 EmergencyNotificationService created");
        
        // Initialize components
        db = FirebaseFirestore.getInstance();
        
        // Create notification channel
        createNotificationChannel();
        
        // Note: User data and emergency listener will be set up in onStartCommand()
        // to ensure we always have fresh user data when the service starts
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚨 EmergencyNotificationService started");
        
        // ALWAYS start as foreground service first to prevent crash
        Notification notification = createForegroundNotification();
        startForeground(9998, notification);
        
        // Refresh user data from SharedPreferences on each start to ensure we have current user info
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        userId = prefs.getString("user_id", null);
        userType = prefs.getString("user_type", null);
        
        // Check if user has logged out - if so, don't restart
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (isLoggedOut) {
            Log.w(TAG, "⚠️ User has logged out, stopping EmergencyNotificationService");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Check if user is still logged in and is a rescuer
        if (userId == null || userType == null || !userType.equals("rescuer")) {
            Log.w(TAG, "⚠️ No valid rescuer session (userId: " + userId + ", userType: " + userType + "), stopping EmergencyNotificationService");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Restart emergency listener with fresh user data
        startEmergencyListener();
        
        return START_STICKY; // Restart service if killed
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🚨 EmergencyNotificationService destroyed");
        
        // Remove emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
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
        Intent notificationIntent = getDashboardIntentForCurrentUser();
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_emergency_monitor))
                .setContentText(getString(R.string.notification_listening_alerts))
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }
    
    /**
     * Creates notification channel for emergency notifications
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setSound(getCustomAlarmSound(), null);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            
            Log.d(TAG, "✅ Emergency notification channel created");
        }
    }
    
    /**
     * Starts listening for emergency notifications
     */
    private void startEmergencyListener() {
        Log.d(TAG, "🚨 Starting emergency listener in background service...");
        
        // Check if user is a rescuer
        if (userId == null || userType == null || !userType.equals("rescuer")) {
            Log.w(TAG, "⚠️ User is not a rescuer (userId: " + userId + ", userType: " + userType + "), stopping emergency listener");
            stopSelf();
            return;
        }
        
        // Always remove any existing listener first to prevent duplicates
        if (emergencyListener != null) {
            Log.d(TAG, "Removing existing emergency listener before starting new one");
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Listen for new emergency notifications
        emergencyListener = db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "🚨 Emergency listener failed.", e);
                        return;
                    }
                    
                    // Check if user is still a rescuer before processing notifications
                    SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                    String currentUserType = prefs.getString("user_type", null);
                    boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
                    
                    if (isLoggedOut || currentUserType == null || !currentUserType.equals("rescuer")) {
                        Log.w(TAG, "⚠️ User is no longer a rescuer or has logged out (userType: " + currentUserType + ", isLoggedOut: " + isLoggedOut + "), stopping EmergencyNotificationService");
                        stopSelf();
                        return;
                    }
                    
                    Log.d(TAG, "🚨 Emergency listener triggered - snapshots: " + (snapshots != null ? snapshots.size() : "null"));
                    
                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            Log.d(TAG, "🚨 Document change type: " + dc.getType() + " for document: " + dc.getDocument().getId());
                            
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // New emergency detected!
                                DocumentSnapshot emergency = dc.getDocument();
                                Log.d(TAG, "🚨 NEW EMERGENCY DETECTED IN BACKGROUND: " + emergency.getId());
                                handleNewEmergency(emergency);
                            }
                        }
                    } else {
                        Log.d(TAG, "🚨 No active emergencies found");
                    }
                });
        
        Log.d(TAG, "🚨 Emergency listener started successfully in background service");
    }
    
    /**
     * Handles a new emergency notification
     */
    private void handleNewEmergency(DocumentSnapshot emergency) {
        // Double-check user type before processing notification
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String currentUserType = prefs.getString("user_type", null);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        
        if (isLoggedOut || currentUserType == null || !currentUserType.equals("rescuer")) {
            Log.w(TAG, "⚠️ User is no longer a rescuer or has logged out (userType: " + currentUserType + ", isLoggedOut: " + isLoggedOut + "), ignoring emergency notification");
            stopSelf();
            return;
        }
        
        String title = emergency.getString("title");
        String message = emergency.getString("message");
        String seniorName = emergency.getString("seniorName");
        String seniorPhone = emergency.getString("seniorPhone");
        String locationAddress = emergency.getString("locationAddress");
        Double latitude = emergency.getDouble("latitude");
        Double longitude = emergency.getDouble("longitude");
        String helpRequestId = emergency.getString("helpRequestId");
        
        Log.d(TAG, "🚨🚨🚨 NEW EMERGENCY RECEIVED IN BACKGROUND 🚨🚨🚨");
        Log.d(TAG, "🚨 Senior: " + seniorName);
        Log.d(TAG, "🚨 Location: " + locationAddress);
        Log.d(TAG, "🚨 Help Request ID: " + helpRequestId);
        
        // Check if this rescuer has already responded to this emergency
        String respondedBy = emergency.getString("respondedBy");
        if (respondedBy != null && respondedBy.equals(userId)) {
            Log.d(TAG, "Current rescuer (userId: " + userId + ") already responded to this emergency, skipping notification for: " + helpRequestId);
            return;
        }
        
        // Additional safety check: ensure we have a valid current user ID
        if (userId == null) {
            Log.w(TAG, "⚠️ No current user ID available, skipping emergency notification for: " + helpRequestId);
            return;
        }
        
        // Check if emergency is within 5km radius (if location is available)
        if (latitude != null && longitude != null && currentLat != 0.0 && currentLong != 0.0) {
            if (!isWithinRadius(latitude, longitude)) {
                Log.d(TAG, "Emergency is outside 5km radius, skipping notification for: " + helpRequestId);
                return;
            }
        } else {
            Log.w(TAG, "Emergency location data missing or rescuer location not available, allowing notification for: " + helpRequestId);
        }
        
        // Show emergency notification
        showEmergencyNotification(title, message, seniorName, seniorPhone, locationAddress, helpRequestId);
        
    }
    
    /**
     * Shows emergency notification
     */
    private void showEmergencyNotification(String title, String message, String seniorName, 
                                         String seniorPhone, String locationAddress, String helpRequestId) {
        
        Intent notificationIntent = getDashboardIntentForCurrentUser();
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationIntent.putExtra("notification_clicked", true);
        notificationIntent.putExtra("helpRequestId", helpRequestId);
        notificationIntent.putExtra("emergency_notification", true);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("location", locationAddress);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            helpRequestId.hashCode(),
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title != null ? title : getString(R.string.notification_emergency_help_request))
                .setContentText(message != null ? message : String.format(getString(R.string.notification_emergency_help_default), seniorName))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 EMERGENCY ALERT 🚨\n\n" +
                                "👤 Senior: " + seniorName + "\n" +
                                "📍 Location: " + locationAddress + "\n" +
                                "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not provided") + "\n\n" +
                                "Please respond immediately!"))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000)
                .setSound(getCustomAlarmSound())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)
                .build();
        
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(helpRequestId.hashCode(), notification);
        
        Log.d(TAG, "✅ Emergency notification shown for: " + seniorName);
    }
    
    
    /**
     * Checks if emergency is within 5km radius
     */
    private boolean isWithinRadius(double emergencyLat, double emergencyLong) {
        if (currentLat == 0.0 || currentLong == 0.0) {
            return true; // If rescuer location not available, allow notification
        }
        
        double distance = calculateDistance(currentLat, currentLong, emergencyLat, emergencyLong);
        return distance <= 5.0; // 5km radius
    }
    
    /**
     * Calculates distance between two points in kilometers
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // Distance in km
    }
    
    private Uri getCustomAlarmSound() {
        try {
            // Try to use custom alarm sound
            Uri customSound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.emergency_alarm);
            Log.d(TAG, "Custom alarm sound URI: " + customSound.toString());
            return customSound;
        } catch (Exception e) {
            // Fallback to system alarm sound if custom file doesn't exist
            Log.w(TAG, "Custom alarm sound not found, using system alarm sound. Error: " + e.getMessage());
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }
    
    /**
     * Gets the appropriate dashboard intent based on the current user type
     * @return Intent for the current user's dashboard
     */
    private Intent getDashboardIntentForCurrentUser() {
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        String userType = sharedPreferences.getString("userType", null);
        
        Log.d(TAG, "Getting dashboard intent for user type: " + userType);
        
        // Handle null userType
        if (userType == null) {
            Log.w(TAG, "User type is null, defaulting to rescuer dashboard");
            return new Intent(this, Rescuer_Dashboard.class);
        }
        
        switch (userType) {
            case "hospital":
                return new Intent(this, Hospital_Dashboard.class);
            case "rescuer":
                return new Intent(this, Rescuer_Dashboard.class);
            case "barangay":
                return new Intent(this, Barangay_Dashboard.class);
            case "seniors":
            case "senior":
                return new Intent(this, Senior_Dashboard.class);
            default:
                // Default to rescuer dashboard if user type is unknown
                Log.w(TAG, "Unknown user type: " + userType + ", defaulting to rescuer dashboard");
                return new Intent(this, Rescuer_Dashboard.class);
        }
    }
}
