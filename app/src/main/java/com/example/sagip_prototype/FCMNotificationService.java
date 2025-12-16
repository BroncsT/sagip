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
    private static final String HOSPITAL_EMERGENCY_CHANNEL_ID = "hospital_emergency_channel";
    
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
        boolean dataHandled = false;
        if (data != null && !data.isEmpty()) {
            Log.d(TAG, "📊 Message data payload: " + data);
            handleDataMessage(data);
            dataHandled = true;
        }
        
        // Handle notification payload (system-generated notifications)
        // Skip if data payload was already handled to prevent duplicate notifications
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            if (dataHandled) {
                Log.d(TAG, "📱 Skipping notification payload - data payload already handled (prevents duplicate)");
            } else {
                Log.d(TAG, "📱 Message notification payload: " + notification.getTitle());
                handleNotificationMessage(notification);
            }
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
        } else if ("EMERGENCY_INCOMING".equals(type)) {
            // This is intended for hospital users
            SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
            String userType = sharedPreferences.getString("userType", null);
            if (!"hospital".equals(userType)) {
                Log.d(TAG, "🚫 Skipping incoming emergency notification - user is not a hospital (userType: " + userType + ")");
                return;
            }

            String title = data.get("title");
            String body = data.get("body");
            String hospitalName = data.get("hospital_name") != null ? data.get("hospital_name") : data.get("hospitalName");
            String seniorName = data.get("senior_name") != null ? data.get("senior_name") : data.get("seniorName");
            String rescuerName = data.get("rescuer_name") != null ? data.get("rescuer_name") : data.get("rescuerName");
            String emergencyId = data.get("emergency_id") != null ? data.get("emergency_id") : data.get("emergencyId");
            String emergencyType = data.get("emergency_type") != null ? data.get("emergency_type") : data.get("emergencyType");

            showHospitalIncomingEmergencyNotification(
                    title != null ? title : "🚨 Emergency Patient Incoming",
                    body != null ? body : "An emergency patient is incoming",
                    hospitalName,
                    seniorName,
                    rescuerName,
                    emergencyId,
                    emergencyType
            );
        } else if ("emergency_sos".equals(type)) {
            // Handle emergency SOS notifications for rescuers only
            SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
            String userType = sharedPreferences.getString("userType", null);
            
            if (!"rescuer".equals(userType)) {
                Log.d(TAG, "🚫 Skipping emergency SOS notification - user is not a rescuer (userType: " + userType + ")");
                return;
            }
            
            // Support both snake_case (from Firebase Functions) and camelCase (legacy) keys
            String seniorName = data.get("senior_name") != null ? data.get("senior_name") : data.get("seniorName");
            String seniorPhone = data.get("senior_phone") != null ? data.get("senior_phone") : data.get("seniorPhone");
            String locationAddress = data.get("location_address") != null ? data.get("location_address") : data.get("locationAddress");
            String emergencyType = data.get("emergency_type") != null ? data.get("emergency_type") : data.get("emergencyType");
            String requestId = data.get("request_id") != null ? data.get("request_id") : data.get("requestId");
            String seniorLat = data.get("senior_lat") != null ? data.get("senior_lat") : data.get("seniorLat");
            String seniorLng = data.get("senior_lng") != null ? data.get("senior_lng") : data.get("seniorLng");
            
            Log.d(TAG, "🚨 Emergency SOS notification received for rescuer - Senior: " + seniorName);
            
            if (seniorName != null && locationAddress != null) {
                showEmergencySOSNotification(
                    seniorName,
                    seniorPhone != null ? seniorPhone : "",
                    locationAddress,
                    emergencyType != null ? emergencyType : "medical",
                    requestId,
                    seniorLat,
                    seniorLng
                );
            } else {
                Log.w(TAG, "⚠️ Emergency SOS notification missing required data - seniorName: " + seniorName + ", locationAddress: " + locationAddress);
            }
        } else if ("RESCUER_RESPONSE".equals(type)) {
            // Handle rescuer response notifications for seniors
            // NOTE: SeniorNotificationService (Firestore listener) already handles this notification
            // Skip showing FCM notification to prevent duplicate notifications
            SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
            String userType = sharedPreferences.getString("userType", null);
            
            if (!"seniors".equals(userType) && !"senior".equals(userType)) {
                Log.d(TAG, "🚫 Skipping rescuer response notification - user is not a senior (userType: " + userType + ")");
                return;
            }
            
            String rescuerName = data.get("rescuerName");
            Log.d(TAG, "🚑 Rescuer response FCM received for senior - Rescuer: " + rescuerName);
            Log.d(TAG, "📱 Skipping FCM notification display - SeniorNotificationService (Firestore listener) handles this to prevent duplicate");
            // SeniorNotificationService listens to Firestore and will show the notification
            // No need to show notification here as it would cause duplicates
        } else if ("EMERGENCY_ALERT".equals(type)) {
            // Handle emergency alert notifications for barangay users
            SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
            String userType = sharedPreferences.getString("userType", null);
            
            if (!"barangay".equals(userType)) {
                Log.d(TAG, "🚫 Skipping emergency alert notification - user is not barangay (userType: " + userType + ")");
                return;
            }
            
            String title = data.get("title");
            String message = data.get("message");
            String seniorName = data.get("seniorName");
            String seniorPhone = data.get("seniorPhone");
            String locationAddress = data.get("locationAddress");
            String barangay = data.get("barangay");
            String requestId = data.get("requestId");
            String emergencyType = data.get("emergencyType");
            
            Log.d(TAG, "🚨 Emergency alert notification received for barangay - Senior: " + seniorName);
            
            showBarangayEmergencyAlertNotification(
                title != null ? title : "🚨 Emergency Alert in " + barangay,
                message != null ? message : "Senior " + seniorName + " needs emergency assistance",
                seniorName, seniorPhone, locationAddress, barangay, requestId, emergencyType
            );
        } else {
            // Handle generic data messages with title and body
            String title = data.get("title");
            String body = data.get("body");
            
            if (title != null && body != null) {
                Log.d(TAG, "📱 Generic data message received: " + title);
                showSimpleNotification(title, body);
            }
        }
    }
    
    private void handleNotificationMessage(RemoteMessage.Notification notification) {
        // Handle notification payload if needed
        String title = notification.getTitle();
        String body = notification.getBody();
        
        if (title != null && body != null) {
            // Check if this is an emergency SOS notification by checking the title
            if (title.contains("EMERGENCY SOS") || title.contains("🚨")) {
                Log.d(TAG, "🚨 Emergency SOS detected in notification payload");
                // Try to extract information from body or show as simple notification
                // The data payload should have the full details, but this is a fallback
                showSimpleNotification(title, body);
            } else {
                showSimpleNotification(title, body);
            }
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
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_hospital_status_updated))
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

    private void showHospitalIncomingEmergencyNotification(String title, String body,
                                                           String hospitalName, String seniorName,
                                                           String rescuerName, String emergencyId,
                                                           String emergencyType) {
        Log.d(TAG, "🚨 Showing hospital incoming emergency notification");
        createHospitalEmergencyNotificationChannel();

        Intent intent = new Intent(this, Hospital_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("notification_type", "EMERGENCY_INCOMING");
        if (hospitalName != null) intent.putExtra("hospital_name", hospitalName);
        if (seniorName != null) intent.putExtra("senior_name", seniorName);
        if (rescuerName != null) intent.putExtra("rescuer_name", rescuerName);
        if (emergencyId != null) intent.putExtra("emergency_id", emergencyId);
        if (emergencyType != null) intent.putExtra("emergency_type", emergencyType);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, HOSPITAL_EMERGENCY_CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000)
                .setSound(getCustomAlarmSound())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(7777, notification);
            Log.d(TAG, "✅ Hospital incoming emergency notification shown");
        } else {
            Log.e(TAG, "❌ NotificationManager is null, cannot show hospital incoming emergency notification");
        }
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

    private void createHospitalEmergencyNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager == null) {
                Log.e(TAG, "❌ NotificationManager is null, cannot create channel");
                return;
            }
            
            // Check if channel exists and needs to be recreated with sound
            // (Android doesn't allow modifying channel settings after creation)
            NotificationChannel existingChannel = notificationManager.getNotificationChannel(HOSPITAL_EMERGENCY_CHANNEL_ID);
            if (existingChannel != null) {
                if (existingChannel.getSound() == null) {
                    Log.d(TAG, "🔄 Existing hospital emergency channel has no sound, deleting and recreating");
                    notificationManager.deleteNotificationChannel(HOSPITAL_EMERGENCY_CHANNEL_ID);
                } else {
                    Log.d(TAG, "✅ Hospital emergency notification channel already exists with sound");
                    return;
                }
            }
            
            NotificationChannel channel = new NotificationChannel(
                    HOSPITAL_EMERGENCY_CHANNEL_ID,
                    "🚨 Hospital Emergency Incoming",
                    NotificationManager.IMPORTANCE_MAX
            );
            channel.setDescription("Critical incoming emergency patient notifications for hospitals");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000);
            channel.setBypassDnd(true);
            channel.setSound(getCustomAlarmSound(), new android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build());

            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "✅ Hospital emergency notification channel created with sound enabled");
        }
    }
    
    /**
     * Creates the emergency SOS notification channel
     */
    private void createEmergencySOSChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "emergency_sos_channel";
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_MAX
            );
            channel.setDescription("Critical emergency SOS notifications from seniors");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light
            channel.setSound(getCustomAlarmSound(), new android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build());
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "✅ Emergency SOS notification channel created");
        }
    }
    
    /**
     * Shows emergency SOS notification for rescuers
     */
    private void showEmergencySOSNotification(String seniorName, String seniorPhone, 
                                             String locationAddress, String emergencyType,
                                             String requestId, String seniorLat, String seniorLng) {
        
        Log.d(TAG, "🚨 Showing emergency SOS notification: " + seniorName);
        createEmergencySOSChannel();
        
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("emergency_sos_clicked", true);
        intent.putExtra("from_emergency_notification", true);
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("senior_phone", seniorPhone);
        intent.putExtra("location_address", locationAddress);
        intent.putExtra("emergency_type", emergencyType);
        if (requestId != null) {
            intent.putExtra("request_id", requestId);
        }
        if (seniorLat != null && !seniorLat.isEmpty() && !"0".equals(seniorLat)) {
            try {
                intent.putExtra("senior_lat", Double.parseDouble(seniorLat));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid seniorLat: " + seniorLat);
            }
        }
        if (seniorLng != null && !seniorLng.isEmpty() && !"0".equals(seniorLng)) {
            try {
                intent.putExtra("senior_lng", Double.parseDouble(seniorLng));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid seniorLng: " + seniorLng);
            }
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get emergency emoji
        String emergencyEmoji = getEmergencyEmoji(emergencyType);
        
        String channelId = "emergency_sos_channel";
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("🚨 EMERGENCY SOS - " + seniorName)
                .setContentText("Senior needs immediate help at " + locationAddress)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 EMERGENCY SOS ALERT 🚨\n\n" +
                                "👤 Senior: " + seniorName + "\n" +
                                "🆘 Emergency: " + emergencyEmoji + " " + emergencyType + "\n" +
                                "📍 Location: " + locationAddress + "\n" +
                                (seniorPhone != null && !seniorPhone.isEmpty() ? "📞 Phone: " + seniorPhone + "\n" : "") +
                                "\nTap to open app and respond immediately!"))
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
                .setFullScreenIntent(pendingIntent, true) // Show as heads-up notification
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(9999, notification); // Use fixed ID for emergency SOS notifications
            Log.d(TAG, "✅ Emergency SOS notification shown for: " + seniorName);
        } else {
            Log.e(TAG, "❌ NotificationManager is null, cannot show emergency SOS notification");
        }
    }
    
    /**
     * Shows rescuer response notification for seniors
     * Called when a rescuer accepts an emergency request
     */
    private void showRescuerResponseNotification(String title, String message, String rescuerName,
                                                 String rescuerPhone, String rescuerTeam, String requestId) {
        Log.d(TAG, "🚑 Showing rescuer response notification from: " + rescuerName);
        createSeniorNotificationChannel();
        
        Intent intent = new Intent(this, Senior_Dashboard.class);
        intent.putExtra("notification_id", "fcm_" + System.currentTimeMillis());
        intent.putExtra("rescuer_name", rescuerName);
        intent.putExtra("rescuer_phone", rescuerPhone);
        intent.putExtra("rescuer_team", rescuerTeam);
        intent.putExtra("request_id", requestId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String channelId = "senior_emergency_channel";
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚑 HELP IS ON THE WAY! 🚑\n\n" +
                                "Rescuer: " + rescuerName + "\n" +
                                "Phone: " + rescuerPhone + "\n" +
                                "Team: " + (rescuerTeam != null ? rescuerTeam : "Rescue Team") + "\n" +
                                (requestId != null ? "Request ID: " + requestId : "") +
                                "\n\nStay calm, help is coming!"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFF00FF00, 1000, 1000) // Green light for positive news
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notification);
            Log.d(TAG, "📤 Rescuer response notification sent to senior");
        }
    }
    
    /**
     * Shows emergency alert notification for barangay officials
     * Called when a senior triggers an emergency SOS in the barangay's area
     */
    private void showBarangayEmergencyAlertNotification(String title, String message, String seniorName,
                                                        String seniorPhone, String locationAddress,
                                                        String barangay, String requestId, String emergencyType) {
        // Skip system notification if dashboard is active - popup will show instead
        if (Barangay_Dashboard.isDashboardActive) {
            Log.d(TAG, "📱 Dashboard is ACTIVE - skipping system notification (popup will show)");
            return;
        }
        
        Log.d(TAG, "🚨 Showing barangay emergency alert notification for: " + seniorName);
        createBarangayNotificationChannel();
        
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
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Add call action button
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + seniorPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis() + 1,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String emergencyEmoji = getEmergencyEmoji(emergencyType != null ? emergencyType : "medical");
        
        String channelId = "barangay_emergency_channel";
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentTitle("🚨 EMERGENCY ALERT - " + (barangay != null ? barangay.toUpperCase() : "BARANGAY"))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 EMERGENCY ALERT 🚨\n\n" +
                                "SENIOR: " + (seniorName != null ? seniorName.toUpperCase() : "Unknown") + "\n" +
                                "PHONE: " + (seniorPhone != null ? seniorPhone : "N/A") + "\n" +
                                "LOCATION: " + (locationAddress != null ? locationAddress : "Unknown") + "\n" +
                                "BARANGAY: " + (barangay != null ? barangay.toUpperCase() : "Unknown") + "\n" +
                                "EMERGENCY TYPE: " + emergencyEmoji + " " + (emergencyType != null ? emergencyType : "Unknown") + "\n\n" +
                                "IMMEDIATE ACTION REQUIRED!"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true) // Auto-dismiss when clicked
                .setOngoing(false) // Allow user to swipe away
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 200, 1000, 200, 1000, 200, 1000, 200, 1000})
                .setLights(0xFFFF0000, 500, 500) // Red light, faster blinking
                .setColor(0xFFFF0000) // Red color
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .addAction(R.drawable.baseline_notifications_active_24, "CALL SENIOR", callPendingIntent)
                .addAction(R.drawable.baseline_notifications_active_24, "VIEW DETAILS", pendingIntent)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(8888, notification); // Use fixed ID for barangay emergency
            Log.d(TAG, "🚨 Barangay emergency alert notification sent");
        }
    }
    
    /**
     * Creates notification channel for senior users
     */
    private void createSeniorNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "senior_emergency_channel";
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "Senior Emergency Updates",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Emergency notifications for senior users");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setLightColor(0xFF00FF00);
            channel.setSound(getCustomAlarmSound(), new android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build());
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Senior notification channel created");
            }
        }
    }
    
    /**
     * Creates notification channel for barangay officials
     */
    private void createBarangayNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "barangay_emergency_channel";
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "🚨 EMERGENCY ALERTS",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Critical emergency notifications for barangay officials - Maximum priority alerts");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light
            channel.setBypassDnd(true); // Bypass Do Not Disturb
            channel.setSound(getCustomAlarmSound(), new android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build());
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Barangay notification channel created");
            }
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
