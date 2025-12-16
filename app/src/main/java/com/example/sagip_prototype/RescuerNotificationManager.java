package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Dedicated notification manager for rescuers to ensure they receive notifications
 * even when the app is completely closed
 */
public class RescuerNotificationManager {
    
    private static final String TAG = "RescuerNotificationManager";
    private static final String CHANNEL_ID = "rescuer_notifications";
    private static final String CHANNEL_NAME = "Rescuer Notifications";
    private static final String CHANNEL_DESCRIPTION = "Critical notifications for rescuers";
    
    private static ListenerRegistration notificationListener;
    private static boolean isMonitoring = false;
    
    /**
     * Starts monitoring for rescuer notifications
     */
    public static void startMonitoring(Context context) {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring rescuer notifications");
            return;
        }
        
        SharedPreferences sharedPreferences = context.getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString("userId", null);
        String userType = sharedPreferences.getString("userType", null);
        
        if (userId == null || !"rescuer".equals(userType)) {
            Log.d(TAG, "Not a rescuer user, skipping notification monitoring");
            return;
        }
        
        Log.d(TAG, "🚨 Starting rescuer notification monitoring for user: " + userId);
        isMonitoring = true;
        
        // Create notification channel
        createNotificationChannel(context);
        
        // Start real-time listener for notifications
        startNotificationListener(context, userId);
    }
    
    /**
     * Stops monitoring for rescuer notifications
     */
    public static void stopMonitoring() {
        if (notificationListener != null) {
            Log.d(TAG, "🛑 Stopping rescuer notification monitoring");
            notificationListener.remove();
            notificationListener = null;
        }
        isMonitoring = false;
    }
    
    /**
     * Starts real-time listener for rescuer notifications
     */
    private static void startNotificationListener(Context context, String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Listen for new notifications in real-time
        Query notificationsQuery = db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(userId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1);
        
        notificationListener = notificationsQuery.addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "❌ Error listening to rescuer notifications: " + error.getMessage(), error);
                return;
            }
            
            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                for (QueryDocumentSnapshot document : querySnapshot) {
                    handleNewNotification(context, document);
                }
            }
        });
        
        Log.d(TAG, "✅ Rescuer notification listener started");
    }
    
    /**
     * Handles a new notification for rescuers
     */
    private static void handleNewNotification(Context context, QueryDocumentSnapshot document) {
        String type = document.getString("type");
        boolean read = Boolean.TRUE.equals(document.getBoolean("read"));
        
        if (read) {
            Log.d(TAG, "Notification already read, skipping");
            return;
        }
        
        Log.d(TAG, "🔔 New rescuer notification received: " + type);
        
        if ("hospital_status_update".equals(type)) {
            // Skip showing hospital_status_update notifications here
            // FCMNotificationService already handles these via FCM push to prevent duplicate notifications
            Log.d(TAG, "📱 Skipping hospital_status_update display - FCMNotificationService handles this to prevent duplicate");
            // Still mark as read so it doesn't trigger again
            markNotificationAsRead(document.getId());
            return;
        } else if ("emergency_help_request".equals(type)) {
            handleEmergencyNotification(context, document);
        }
        
        // Mark as read
        markNotificationAsRead(document.getId());
    }
    
    /**
     * Handles hospital status update notification for rescuers
     */
    private static void handleHospitalUpdateNotification(Context context, QueryDocumentSnapshot document) {
        String hospitalName = document.getString("hospitalName");
        String hospitalStatus = document.getString("hospitalStatus");
        Long availableBeds = document.getLong("availableBeds");
        Long availableDoctors = document.getLong("availableDoctors");
        
        if (hospitalName != null && hospitalStatus != null && 
            availableBeds != null && availableDoctors != null) {
            
            Log.d(TAG, "🏥 Processing hospital update notification: " + hospitalName);
            
            // Show notification with high priority
            showHospitalUpdateNotification(
                context,
                hospitalName, 
                hospitalStatus, 
                availableBeds.intValue(), 
                availableDoctors.intValue()
            );
        }
    }
    
    /**
     * Handles emergency notification for rescuers
     */
    private static void handleEmergencyNotification(Context context, QueryDocumentSnapshot document) {
        String seniorName = document.getString("seniorName");
        String emergencyType = document.getString("emergencyType");
        String location = document.getString("location");
        String phoneNumber = document.getString("phoneNumber");
        
        if (seniorName != null && emergencyType != null && 
            location != null && phoneNumber != null) {
            
            Log.d(TAG, "🚨 Processing emergency notification: " + seniorName);
            
            // Show emergency notification with maximum priority
            showEmergencyNotification(
                context,
                seniorName, 
                emergencyType, 
                location, 
                phoneNumber
            );
        }
    }
    
    /**
     * Shows hospital update notification for rescuers
     */
    private static void showHospitalUpdateNotification(Context context, String hospitalName, String hospitalStatus, 
                                                     int availableBeds, int availableDoctors) {
        
        Intent intent = new Intent(context, Rescuer_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        // Add hospital data to intent for notification click handling
        intent.putExtra("notification_type", "hospital_status_update");
        intent.putExtra("hospital_name", hospitalName);
        intent.putExtra("hospital_status", hospitalStatus);
        intent.putExtra("available_beds", availableBeds);
        intent.putExtra("available_doctors", availableDoctors);
        intent.putExtra("highlight_hospital", hospitalName);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_hospital_status_updated))
                .setContentText(hospitalName + " is now " + hospitalStatus.toUpperCase())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(hospitalName + " has updated their status to " + hospitalStatus.toUpperCase() + 
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
                .setSound(getCustomAlarmSound(context))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), notification);
        
        Log.d(TAG, "✅ Hospital update notification shown: " + hospitalName);
    }
    
    /**
     * Shows emergency notification for rescuers
     */
    private static void showEmergencyNotification(Context context, String seniorName, String emergencyType, 
                                                String location, String phoneNumber) {
        
        Intent intent = new Intent(context, Rescuer_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("emergency_notification", true);
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("emergency_type", emergencyType);
        intent.putExtra("location", location);
        intent.putExtra("phone_number", phoneNumber);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String emergencyEmoji = getEmergencyEmoji(emergencyType);
        
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_emergency_help_request))
                .setContentText(String.format(context.getString(R.string.notification_emergency_help_text), seniorName, emergencyEmoji, emergencyType))
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
                .setSound(getCustomAlarmSound(context))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(9999, notification);
        
        Log.d(TAG, "✅ Emergency notification shown for: " + seniorName);
    }
    
    /**
     * Marks a notification as read in the database
     */
    private static void markNotificationAsRead(String notificationId) {
        SharedPreferences sharedPreferences = FirebaseFirestore.getInstance().getApp().getApplicationContext()
                .getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
        String userId = sharedPreferences.getString("userId", null);
        
        if (userId == null) {
            Log.w(TAG, "Cannot mark notification as read - no userId");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Notification marked as read: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to mark notification as read: " + notificationId, e);
                });
    }
    
    /**
     * Creates notification channel for rescuers
     */
    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setLightColor(0xFF2196F3);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            
            Log.d(TAG, "✅ Rescuer notification channel created");
        }
    }
    
    private static String getStatusEmoji(String status) {
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
    
    private static String getEmergencyEmoji(String emergencyType) {
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
    
    private static Uri getCustomAlarmSound(Context context) {
        try {
            // Try to use custom alarm sound
            Uri customSound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.emergency_alarm);
            Log.d(TAG, "Custom alarm sound URI: " + customSound.toString());
            return customSound;
        } catch (Exception e) {
            // Fallback to system alarm sound if custom file doesn't exist
            Log.w(TAG, "Custom alarm sound not found, using system alarm sound. Error: " + e.getMessage());
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }
}
