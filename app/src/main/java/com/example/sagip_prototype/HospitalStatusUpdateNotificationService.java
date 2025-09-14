package com.example.sagip_prototype;

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

public class HospitalStatusUpdateNotificationService {
    
    private static final String TAG = "HospitalStatusUpdateNotificationService";
    private static final String CHANNEL_ID = "hospital_status_updates";
    private static final String CHANNEL_NAME = "Hospital Status Updates";
    private static final String CHANNEL_DESCRIPTION = "Notifications when hospitals update their status";
    
    /**
     * Sends notifications to all rescuer users when a hospital updates their status
     * This method saves notification data to each rescuer's database for them to display locally
     */
    public static void notifyRescuersOfHospitalUpdate(Context context, String hospitalName, String hospitalStatus, int availableBeds, int availableDoctors) {
        Log.d(TAG, "=== NOTIFYING RESCUERS OF HOSPITAL UPDATE ===");
        Log.d(TAG, "Hospital: " + hospitalName);
        Log.d(TAG, "Status: " + hospitalStatus);
        Log.d(TAG, "Available Beds: " + availableBeds);
        Log.d(TAG, "Available Doctors: " + availableDoctors);
        
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
                        String rescuerPhone = document.getString("mobileNumber");
                        
                        Log.d(TAG, "Processing rescuer: " + rescuerName + " (ID: " + rescuerId + ", Phone: " + rescuerPhone + ")");
                        
                        // Save notification to database for the rescuer to display locally
                        saveNotificationToDatabase(db, rescuerId, hospitalName, hospitalStatus, availableBeds, availableDoctors);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get rescuer users: " + e.getMessage(), e);
                });
    }
    
    /**
     * Creates notification channel for hospital status updates
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
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Creates and shows notification for hospital status update
     */
    private static void createHospitalUpdateNotification(Context context, String rescuerId, String hospitalName, String hospitalStatus, int availableBeds, int availableDoctors) {
        Log.d(TAG, "=== CREATING NOTIFICATION ===");
        Log.d(TAG, "Rescuer ID: " + rescuerId);
        Log.d(TAG, "Hospital: " + hospitalName);
        Log.d(TAG, "Status: " + hospitalStatus);
        
        // Create intent to open Rescuer Dashboard
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
            rescuerId.hashCode(), // Unique request code for each rescuer
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Get status emoji and color
        String statusEmoji = getStatusEmoji(hospitalStatus);
        String statusColor = getStatusColor(hospitalStatus);
        
        Log.d(TAG, "Status emoji: " + statusEmoji + ", Color: " + statusColor);
        
        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
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
                .setLights(0xFF2196F3, 1000, 1000) // Blue light
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        // Show notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(rescuerId.hashCode(), builder.build());
            Log.d(TAG, "✅ Notification displayed successfully for rescuer: " + rescuerId);
        } else {
            Log.e(TAG, "❌ NotificationManager is null!");
        }
    }
    
    /**
     * Saves notification to database for the rescuer
     */
    private static void saveNotificationToDatabase(FirebaseFirestore db, String rescuerId, String hospitalName, String hospitalStatus, int availableBeds, int availableDoctors) {
        Log.d(TAG, "=== SAVING NOTIFICATION TO DATABASE ===");
        Log.d(TAG, "Rescuer ID: " + rescuerId);
        Log.d(TAG, "Hospital: " + hospitalName);
        Log.d(TAG, "Status: " + hospitalStatus);
        
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "hospital_status_update");
        notificationData.put("hospitalName", hospitalName);
        notificationData.put("hospitalStatus", hospitalStatus);
        notificationData.put("availableBeds", availableBeds);
        notificationData.put("availableDoctors", availableDoctors);
        notificationData.put("timestamp", com.google.firebase.Timestamp.now());
        notificationData.put("read", false);
        
        Log.d(TAG, "Notification data: " + notificationData.toString());
        
        // Save to rescuer's notifications collection
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("notifications")
                .add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✅ Notification saved successfully to database for rescuer: " + rescuerId);
                    Log.d(TAG, "Document ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save notification to database for rescuer: " + rescuerId, e);
                });
    }
    
    /**
     * Checks for new hospital status update notifications for a specific rescuer
     * and displays them locally on their device
     */
    public static void checkAndDisplayNotificationsForRescuer(Context context, String rescuerId) {
        Log.d(TAG, "=== CHECKING FOR NOTIFICATIONS FOR RESCUER ===");
        Log.d(TAG, "Rescuer ID: " + rescuerId);
        
        // Create notification channel
        createNotificationChannel(context);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Get unread notifications for this rescuer (simplified query to avoid index requirement)
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("notifications")
                .whereEqualTo("read", false)
                .whereEqualTo("type", "hospital_status_update")
                .limit(10) // Limit to 10 most recent notifications
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " unread notifications for rescuer: " + rescuerId);
                    
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No unread notifications found for rescuer: " + rescuerId);
                        return;
                    }
                    
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String hospitalName = document.getString("hospitalName");
                        String hospitalStatus = document.getString("hospitalStatus");
                        Long availableBeds = document.getLong("availableBeds");
                        Long availableDoctors = document.getLong("availableDoctors");
                        String notificationId = document.getId();
                        
                        Log.d(TAG, "Processing notification: " + notificationId);
                        Log.d(TAG, "Hospital: " + hospitalName + ", Status: " + hospitalStatus);
                        Log.d(TAG, "Beds: " + availableBeds + ", Doctors: " + availableDoctors);
                        
                        if (hospitalName != null && hospitalStatus != null && availableBeds != null && availableDoctors != null) {
                            Log.d(TAG, "✅ All data valid, creating notification");
                            // Display notification locally
                            createHospitalUpdateNotification(context, rescuerId, hospitalName, hospitalStatus, 
                                    availableBeds.intValue(), availableDoctors.intValue());
                            
                            // Mark as read
                            markNotificationAsRead(db, rescuerId, notificationId);
                        } else {
                            Log.w(TAG, "❌ Invalid notification data - missing fields");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to get notifications for rescuer: " + rescuerId, e);
                    Log.d(TAG, "Trying fallback method - getting all notifications and filtering in app");
                    // Fallback: Get all notifications and filter in the app
                    getNotificationsFallback(context, rescuerId);
                });
    }
    
    /**
     * Fallback method to get all notifications and filter in the app
     */
    private static void getNotificationsFallback(Context context, String rescuerId) {
        Log.d(TAG, "=== USING FALLBACK METHOD ===");
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Get all notifications for this rescuer
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("notifications")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Fallback: Found " + querySnapshot.size() + " total notifications for rescuer: " + rescuerId);
                    
                    int unreadCount = 0;
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Boolean read = document.getBoolean("read");
                        String type = document.getString("type");
                        
                        // Filter for unread hospital status update notifications
                        if ((read == null || !read) && "hospital_status_update".equals(type)) {
                            unreadCount++;
                            
                            String hospitalName = document.getString("hospitalName");
                            String hospitalStatus = document.getString("hospitalStatus");
                            Long availableBeds = document.getLong("availableBeds");
                            Long availableDoctors = document.getLong("availableDoctors");
                            String notificationId = document.getId();
                            
                            Log.d(TAG, "Fallback: Processing notification: " + notificationId);
                            
                            if (hospitalName != null && hospitalStatus != null && availableBeds != null && availableDoctors != null) {
                                Log.d(TAG, "✅ Fallback: All data valid, creating notification");
                                // Display notification locally
                                createHospitalUpdateNotification(context, rescuerId, hospitalName, hospitalStatus, 
                                        availableBeds.intValue(), availableDoctors.intValue());
                                
                                // Mark as read
                                markNotificationAsRead(db, rescuerId, notificationId);
                            }
                        }
                    }
                    
                    Log.d(TAG, "Fallback: Found " + unreadCount + " unread hospital status update notifications");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Fallback method also failed: " + e.getMessage(), e);
                });
    }
    
    /**
     * Marks a notification as read
     */
    private static void markNotificationAsRead(FirebaseFirestore db, String rescuerId, String notificationId) {
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .collection("notifications")
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Notification marked as read: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark notification as read: " + notificationId, e);
                });
    }
    
    /**
     * Gets emoji for hospital status
     */
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
     * Gets color for hospital status
     */
    private static String getStatusColor(String status) {
        switch (status.toLowerCase()) {
            case "available":
                return "#4CAF50"; // Green
            case "busy":
                return "#FF9800"; // Orange
            case "full":
                return "#F44336"; // Red
            default:
                return "#9E9E9E"; // Gray
        }
    }
}
