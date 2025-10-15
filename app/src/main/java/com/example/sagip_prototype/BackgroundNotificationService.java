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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Background service that monitors for notifications even when the app is closed
 * This ensures rescuers receive notifications for hospital updates and emergencies
 */
public class BackgroundNotificationService extends Service {
    
    private static final String TAG = "BackgroundNotificationService";
    private static final String CHANNEL_ID = "background_notification_service";
    private static final int FOREGROUND_NOTIFICATION_ID = 1001;
    
    private FirebaseFirestore db;
    private ListenerRegistration notificationListener;
    private String currentUserId;
    private String currentUserType;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BackgroundNotificationService created");
        
        db = FirebaseFirestore.getInstance();
        
        // Get current user info
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        currentUserId = sharedPreferences.getString("userId", null);
        currentUserType = sharedPreferences.getString("userType", null);
        
        Log.d(TAG, "Current user: " + currentUserId + ", type: " + currentUserType);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "BackgroundNotificationService started");
        
        // Check if user has logged out - if so, don't restart
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (isLoggedOut) {
            Log.w(TAG, "⚠️ User has logged out, stopping BackgroundNotificationService");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Create foreground service notification
        createForegroundNotification();
        
        // Start monitoring for notifications if user is logged in
        if (currentUserId != null && currentUserType != null) {
            startNotificationMonitoring();
        } else {
            Log.w(TAG, "User not logged in, stopping service");
            stopSelf();
        }
        
        return START_STICKY; // Restart service if killed
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "BackgroundNotificationService destroyed");
        
        if (notificationListener != null) {
            notificationListener.remove();
        }
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Creates a foreground notification to keep the service running
     */
    private void createForegroundNotification() {
        createNotificationChannel();
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SAGIPP Background Service")
                .setContentText("Monitoring for emergency notifications...")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
        
        startForeground(FOREGROUND_NOTIFICATION_ID, notification);
    }
    
    /**
     * Starts monitoring for notifications in the database
     */
    private void startNotificationMonitoring() {
        if (currentUserId == null || currentUserType == null) {
            Log.w(TAG, "Cannot start monitoring - user not logged in");
            return;
        }
        
        Log.d(TAG, "Starting notification monitoring for user: " + currentUserId);
        
        // Monitor for all user types - rescuers receive hospital notifications, 
        // all users can receive emergency notifications
        Log.d(TAG, "Starting notification monitoring for user type: " + currentUserType);
        
        // Listen for new notifications based on user type
        String collectionName = getCollectionNameForUserType(currentUserType);
        Query notificationsQuery = db.collection("Sagip")
                .document("users")
                .collection(collectionName)
                .document(currentUserId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1);
        
        notificationListener = notificationsQuery.addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error listening to notifications: " + error.getMessage(), error);
                return;
            }
            
            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                for (QueryDocumentSnapshot document : querySnapshot) {
                    handleNewNotification(document);
                }
            }
        });
    }
    
    /**
     * Handles a new notification from the database
     */
    private void handleNewNotification(QueryDocumentSnapshot document) {
        String type = document.getString("type");
        boolean read = Boolean.TRUE.equals(document.getBoolean("read"));
        
        if (read) {
            Log.d(TAG, "Notification already read, skipping");
            return;
        }
        
        Log.d(TAG, "New notification received: " + type);
        
        if ("hospital_status_update".equals(type)) {
            // Only rescuers receive hospital status updates
            if ("rescuer".equals(currentUserType)) {
                handleHospitalUpdateNotification(document);
            }
        } else if ("emergency_help_request".equals(type)) {
            // All user types can receive emergency notifications
            handleEmergencyNotification(document);
        }
    }
    
    /**
     * Handles hospital status update notification
     */
    private void handleHospitalUpdateNotification(QueryDocumentSnapshot document) {
        String hospitalName = document.getString("hospitalName");
        String hospitalStatus = document.getString("hospitalStatus");
        Long availableBeds = document.getLong("availableBeds");
        Long availableDoctors = document.getLong("availableDoctors");
        
        if (hospitalName != null && hospitalStatus != null && 
            availableBeds != null && availableDoctors != null) {
            
            Log.d(TAG, "Processing hospital update notification: " + hospitalName);
            
            // Show notification
            showHospitalUpdateNotification(
                hospitalName, 
                hospitalStatus, 
                availableBeds.intValue(), 
                availableDoctors.intValue()
            );
            
            // Mark as read
            markNotificationAsRead(document.getId());
        }
    }
    
    /**
     * Handles emergency notification
     */
    private void handleEmergencyNotification(QueryDocumentSnapshot document) {
        String seniorName = document.getString("seniorName");
        String emergencyType = document.getString("emergencyType");
        String location = document.getString("location");
        String phoneNumber = document.getString("phoneNumber");
        
        if (seniorName != null && emergencyType != null && 
            location != null && phoneNumber != null) {
            
            Log.d(TAG, "Processing emergency notification: " + seniorName);
            
            // Show emergency notification
            showEmergencyNotification(
                seniorName, 
                emergencyType, 
                location, 
                phoneNumber
            );
            
            // Mark as read
            markNotificationAsRead(document.getId());
        }
    }
    
    /**
     * Shows hospital update notification
     */
    private void showHospitalUpdateNotification(String hospitalName, String hospitalStatus, 
                                             int availableBeds, int availableDoctors) {
        
        Intent intent = getDashboardIntentForCurrentUser();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
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
                .setSound(getCustomAlarmSound())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), notification);
        
        Log.d(TAG, "Hospital update notification shown: " + hospitalName);
    }
    
    /**
     * Shows emergency notification
     */
    private void showEmergencyNotification(String seniorName, String emergencyType, 
                                        String location, String phoneNumber) {
        
        Intent intent = getDashboardIntentForCurrentUser();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("emergency_notification", true);
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("emergency_type", emergencyType);
        intent.putExtra("location", location);
        intent.putExtra("phone_number", phoneNumber);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String emergencyEmoji = getEmergencyEmoji(emergencyType);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚨 EMERGENCY HELP REQUEST")
                .setContentText(seniorName + " needs " + emergencyEmoji + " " + emergencyType)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 EMERGENCY ALERT 🚨\n\n" +
                                "👤 Senior: " + seniorName + "\n" +
                                "🆘 Emergency: " + emergencyEmoji + " " + emergencyType + "\n" +
                                "📍 Location: " + location + "\n" +
                                "📞 Phone: " + phoneNumber + "\n\n" +
                                "Please respond immediately!"))
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000)
                .setSound(getCustomAlarmSound())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(9999, notification);
        
        Log.d(TAG, "Emergency notification shown for: " + seniorName);
    }
    
    /**
     * Marks a notification as read in the database
     */
    private void markNotificationAsRead(String notificationId) {
        String collectionName = getCollectionNameForUserType(currentUserType);
        db.collection("Sagip")
                .document("users")
                .collection(collectionName)
                .document(currentUserId)
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
    
    /**
     * Gets the correct collection name for the user type
     */
    private String getCollectionNameForUserType(String userType) {
        switch (userType) {
            case "rescuer":
                return "rescuer";
            case "hospital":
                return "hospital";
            case "barangay":
                return "barangay";
            case "senior":
            case "seniors":
                return "seniors";
            default:
                return "rescuer"; // Default fallback
        }
    }
    
    /**
     * Creates notification channel
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Background Notification Service",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Background service for monitoring notifications");
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
    
    private String getEmergencyEmoji(String emergencyType) {
        switch (emergencyType.toLowerCase()) {
            case "medical":
                return "🏥";
            case "fall":
                return "⚠️";
            case "accident":
                return "🚑";
            case "fire":
                return "🔥";
            case "police":
                return "👮";
            case "other":
                return "🆘";
            default:
                return "🚨";
        }
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
        Log.d(TAG, "Getting dashboard intent for user type: " + currentUserType);
        
        switch (currentUserType) {
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
                Log.w(TAG, "Unknown user type: " + currentUserType + ", defaulting to rescuer dashboard");
                return new Intent(this, Rescuer_Dashboard.class);
        }
    }
}
