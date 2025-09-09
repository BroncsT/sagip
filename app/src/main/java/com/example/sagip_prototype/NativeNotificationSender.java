package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Native Android notification sender that works without third-party services
 * Uses Firebase Cloud Messaging (FCM) for background notifications when app is closed
 * Uses local notifications when app is running
 */
public class NativeNotificationSender {
    
    private static final String TAG = "NativeNotificationSender";
    private static final String CHANNEL_ID = "native_hospital_updates";
    private static final String CHANNEL_NAME = "Hospital Status Updates";
    private static final String CHANNEL_DESCRIPTION = "Real-time hospital status update notifications";
    
    /**
     * Sends native notifications to all rescuer users when hospital status is updated
     * This method works both when app is running and when app is closed
     */
    public static void sendHospitalUpdateNotificationToRescuers(String hospitalName, String hospitalStatus, 
                                                              int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "🚀 SENDING NATIVE NOTIFICATION TO ALL RESCUERS");
        Log.d(TAG, "Hospital: " + hospitalName + ", Status: " + hospitalStatus);
        Log.d(TAG, "Available Beds: " + availableBeds + ", Available Doctors: " + availableDoctors);
        
        // Get all rescuer users from database
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " rescuer users to notify");
                    
                    if (querySnapshot.isEmpty()) {
                        Log.w(TAG, "No rescuer users found in database!");
                        return;
                    }
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String rescuerId = document.getId();
                        String rescuerName = document.getString("name");
                        String fcmToken = document.getString("fcmToken");
                        
                        Log.d(TAG, "Processing rescuer: " + rescuerName + " (ID: " + rescuerId + ")");
                        
                        // Send immediate local notification to all rescuers
                        sendImmediateLocalNotificationToRescuer(rescuerId, hospitalName, hospitalStatus, availableBeds, availableDoctors);
                        
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            // Also send FCM notification for background delivery
                            sendFCMNotificationToRescuer(db, rescuerId, fcmToken, hospitalName, hospitalStatus, availableBeds, availableDoctors);
                        } else {
                            Log.w(TAG, "No FCM token found for rescuer: " + rescuerName);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get rescuer users: " + e.getMessage(), e);
                });
    }
    
    /**
     * Sends immediate local notification to a specific rescuer
     * This works instantly when the app is open or in background
     */
    private static void sendImmediateLocalNotificationToRescuer(String rescuerId, String hospitalName, 
                                                              String hospitalStatus, int availableBeds, 
                                                              int availableDoctors) {
        Log.d(TAG, "📱 Sending immediate local notification to rescuer: " + rescuerId);
        
        // Create notification content
        String title = "🏥 Hospital Status Update";
        String message = hospitalName + " is now " + hospitalStatus.toUpperCase() + 
                        " (Beds: " + availableBeds + ", Doctors: " + availableDoctors + ")";
        
        // Store notification data in Firestore for immediate pickup by rescuer services
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "hospital_status_update");
        notificationData.put("title", title);
        notificationData.put("message", message);
        notificationData.put("hospitalName", hospitalName);
        notificationData.put("hospitalStatus", hospitalStatus);
        notificationData.put("availableBeds", availableBeds);
        notificationData.put("availableDoctors", availableDoctors);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        
        // Store in rescuer's notification collection for immediate pickup
        db.collection("Sagip")
            .document("users")
            .collection("rescuer")
            .document(rescuerId)
            .collection("notifications")
            .add(notificationData)
            .addOnSuccessListener(documentReference -> {
                Log.d(TAG, "✅ Immediate notification stored for rescuer: " + rescuerId);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Failed to store immediate notification for rescuer: " + rescuerId, e);
            });
    }
    
    /**
     * Sends FCM notification to a specific rescuer
     * This will work even when the app is closed
     */
    private static void sendFCMNotificationToRescuer(FirebaseFirestore db, String rescuerId, String fcmToken, 
                                                   String hospitalName, String hospitalStatus, 
                                                   int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "Sending FCM notification to rescuer: " + rescuerId);
        
        // Create notification data for FCM
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "hospital_status_update");
        notificationData.put("hospitalName", hospitalName);
        notificationData.put("hospitalStatus", hospitalStatus);
        notificationData.put("availableBeds", availableBeds);
        notificationData.put("availableDoctors", availableDoctors);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        notificationData.put("source", "native_fcm");
        notificationData.put("delivered", true);
        
        // Save notification to user's collection - this will trigger Firebase Function
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "✅ FCM notification saved for rescuer: " + rescuerId + " - Firebase Function will send FCM");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save FCM notification for rescuer: " + rescuerId, e);
                });
    }
    
    /**
     * Sends emergency notification to all users (rescuers, hospitals, barangay)
     * This method works both when app is running and when app is closed
     */
    public static void sendEmergencyNotificationToAllUsers(String seniorName, String emergencyType, 
                                                         String location, String phoneNumber) {
        
        Log.d(TAG, "🚨 SENDING EMERGENCY NOTIFICATION TO ALL USERS");
        Log.d(TAG, "Senior: " + seniorName + ", Emergency: " + emergencyType);
        Log.d(TAG, "Location: " + location + ", Phone: " + phoneNumber);
        
        // Send to all user types
        String[] userTypes = {"rescuer", "hospital", "barangay"};
        
        for (String userType : userTypes) {
            sendEmergencyNotificationToUserType(userType, seniorName, emergencyType, location, phoneNumber);
        }
    }
    
    /**
     * Sends emergency notification to a specific user type
     */
    private static void sendEmergencyNotificationToUserType(String userType, String seniorName, String emergencyType, 
                                                          String location, String phoneNumber) {
        
        Log.d(TAG, "🚨 SENDING EMERGENCY NOTIFICATION TO " + userType.toUpperCase() + " USERS");
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " " + userType + " users to notify for emergency");
                    
                    if (querySnapshot.isEmpty()) {
                        Log.w(TAG, "No " + userType + " users found in database!");
                        return;
                    }
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String userId = document.getId();
                        String userName = document.getString("name");
                        String fcmToken = document.getString("fcmToken");
                        
                        Log.d(TAG, "Processing " + userType + " for emergency: " + userName + " (ID: " + userId + ")");
                        
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            // Send FCM emergency notification
                            sendFCMEmergencyNotificationToUser(db, userType, userId, fcmToken, seniorName, emergencyType, location, phoneNumber);
                        } else {
                            Log.w(TAG, "No FCM token found for " + userType + ": " + userName);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get " + userType + " users for emergency: " + e.getMessage(), e);
                });
    }
    
    /**
     * Sends FCM emergency notification to a specific user
     */
    private static void sendFCMEmergencyNotificationToUser(FirebaseFirestore db, String userType, String userId, String fcmToken,
                                                         String seniorName, String emergencyType, 
                                                         String location, String phoneNumber) {
        
        Log.d(TAG, "Sending FCM emergency notification to " + userType + ": " + userId);
        
        // Create emergency notification data for FCM
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "emergency_help_request");
        notificationData.put("seniorName", seniorName);
        notificationData.put("emergencyType", emergencyType);
        notificationData.put("location", location);
        notificationData.put("phoneNumber", phoneNumber);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        notificationData.put("source", "native_fcm_emergency");
        notificationData.put("delivered", true);
        notificationData.put("priority", "high");
        
        // Save emergency notification to user's collection
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "✅ FCM emergency notification saved for " + userType + ": " + userId + " - Firebase Function will send FCM");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save FCM emergency notification for " + userType + ": " + userId, e);
                });
    }
    
    /**
     * Updates FCM token for a user in the database
     */
    public static void updateUserFCMToken(String userId, String userType, String fcmToken) {
        Log.d(TAG, "Updating FCM token for user: " + userId + ", type: " + userType);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("fcmToken", fcmToken);
        tokenData.put("lastTokenUpdate", System.currentTimeMillis());
        
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .update(tokenData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ FCM token updated successfully for user: " + userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to update FCM token for user: " + userId, e);
                });
    }
    
    /**
     * Creates notification channel for the app
     */
    public static void createNotificationChannel(Context context) {
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
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Notification channel created: " + CHANNEL_ID);
            }
        }
    }
    
    /**
     * Sends a test notification to verify the system is working
     */
    public static void sendTestNotification(String userId, String userType) {
        Log.d(TAG, "🧪 Sending test notification to user: " + userId + ", type: " + userType);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Create test notification data
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "test_notification");
        notificationData.put("title", "Test Notification");
        notificationData.put("message", "This is a test notification to verify the system is working");
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        notificationData.put("source", "test");
        notificationData.put("delivered", true);
        
        // Save test notification to user's collection
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "✅ Test notification saved for user: " + userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save test notification for user: " + userId, e);
                });
    }
    
    /**
     * Shows a local notification (when app is running)
     */
    public static void showLocalNotification(Context context, String title, String message, Intent intent) {
        createNotificationChannel(context);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            (int) System.currentTimeMillis(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setLights(0xFF2196F3, 1000, 1000)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notification);
            Log.d(TAG, "✅ Local notification shown: " + title);
        }
    }
}
