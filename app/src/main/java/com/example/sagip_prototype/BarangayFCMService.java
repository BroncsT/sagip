package com.example.sagip_prototype;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class BarangayFCMService extends FirebaseMessagingService {
    private static final String TAG = "BarangayFCMService";
    private static final String CHANNEL_ID = "barangay_emergency_channel";
    private static final int NOTIFICATION_ID = 2000;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "🔔 FCM Message received from: " + remoteMessage.getFrom());

        // Check if message contains data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "📱 Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        }

        // Check if message contains notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "📱 Message notification body: " + remoteMessage.getNotification().getBody());
            handleNotificationMessage(remoteMessage);
        }
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "🔔 Refreshed token: " + token);
        // Send token to server if needed
        sendRegistrationToServer(token);
    }

    private void handleDataMessage(Map<String, String> data) {
        String type = data.get("type");
        String title = data.get("title");
        String message = data.get("message");
        String seniorName = data.get("seniorName");
        String seniorPhone = data.get("seniorPhone");
        String locationAddress = data.get("locationAddress");
        String barangay = data.get("barangay");
        String requestId = data.get("requestId");
        String emergencyType = data.get("emergencyType");

        Log.d(TAG, "🚨 Handling FCM data message - Type: " + type + ", Senior: " + seniorName);

        if ("EMERGENCY_ALERT".equals(type)) {
            showEmergencyAlertNotification(
                title != null ? title : "🚨 Emergency Alert in " + barangay,
                message != null ? message : "Senior " + seniorName + " needs emergency assistance",
                seniorName, seniorPhone, locationAddress, barangay, requestId, emergencyType
            );
        }
    }

    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            showSimpleNotification(
                notification.getTitle() != null ? notification.getTitle() : "Emergency Alert",
                notification.getBody() != null ? notification.getBody() : "Emergency notification"
            );
        }
    }

    private void showEmergencyAlertNotification(String title, String message, String seniorName,
                                             String seniorPhone, String locationAddress, 
                                             String barangay, String requestId, String emergencyType) {
        Log.d(TAG, "🚨 Showing FCM EMERGENCY ALERT notification for: " + seniorName);

        // Create intent to open Barangay_Dashboard
        Intent intent = new Intent(this, Barangay_Dashboard.class);
        intent.putExtra("notification_id", "fcm_" + System.currentTimeMillis());
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("senior_phone", seniorPhone);
        intent.putExtra("location_address", locationAddress);
        intent.putExtra("barangay", barangay);
        intent.putExtra("request_id", requestId);
        intent.putExtra("emergency_type", emergencyType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                NOTIFICATION_ID, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create EMERGENCY ALERT notification with maximum priority
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_emergency)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.ic_emergency))
                .setContentTitle(getString(R.string.barangay_emergency_alert_title))
                .setContentText(getString(R.string.barangay_emergency_alert_content, barangay))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 EMERGENCY ALERT 🚨\n\n" +
                                "SENIOR: " + seniorName.toUpperCase() + "\n" +
                                "PHONE: " + seniorPhone + "\n" +
                                "LOCATION: " + locationAddress + "\n" +
                                "BARANGAY: " + barangay.toUpperCase() + "\n\n" +
                                "IMMEDIATE ACTION REQUIRED!"))
                .setPriority(NotificationCompat.PRIORITY_MAX) // Maximum priority
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false) // Don't auto-cancel - user must manually dismiss
                .setOngoing(true) // Make it ongoing so it can't be swiped away
                .setContentIntent(pendingIntent)
                .setSound(getEmergencyAlertSound())
                .setVibrate(getEmergencyVibrationPattern())
                .setLights(0xFF0000, 500, 500) // Red light, faster blinking
                .setColor(0xFF0000) // Red color
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true) // Show as full screen alert
                .setTimeoutAfter(300000); // Auto-dismiss after 5 minutes if not handled

        // Add action buttons
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + seniorPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                this, 
                NOTIFICATION_ID + 1, 
                callIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        builder.addAction(R.drawable.ic_emergency, "CALL SENIOR", callPendingIntent);
        builder.addAction(R.drawable.ic_emergency, "VIEW DETAILS", pendingIntent);

        // Show notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            createNotificationChannel();
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "🚨 FCM EMERGENCY ALERT notification sent to barangay user");
            
            // Also show as heads-up notification
            showHeadsUpNotification(seniorName, barangay, seniorPhone);
        }
    }

    private void showSimpleNotification(String title, String message) {
        Log.d(TAG, "📱 Showing simple FCM notification: " + title);

        Intent intent = new Intent(this, Barangay_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                NOTIFICATION_ID + 1, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_emergency)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getNotificationSound())
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFF0000, 1000, 1000);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            createNotificationChannel();
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
            Log.d(TAG, "📤 Simple FCM notification sent");
        }
    }

    private void sendRegistrationToServer(String token) {
        // TODO: Send token to your server if needed
        Log.d(TAG, "📤 FCM Token: " + token);
    }

    private Uri getNotificationSound() {
        // Use custom alarm sound if available, otherwise use default
        try {
            return Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.emergency_alarm);
        } catch (Exception e) {
            Log.w(TAG, "Custom alarm sound not found, using default", e);
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }
    
    private Uri getEmergencyAlertSound() {
        // Use the most urgent alarm sound for emergency alerts
        try {
            return Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.emergency_alarm);
        } catch (Exception e) {
            Log.w(TAG, "Emergency alarm sound not found, using system alarm", e);
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }
    
    private long[] getEmergencyVibrationPattern() {
        // More intense vibration pattern for emergency alerts
        return new long[]{0, 1000, 200, 1000, 200, 1000, 200, 1000, 200, 1000};
    }
    
    private void showHeadsUpNotification(String seniorName, String barangay, String seniorPhone) {
        Log.d(TAG, "🚨 Showing heads-up emergency alert for: " + seniorName);
        
        // Create a separate heads-up notification
        Intent intent = new Intent(this, Barangay_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                NOTIFICATION_ID + 2, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder headsUpBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_emergency)
                .setContentTitle(getString(R.string.barangay_emergency_heads_up_title, barangay))
                .setContentText(getString(R.string.barangay_emergency_heads_up_content))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getEmergencyAlertSound())
                .setVibrate(getEmergencyVibrationPattern())
                .setLights(0xFF0000, 500, 500)
                .setColor(0xFF0000)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true);
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID + 2, headsUpBuilder.build());
            Log.d(TAG, "🚨 Heads-up emergency alert sent");
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "🚨 EMERGENCY ALERTS";
            String description = "Critical emergency notifications for barangay officials - Maximum priority alerts";
            int importance = NotificationManager.IMPORTANCE_MAX; // Maximum importance

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(0xFF0000); // Red light
            channel.enableVibration(true);
            channel.setVibrationPattern(getEmergencyVibrationPattern()); // Intense vibration
            channel.setBypassDnd(true); // Bypass Do Not Disturb
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            channel.setImportance(NotificationManager.IMPORTANCE_MAX);

            // Set custom emergency sound
            Uri soundUri = getEmergencyAlertSound();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // Enforce sound even in silent mode
                    .build();
            channel.setSound(soundUri, audioAttributes);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "🚨 FCM EMERGENCY ALERT channel created with maximum priority");
            }
        }
    }
}
