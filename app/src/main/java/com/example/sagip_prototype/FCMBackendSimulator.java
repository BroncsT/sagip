package com.example.sagip_prototype;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Simulates FCM backend functionality for testing purposes
 * In a real implementation, this would be a separate backend service
 */
public class FCMBackendSimulator {
    
    private static final String TAG = "FCMBackendSimulator";
    
    /**
     * Simulates sending FCM notification to all rescuer users
     * In a real implementation, this would use FCM Admin SDK
     */
    public static void sendFCMNotificationToRescuers(String hospitalName, String hospitalStatus, 
                                                    int availableBeds, int availableDoctors) {
        
        Log.d(TAG, "=== SIMULATING FCM NOTIFICATION SEND ===");
        Log.d(TAG, "Hospital: " + hospitalName);
        Log.d(TAG, "Status: " + hospitalStatus);
        Log.d(TAG, "Available Beds: " + availableBeds);
        Log.d(TAG, "Available Doctors: " + availableDoctors);
        
        // Get all rescuer FCM tokens
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
                            Log.d(TAG, "Simulating FCM send to rescuer: " + rescuerId + " with token: " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
                            
                            // Simulate FCM message delivery by creating a notification document
                            // In a real implementation, this would be handled by FCM automatically
                            simulateFCMDelivery(rescuerId, hospitalName, hospitalStatus, availableBeds, availableDoctors);
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
     * Simulates FCM message delivery by creating a notification document
     * In a real implementation, FCM would handle this automatically
     */
    private static void simulateFCMDelivery(String rescuerId, String hospitalName, String hospitalStatus, 
                                          int availableBeds, int availableDoctors) {
        
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "hospital_status_update");
        notificationData.put("hospitalName", hospitalName);
        notificationData.put("hospitalStatus", hospitalStatus);
        notificationData.put("availableBeds", availableBeds);
        notificationData.put("availableDoctors", availableDoctors);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        notificationData.put("source", "fcm_simulated");
        notificationData.put("delivered", true);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "✅ FCM notification simulated and saved for rescuer: " + rescuerId);
                    
                    // Trigger local notification if app is running
                    triggerLocalNotification(hospitalName, hospitalStatus, availableBeds, availableDoctors);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to simulate FCM notification for rescuer: " + rescuerId, e);
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
}
