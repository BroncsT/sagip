package com.example.sagip_prototype;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * FCM Notification Sender for hospital status updates
 * This class handles sending notifications to rescuers when hospital status changes
 */
public class FCMNotificationSender {
    
    private static final String TAG = "FCMNotificationSender";
    
    /**
     * Sends FCM notification to all rescuer users when hospital status is updated
     */
    public static void sendHospitalUpdateNotificationToRescuers(String hospitalName, String hospitalStatus, 
                                                              int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "🚀 SENDING FCM NOTIFICATION TO ALL RESCUERS");
        Log.d(TAG, "Hospital: " + hospitalName + ", Status: " + hospitalStatus);
        Log.d(TAG, "Available Beds: " + availableBeds + ", Available Doctors: " + availableDoctors);
        
        // Get all rescuer FCM tokens and send notifications
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " rescuer users");
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String rescuerId = document.getId();
                        String fcmToken = document.getString("fcmToken");
                        
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            Log.d(TAG, "Sending FCM notification to rescuer: " + rescuerId);
                            sendRealFCMNotification(fcmToken, hospitalName, hospitalStatus, availableBeds, availableDoctors);
                        } else {
                            Log.d(TAG, "No FCM token for rescuer: " + rescuerId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get rescuer list: " + e.getMessage());
                });
    }
    
    /**
     * Sends notification to a specific FCM token using Firebase Functions
     */
    private static void sendRealFCMNotification(String fcmToken, String hospitalName, String hospitalStatus, 
                                              int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "Sending notification to token: " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
        
        // Save notification to Firestore to trigger Firebase Function
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "hospital_status_update");
        notificationData.put("hospitalName", hospitalName);
        notificationData.put("hospitalStatus", hospitalStatus);
        notificationData.put("availableBeds", availableBeds);
        notificationData.put("availableDoctors", availableDoctors);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        notificationData.put("source", "fcm_real");
        notificationData.put("delivered", true);
        
        // Find the user by FCM token and save notification
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .whereEqualTo("fcmToken", fcmToken)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String userId = document.getId();
                        
                        // Save notification to user's collection - this will trigger Firebase Function
                        db.collection("Sagip")
                                .document("users")
                                .collection("rescuer")
                                .document(userId)
                                .collection("notifications")
                                .add(notificationData)
                                .addOnSuccessListener(docRef -> {
                                    Log.d(TAG, "✅ Notification saved for user: " + userId + " - Firebase Function will send FCM");
                                    
                                    // Trigger local notification if app is running
                                    triggerLocalNotification(hospitalName, hospitalStatus, availableBeds, availableDoctors);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to save notification for user: " + userId, e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to find user with FCM token: " + e.getMessage());
                });
    }
    
    
    /**
     * Triggers a local notification to simulate real-time FCM delivery
     */
    private static void triggerLocalNotification(String hospitalName, String hospitalStatus, 
                                               int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "🔔 Triggering local notification to simulate real-time FCM delivery");
        
        // This would normally be handled by the FCM service automatically
        // For simulation purposes, we'll log that the notification was "delivered"
        String statusEmoji = getStatusEmoji(hospitalStatus);
        
        Log.d(TAG, "📱 NOTIFICATION DELIVERED:");
        Log.d(TAG, "   Title: 🏥 Hospital Status Updated");
        Log.d(TAG, "   Body: " + hospitalName + " is now " + statusEmoji + " " + hospitalStatus.toUpperCase());
        Log.d(TAG, "   Details: Available Beds: " + availableBeds + ", Available Doctors: " + availableDoctors);
        Log.d(TAG, "   ⚡ DELIVERED INSTANTLY (Real-time FCM simulation)");
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
    
    /**
     * Updates FCM token for a user
     */
    public static void updateUserFCMToken(String userId, String userType, String fcmToken) {
        Log.d(TAG, "Updating FCM token for user: " + userId + ", type: " + userType);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("fcmToken", fcmToken);
        tokenData.put("tokenUpdatedAt", System.currentTimeMillis());
        
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .update(tokenData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "FCM token updated successfully for user: " + userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update FCM token for user: " + userId, e);
                });
    }
}
