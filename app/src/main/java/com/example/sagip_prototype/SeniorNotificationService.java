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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class SeniorNotificationService {
    private static final String TAG = "SeniorNotificationService";
    private static final String CHANNEL_ID = "senior_notifications";
    private static SeniorNotificationService instance;
    private Context context;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration notificationListener;
    
    public static synchronized SeniorNotificationService getInstance(Context context) {
        if (instance == null) {
            instance = new SeniorNotificationService(context);
        } else {
            // Update context and auth references when switching users
            instance.context = context;
            instance.mAuth = FirebaseAuth.getInstance();
            instance.db = FirebaseFirestore.getInstance();
        }
        return instance;
    }
    
    private SeniorNotificationService(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
        createNotificationChannel();
    }
    
    public void startListening() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, cannot start listening");
            return;
        }
        
        // Stop existing listener if any
        if (notificationListener != null) {
            Log.d(TAG, "🛑 Stopping existing notification listener before starting new one");
            notificationListener.remove();
            notificationListener = null;
        }
        
        String userId = currentUser.getUid();
        Log.d(TAG, "🔔 Starting senior notification listener for user: " + userId);
        
        // Listen to senior's notifications
        String notificationPath = "Sagip/users/seniors/" + userId + "/notifications";
        Log.d(TAG, "🔔 Listening to notification path: " + notificationPath);
        
        notificationListener = db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("isRead", false)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to notifications: " + error.getMessage());
                        return;
                    }
                    
                    Log.d(TAG, "📱 Notification listener triggered - documents: " + (querySnapshot != null ? querySnapshot.size() : 0));
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        Log.d(TAG, "📱 Processing " + querySnapshot.size() + " notification documents");
                    }
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        Log.d(TAG, "📱 Processing " + querySnapshot.size() + " unread notification documents");
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            Log.d(TAG, "📱 Processing unread notification document: " + document.getId());
                            handleNotification(document);
                        }
                        Log.d(TAG, "📱 Processed " + querySnapshot.size() + " unread notifications");
                    } else {
                        Log.d(TAG, "📱 No notifications found");
                    }
                });
    }
    
    public void stopListening() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
            Log.d(TAG, "🛑 Stopped senior notification listener");
        }
    }
    
    /**
     * Reset the service when switching users to prevent cross-user notifications
     */
    public static void resetInstance() {
        if (instance != null) {
            instance.stopListening();
            instance = null;
            Log.d(TAG, "🔄 SeniorNotificationService instance reset for user switch");
        }
    }
    
    private void handleNotification(QueryDocumentSnapshot document) {
        try {
            String type = document.getString("type");
            String title = document.getString("title");
            String message = document.getString("message");
            String rescuerName = document.getString("rescuerName");
            String rescuerPhone = document.getString("rescuerPhone");
            String requestId = document.getString("requestId");
            Long timestamp = document.getLong("timestamp");
            Boolean isRead = document.getBoolean("isRead");
            
            Log.d(TAG, "📱 Handling notification - Type: " + type + ", Title: " + title + ", isRead: " + isRead);
            Log.d(TAG, "📱 Notification data - Rescuer: " + rescuerName + ", Phone: " + rescuerPhone + ", RequestId: " + requestId);
            Log.d(TAG, "📱 Full document data: " + document.getData().toString());
            
            // Process notification (already filtered for unread in query)
            Log.d(TAG, "📱 Processing unread notification: " + type);
            
            Log.d(TAG, "📱 Processing notification - Type: " + type + ", Rescuer: " + rescuerName + ", Request ID: " + requestId);
            if ("RESCUER_RESPONSE".equals(type)) {
                Log.d(TAG, "📱 ✅ RESCUER_RESPONSE type matched! Showing rescuer response notification for: " + rescuerName);
                showRescuerResponseNotificationWithDocument(title, message, rescuerName, rescuerPhone, requestId, document);
            } else {
                Log.d(TAG, "📱 ❌ Unknown notification type: " + type + " (expected: RESCUER_RESPONSE)");
            }
            
            // Mark notification as read
            document.getReference().update("isRead", true)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "📱 Notification marked as read"))
                    .addOnFailureListener(e -> Log.e(TAG, "📱 Failed to mark notification as read", e));
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification: " + e.getMessage(), e);
        }
    }
    
    private void showRescuerResponseNotification(String title, String message, String rescuerName, 
                                               String rescuerPhone, String requestId, Long timestamp) {
        Log.d(TAG, "🚨 Showing rescuer response notification for: " + rescuerName);
        
        // First try to get emergency data from local EmergencyQueueManager
        EmergencyQueueManager.EmergencyRequest emergency = EmergencyQueueManager.getInstance(context).getEmergencyById(requestId);
        
        if (emergency != null) {
            // Emergency found in local queue, show notification with full data
            // Note: We need the document for hospital info, but we don't have it here
            // For now, we'll use the basic notification without hospital info
            showBasicRescuerResponseNotification(title, message, rescuerName, rescuerPhone, requestId);
        } else {
            // Emergency not found in local queue, load from database
            Log.d(TAG, "⚠️ Emergency not found in local queue, loading from database...");
            loadEmergencyAndShowNotification(title, message, rescuerName, rescuerPhone, requestId);
        }
    }
    
    private void showRescuerResponseNotificationWithDocument(String title, String message, String rescuerName, 
                                                           String rescuerPhone, String requestId, 
                                                           QueryDocumentSnapshot document) {
        Log.d(TAG, "📱 ===== showRescuerResponseNotificationWithDocument CALLED =====");
        Log.d(TAG, "📱 Showing rescuer response notification with document data");
        Log.d(TAG, "📱 Rescuer: " + rescuerName + ", Phone: " + rescuerPhone + ", Request ID: " + requestId);
        Log.d(TAG, "📱 Title: " + title + ", Message: " + message);
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Check if notifications are enabled
        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean notificationsEnabled = notificationManager.areNotificationsEnabled();
            Log.d(TAG, "📱 Notifications enabled: " + notificationsEnabled);
            if (!notificationsEnabled) {
                Log.w(TAG, "⚠️ Notifications are disabled, cannot show notification");
                return;
            }
        }
        
        // Show popup immediately instead of creating notification
        showRescuerAcceptedPopupImmediately(rescuerName, rescuerPhone, requestId, document);
        
        // Also create notification for background cases
        Intent intent = new Intent(context, Senior_Dashboard.class);
        intent.putExtra("notification_type", "rescuer_response");
        intent.putExtra("rescuer_name", rescuerName);
        intent.putExtra("rescuer_phone", rescuerPhone);
        intent.putExtra("request_id", requestId);
        
        // Get emergency status and assigned rescuer ID from document
        String emergencyStatus = document.getString("emergency_status");
        String assignedRescuerId = document.getString("assigned_rescuer_id");
        intent.putExtra("emergency_status", emergencyStatus);
        intent.putExtra("assigned_rescuer_id", assignedRescuerId);
        
        // Extract rescue group from message
        String rescueGroup = extractRescueGroupFromMessage(message, rescuerName);
        intent.putExtra("rescuer_team", rescueGroup);
        
        // Add hospital information from notification document
        String hospitalId = document.getString("hospitalId");
        String hospitalName = document.getString("hospitalName");
        String hospitalAddress = document.getString("hospitalAddress");
        String hospitalPhone = document.getString("hospitalPhone");
        
        if (hospitalId != null) {
            intent.putExtra("hospital_id", hospitalId);
        }
        if (hospitalName != null) {
            intent.putExtra("hospital_name", hospitalName);
        }
        if (hospitalAddress != null) {
            intent.putExtra("hospital_address", hospitalAddress);
        }
        if (hospitalPhone != null) {
            intent.putExtra("hospital_phone", hospitalPhone);
        }
        
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                (requestId != null) ? requestId.hashCode() : (int) System.currentTimeMillis(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create call intent
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(android.net.Uri.parse("tel:" + rescuerPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                context,
                (requestId != null) ? (requestId + "_call").hashCode() : (int) (System.currentTimeMillis() + 1),
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFF00FF00, 1000, 1000) // Green light
                .addAction(android.R.drawable.ic_menu_call, "📞 CALL RESCUER", callPendingIntent)
                .setOngoing(false)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis());
        
        int notificationId = (requestId != null) ? requestId.hashCode() : (int) System.currentTimeMillis();
        
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "📤 Rescuer response notification with hospital info sent to senior - ID: " + notificationId);
            Log.d(TAG, "📤 Notification title: " + title + ", message: " + message);
            Log.d(TAG, "📤 NotificationManager is null: " + (notificationManager == null));
            Log.d(TAG, "📤 Notification channel ID: " + CHANNEL_ID);
            Log.d(TAG, "📤 Notification sent successfully!");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending rescuer response notification: " + e.getMessage(), e);
        }
    }
    
    private void loadEmergencyAndShowNotification(String title, String message, String rescuerName, 
                                                String rescuerPhone, String requestId) {
        EmergencyQueueManager.getInstance(context).loadEmergencyByRequestIdFromDatabase(requestId, 
            new EmergencyQueueManager.EmergencyLoadCallback() {
                @Override
                public void onEmergencyLoaded(EmergencyQueueManager.EmergencyRequest emergency) {
                    if (emergency != null) {
                        // Emergency loaded from database, show basic notification (no hospital info available)
                        Log.d(TAG, "📱 Emergency loaded from database, showing basic notification");
                        showBasicRescuerResponseNotification(title, message, rescuerName, rescuerPhone, requestId);
                    } else {
                        // Emergency not found in database, show basic notification
                        Log.w(TAG, "⚠️ Emergency not found in database, showing basic notification");
                        showBasicRescuerResponseNotification(title, message, rescuerName, rescuerPhone, requestId);
                    }
                }
            });
    }
    
    private void showRescuerResponseNotificationWithData(String title, String message, String rescuerName, 
                                                       String rescuerPhone, String requestId, 
                                                       EmergencyQueueManager.EmergencyRequest emergency, 
                                                       QueryDocumentSnapshot document) {
        Log.d(TAG, "📱 Showing rescuer response notification with emergency data");
        Log.d(TAG, "📱 Rescuer: " + rescuerName + ", Phone: " + rescuerPhone + ", Request ID: " + requestId);
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent to open senior dashboard
        Intent intent = new Intent(context, Senior_Dashboard.class);
        intent.putExtra("notification_type", "rescuer_response");
        intent.putExtra("rescuer_name", rescuerName);
        intent.putExtra("rescuer_phone", rescuerPhone);
        intent.putExtra("request_id", requestId);
        intent.putExtra("emergency_status", emergency.status);
        intent.putExtra("assigned_rescuer_id", emergency.assignedRescuerId);
        
        // Extract rescue group from message
        String rescueGroup = extractRescueGroupFromMessage(message, rescuerName);
        intent.putExtra("rescuer_team", rescueGroup);
        
        // Add hospital information from notification document
        String hospitalId = document.getString("hospitalId");
        String hospitalName = document.getString("hospitalName");
        String hospitalAddress = document.getString("hospitalAddress");
        String hospitalPhone = document.getString("hospitalPhone");
        
        if (hospitalId != null) {
            intent.putExtra("hospital_id", hospitalId);
        }
        if (hospitalName != null) {
            intent.putExtra("hospital_name", hospitalName);
        }
        if (hospitalAddress != null) {
            intent.putExtra("hospital_address", hospitalAddress);
        }
        if (hospitalPhone != null) {
            intent.putExtra("hospital_phone", hospitalPhone);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                (requestId != null) ? requestId.hashCode() : (int) System.currentTimeMillis(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create call intent
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(android.net.Uri.parse("tel:" + rescuerPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                context,
                (requestId != null) ? (requestId + "_call").hashCode() : (int) (System.currentTimeMillis() + 1),
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create enhanced notification with emergency data
        String statusText = "assigned".equals(emergency.status) ? "✅ Assigned" : "⏳ Pending";
        String priorityText = getPriorityText(emergency.priority);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 Help is on the way!\n\n" +
                                "👤 Rescuer: " + rescuerName + "\n" +
                                "📞 Phone: " + rescuerPhone + "\n" +
                                "🆔 Request ID: " + requestId + "\n" +
                                "📍 Location: " + emergency.locationAddress + "\n" +
                                "📊 Status: " + statusText + "\n" +
                                "⚡ Priority: " + priorityText + "\n\n" +
                                "Your rescuer is responding to your emergency!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFF00FF00, 1000, 1000) // Green light
                .addAction(android.R.drawable.ic_menu_call, "📞 CALL RESCUER", callPendingIntent)
                .setOngoing(false);
        
        notificationManager.notify((requestId != null) ? requestId.hashCode() : (int) System.currentTimeMillis(), builder.build());
        Log.d(TAG, "📤 Enhanced rescuer response notification sent to senior");
    }
    
    private void showBasicRescuerResponseNotification(String title, String message, String rescuerName, 
                                                    String rescuerPhone, String requestId) {
        Log.d(TAG, "📱 Showing basic rescuer response notification");
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Check if notifications are enabled
        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            boolean notificationsEnabled = notificationManager.areNotificationsEnabled();
            Log.d(TAG, "📱 Notifications enabled: " + notificationsEnabled);
            if (!notificationsEnabled) {
                Log.w(TAG, "⚠️ Notifications are disabled, cannot show notification");
                return;
            }
        }
        
        // Create intent to open senior dashboard
        Intent intent = new Intent(context, Senior_Dashboard.class);
        intent.putExtra("notification_type", "rescuer_response");
        intent.putExtra("rescuer_name", rescuerName);
        intent.putExtra("rescuer_phone", rescuerPhone);
        intent.putExtra("request_id", requestId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                (requestId != null) ? requestId.hashCode() : (int) System.currentTimeMillis(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create call intent
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(android.net.Uri.parse("tel:" + rescuerPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                context,
                (requestId != null) ? (requestId + "_call").hashCode() : (int) (System.currentTimeMillis() + 1),
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create basic notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🚨 Help is on the way!\n\n" +
                                "👤 Rescuer: " + rescuerName + "\n" +
                                "📞 Phone: " + rescuerPhone + "\n" +
                                "🆔 Request ID: " + requestId + "\n\n" +
                                "Your rescuer is responding to your emergency!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFF00FF00, 1000, 1000) // Green light
                .addAction(android.R.drawable.ic_menu_call, "📞 CALL RESCUER", callPendingIntent)
                .setOngoing(false)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis());
        
        int notificationId = (requestId != null) ? requestId.hashCode() : (int) System.currentTimeMillis();
        
        try {
            notificationManager.notify(notificationId, builder.build());
            Log.d(TAG, "📤 Basic rescuer response notification sent to senior - ID: " + notificationId);
            Log.d(TAG, "📤 Notification title: " + title + ", message: " + message);
            Log.d(TAG, "📤 NotificationManager is null: " + (notificationManager == null));
            Log.d(TAG, "📤 Notification channel ID: " + CHANNEL_ID);
            Log.d(TAG, "📤 Basic notification sent successfully!");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending basic rescuer response notification: " + e.getMessage(), e);
        }
    }
    
    private String getPriorityText(int priority) {
        switch (priority) {
            case 1: return "🔴 Critical";
            case 2: return "🟠 High";
            case 3: return "🟡 Medium";
            case 4: return "🟢 Low";
            default: return "⚪ Unknown";
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) {
                Log.e(TAG, "❌ NotificationManager is null, cannot create channel");
                return;
            }
            
            // Check if channel already exists
            NotificationChannel existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (existingChannel != null) {
                Log.d(TAG, "📱 Notification channel already exists: " + CHANNEL_ID);
                return;
            }
            
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Senior Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for seniors about emergency responses");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            
            // Set custom alarm sound
            Uri alarmSound = getCustomAlarmSound();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(alarmSound, audioAttributes);
            
            channel.enableLights(true);
            channel.setLightColor(0xFF00FF00); // Green light
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "📱 Senior notification channel created successfully: " + CHANNEL_ID);
            
            // Verify channel was created
            NotificationChannel createdChannel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (createdChannel != null) {
                Log.d(TAG, "✅ Channel verification successful - ID: " + createdChannel.getId() + ", Importance: " + createdChannel.getImportance());
            } else {
                Log.e(TAG, "❌ Channel verification failed - channel not found after creation");
            }
        } else {
            Log.d(TAG, "📱 Android version < O, no channel creation needed");
        }
    }
    
    private Uri getCustomAlarmSound() {
        try {
            Uri customSound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.emergency_alarm);
            Log.d(TAG, "🔊 Using custom alarm sound: " + customSound.toString());
            return customSound;
        } catch (Exception e) {
            Log.w(TAG, "Custom alarm sound not found, using system sound. Error: " + e.getMessage());
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
    }
    
    private String extractRescueGroupFromMessage(String message, String rescuerName) {
        if (message == null || rescuerName == null) {
            return "Emergency Response Team";
        }
        
        // Message format: "rescuerName from rescueGroup is responding to your emergency"
        // Look for pattern: "rescuerName from " followed by text until " is responding"
        String pattern = rescuerName + " from ";
        int startIndex = message.indexOf(pattern);
        
        if (startIndex != -1) {
            startIndex += pattern.length();
            int endIndex = message.indexOf(" is responding", startIndex);
            
            if (endIndex != -1) {
                String rescueGroup = message.substring(startIndex, endIndex).trim();
                Log.d(TAG, "🏢 Extracted rescue group from message: " + rescueGroup);
                return rescueGroup;
            }
        }
        
        Log.d(TAG, "🏢 Could not extract rescue group from message: " + message);
        return "Emergency Response Team";
    }
    
    // Test method to verify notification system is working
    public void sendTestNotification() {
        Log.d(TAG, "🧪 Sending test notification to verify notification system");
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.e(TAG, "❌ NotificationManager is null");
            return;
        }
        
        // Check if notifications are enabled
        boolean notificationsEnabled = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationsEnabled = notificationManager.areNotificationsEnabled();
            Log.d(TAG, "🧪 Notifications enabled: " + notificationsEnabled);
            if (!notificationsEnabled) {
                Log.w(TAG, "⚠️ Notifications are disabled, cannot send test notification");
                return;
            }
        }
        
        // Check if notification channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                Log.e(TAG, "❌ Notification channel does not exist: " + CHANNEL_ID);
                Log.d(TAG, "🧪 Creating notification channel...");
                createNotificationChannel();
                
                // Check again after creation
                channel = notificationManager.getNotificationChannel(CHANNEL_ID);
                if (channel == null) {
                    Log.e(TAG, "❌ Failed to create notification channel");
                    return;
                }
            } else {
                Log.d(TAG, "✅ Notification channel exists: " + CHANNEL_ID + ", Importance: " + channel.getImportance());
            }
        }
        
        // Create test notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🧪 Test Notification")
                .setContentText("This is a test notification to verify the notification system is working")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis());
        
        int testNotificationId = (int) System.currentTimeMillis();
        
        try {
            notificationManager.notify(testNotificationId, builder.build());
            Log.d(TAG, "🧪 Test notification sent successfully with ID: " + testNotificationId);
            Log.d(TAG, "🧪 NotificationManager is null: " + (notificationManager == null));
            Log.d(TAG, "🧪 Notification channel ID: " + CHANNEL_ID);
            Log.d(TAG, "🧪 Notifications enabled: " + notificationsEnabled);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending test notification: " + e.getMessage(), e);
        }
    }
    
    private void showRescuerAcceptedPopupImmediately(String rescuerName, String rescuerPhone, String requestId, QueryDocumentSnapshot document) {
        Log.d(TAG, "🎉 Showing rescuer accepted popup immediately for: " + rescuerName);
        
        // Get additional data from document
        String rescuerTeam = document.getString("rescuerTeam");
        String emergencyStatus = document.getString("emergency_status");
        String assignedRescuerId = document.getString("assigned_rescuer_id");
        String hospitalId = document.getString("hospitalId");
        String hospitalName = document.getString("hospitalName");
        String hospitalAddress = document.getString("hospitalAddress");
        String hospitalPhone = document.getString("hospitalPhone");
        
        // Create a broadcast intent to show the popup in the Senior_Dashboard
        Intent popupIntent = new Intent("com.example.sagip_prototype.SHOW_RESCUER_ACCEPTED_POPUP");
        popupIntent.putExtra("rescuer_name", rescuerName);
        popupIntent.putExtra("rescuer_phone", rescuerPhone);
        popupIntent.putExtra("rescuer_team", rescuerTeam);
        popupIntent.putExtra("request_id", requestId);
        popupIntent.putExtra("assigned_rescuer_id", assignedRescuerId);
        popupIntent.putExtra("emergency_status", emergencyStatus);
        popupIntent.putExtra("hospital_id", hospitalId);
        popupIntent.putExtra("hospital_name", hospitalName);
        popupIntent.putExtra("hospital_address", hospitalAddress);
        popupIntent.putExtra("hospital_phone", hospitalPhone);
        
        // Send broadcast
        context.sendBroadcast(popupIntent);
        Log.d(TAG, "📡 Broadcast sent to show rescuer accepted popup");
    }
}
