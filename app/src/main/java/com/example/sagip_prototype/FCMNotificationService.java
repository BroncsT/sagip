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
        
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
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
    
    private void showSimpleNotification(String title, String body) {
        createNotificationChannel();
        
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
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
        
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
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
}
