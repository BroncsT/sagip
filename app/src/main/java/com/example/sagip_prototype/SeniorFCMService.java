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

public class SeniorFCMService extends FirebaseMessagingService {
    private static final String TAG = "SeniorFCMService";
    private static final String CHANNEL_ID = "senior_emergency_channel";
    private static final int NOTIFICATION_ID = 1000;

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
        String rescuerName = data.get("rescuerName");
        String rescuerPhone = data.get("rescuerPhone");
        String rescuerTeam = data.get("rescuerTeam");
        String requestId = data.get("requestId");

        Log.d(TAG, "🚑 Handling FCM data message - Type: " + type + ", Rescuer: " + rescuerName);

        if ("RESCUER_RESPONSE".equals(type)) {
            showRescuerResponseNotification(
                title != null ? title : "🚑 Help is on the way!",
                message != null ? message : rescuerName + " is responding to your emergency",
                rescuerName, rescuerPhone, rescuerTeam, requestId
            );
        }
    }

    private void handleNotificationMessage(RemoteMessage remoteMessage) {
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            showSimpleNotification(
                notification.getTitle() != null ? notification.getTitle() : "Emergency Update",
                notification.getBody() != null ? notification.getBody() : "Emergency notification"
            );
        }
    }

    private void showRescuerResponseNotification(String title, String message, String rescuerName,
                                               String rescuerPhone, String rescuerTeam, String requestId) {
        Log.d(TAG, "🚑 Showing FCM rescuer response notification from: " + rescuerName);

        // Create intent to open Senior_Dashboard
        Intent intent = new Intent(this, Senior_Dashboard.class);
        intent.putExtra("notification_id", "fcm_" + System.currentTimeMillis());
        intent.putExtra("rescuer_name", rescuerName);
        intent.putExtra("rescuer_phone", rescuerPhone);
        intent.putExtra("rescuer_team", rescuerTeam);
        intent.putExtra("request_id", requestId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                NOTIFICATION_ID, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_emergency)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Rescuer: " + rescuerName + "\n" +
                                "Phone: " + rescuerPhone + "\n" +
                                "Team: " + (rescuerTeam != null ? rescuerTeam : "Rescue Team") + "\n" +
                                "Request ID: " + requestId))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getNotificationSound())
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFF0000, 1000, 1000);

        // Show notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            createNotificationChannel();
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "📤 FCM Rescuer response notification sent to senior");
        }
    }

    private void showSimpleNotification(String title, String message) {
        Log.d(TAG, "📱 Showing simple FCM notification: " + title);

        Intent intent = new Intent(this, Senior_Dashboard.class);
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Senior Emergency Updates";
            String description = "Emergency notifications for senior users";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(0xFF0000);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});

            // Set custom sound
            Uri soundUri = getNotificationSound();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(soundUri, audioAttributes);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "🔔 FCM Notification channel created");
            }
        }
    }
}
