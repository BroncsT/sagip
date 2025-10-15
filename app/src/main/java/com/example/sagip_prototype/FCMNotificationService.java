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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class FCMNotificationService extends FirebaseMessagingService {
    
    private static final String TAG = "FCMNotificationService";
    private static final String CHANNEL_ID = "fcm_hospital_updates";
    private static final int NOTIFICATION_ID = 3001;
    
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "🔔 FCM message received: " + remoteMessage.getMessageId());
        Log.d(TAG, "Message from: " + remoteMessage.getFrom());
        Log.d(TAG, "Message type: " + (remoteMessage.getData().isEmpty() ? "notification" : "data"));
        
        // Check if user has logged out before processing any notifications
        if (isUserLoggedOut()) {
            Log.w(TAG, "🚫 User has logged out, ignoring FCM message");
            return;
        }
        
        // Verify user context is valid
        if (!isValidUserContext()) {
            Log.w(TAG, "🚫 Invalid user context, ignoring FCM message");
            return;
        }
        
        // Handle data payload (this is what we use for custom notifications)
        Map<String, String> data = remoteMessage.getData();
        if (data != null && !data.isEmpty()) {
            Log.d(TAG, "📊 Message data payload: " + data);
            handleDataMessage(data);
        }
        
        // Handle notification payload (system-generated notifications)
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            Log.d(TAG, "📱 Message notification payload: " + notification.getTitle());
            handleNotificationMessage(notification);
        }
        
        // If both data and notification are present, prioritize data payload
        if (data != null && !data.isEmpty() && notification != null) {
            Log.d(TAG, "⚠️ Both data and notification payload present - using data payload");
        }
    }
    
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
        
        // Send token to server for this user
        sendTokenToServer(token);
    }
    
    private void handleDataMessage(Map<String, String> data) {
        String type = data.get("type");
        
        if ("hospital_status_update".equals(type)) {
            // Check if current user is a hospital - if so, don't show the notification
            // to prevent hospitals from receiving their own status update notifications
            if (isCurrentUserHospital()) {
                Log.d(TAG, "🚫 Skipping hospital status update notification - current user is a hospital");
                return;
            }
            
            String hospitalName = data.get("hospitalName");
            String hospitalStatus = data.get("hospitalStatus");
            String availableBeds = data.get("availableBeds");
            String availableDoctors = data.get("availableDoctors");
            
            if (hospitalName != null && hospitalStatus != null && 
                availableBeds != null && availableDoctors != null) {
                
                showHospitalUpdateNotification(
                    hospitalName, 
                    hospitalStatus, 
                    Integer.parseInt(availableBeds), 
                    Integer.parseInt(availableDoctors)
                );
            }
        } else if ("emergency_help_request".equals(type)) {
            String seniorName = data.get("seniorName");
            String emergencyType = data.get("emergencyType");
            String location = data.get("location");
            String phoneNumber = data.get("phoneNumber");
            
            if (seniorName != null && emergencyType != null && 
                location != null && phoneNumber != null) {
                
                showEmergencyNotification(
                    seniorName, 
                    emergencyType, 
                    location, 
                    phoneNumber
                );
            }
        }
    }
    
    private void handleNotificationMessage(RemoteMessage.Notification notification) {
        // Handle notification payload if needed
        String title = notification.getTitle();
        String body = notification.getBody();
        
        if (title != null && body != null) {
            showSimpleNotification(title, body);
        }
    }
    
    private void showHospitalUpdateNotification(String hospitalName, String hospitalStatus, 
                                             int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "🏥 Showing hospital update notification: " + hospitalName);
        createNotificationChannel();
        
        Intent intent = getDashboardIntentForCurrentUser();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("notification_type", "hospital_update");
        intent.putExtra("hospital_name", hospitalName);
        intent.putExtra("hospital_status", hospitalStatus);
        intent.putExtra("available_beds", availableBeds);
        intent.putExtra("available_doctors", availableDoctors);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get status emoji
        String statusEmoji = getStatusEmoji(hospitalStatus);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_hospital_status_updated))
                .setContentText(String.format(getString(R.string.notification_hospital_status_text), hospitalName, statusEmoji, hospitalStatus.toUpperCase()))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(String.format(getString(R.string.notification_hospital_status_text), hospitalName, statusEmoji, hospitalStatus.toUpperCase()) + 
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
    
    private void showSimpleNotification(String title, String body) {
        createNotificationChannel();
        
        Intent intent = getDashboardIntentForCurrentUser();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setLights(0xFF2196F3, 1000, 1000)
                .setSound(getCustomAlarmSound())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), notification);
        
        Log.d(TAG, "Simple notification shown: " + title);
    }
    
    private void showEmergencyNotification(String seniorName, String emergencyType, 
                                        String location, String phoneNumber) {
        
        Log.d(TAG, "🚨 Showing emergency notification: " + seniorName);
        createNotificationChannel();
        
        Intent intent = getDashboardIntentForCurrentUser();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("notification_type", "emergency");
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
        
        // Get emergency emoji
        String emergencyEmoji = getEmergencyEmoji(emergencyType);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_emergency_help_request))
                .setContentText(String.format(getString(R.string.notification_emergency_help_text), seniorName, emergencyEmoji, emergencyType))
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
        notificationManager.notify(9999, notification); // Use fixed ID for emergency notifications
        
        Log.d(TAG, "Emergency notification shown for: " + seniorName);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Hospital Status Updates",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Real-time hospital status update notifications");
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
    
    private void sendTokenToServer(String token) {
        Log.d(TAG, "Sending FCM token to server: " + token);
        
        // Get current user info from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        String userId = sharedPreferences.getString("userId", null);
        String userType = sharedPreferences.getString("userType", null);
        
        if (userId != null && userType != null) {
            // Update FCM token in database using native notification sender
            NativeNotificationSender.updateUserFCMToken(userId, userType, token);
            Log.d(TAG, "FCM token sent to server for user: " + userId + ", type: " + userType);
        } else {
            Log.w(TAG, "Cannot send FCM token - user not logged in or user info missing");
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
     * Checks if the current user is a hospital user
     * @return true if current user is a hospital, false otherwise
     */
    private boolean isCurrentUserHospital() {
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        String userType = sharedPreferences.getString("userType", null);
        boolean isHospital = "hospital".equals(userType);
        Log.d(TAG, "Current user type: " + userType + ", isHospital: " + isHospital);
        return isHospital;
    }
    
    /**
     * Checks if the user has logged out
     * @return true if user has logged out, false otherwise
     */
    private boolean isUserLoggedOut() {
        // Check both SharedPreferences for logout status
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        
        boolean userLoggedOut = userPrefs.getBoolean("user_logged_out", false);
        boolean sagipLoggedOut = sagipPrefs.getBoolean("user_logged_out", false);
        boolean isLoggedIn = sagipPrefs.getBoolean("isLoggedIn", false);
        
        boolean isLoggedOut = userLoggedOut || sagipLoggedOut || !isLoggedIn;
        
        Log.d(TAG, "User logout check - user_prefs: " + userLoggedOut + 
                   ", sagip_prefs: " + sagipLoggedOut + 
                   ", isLoggedIn: " + isLoggedIn + 
                   ", result: " + isLoggedOut);
        
        return isLoggedOut;
    }
    
    /**
     * Validates that the user context is valid and consistent
     * @return true if user context is valid, false otherwise
     */
    private boolean isValidUserContext() {
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        
        String userId1 = userPrefs.getString("user_id", null);
        String userType1 = userPrefs.getString("user_type", null);
        String userId2 = sagipPrefs.getString("userId", null);
        String userType2 = sagipPrefs.getString("userType", null);
        
        // Check if user data exists in both SharedPreferences
        boolean hasUserData = (userId1 != null && userType1 != null) || (userId2 != null && userType2 != null);
        
        // Check if user data is consistent between both SharedPreferences
        boolean isConsistent = (userId1 == null || userId1.equals(userId2)) && 
                              (userType1 == null || userType1.equals(userType2));
        
        boolean isValid = hasUserData && isConsistent;
        
        Log.d(TAG, "User context validation - hasUserData: " + hasUserData + 
                   ", isConsistent: " + isConsistent + 
                   ", result: " + isValid);
        
        return isValid;
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
