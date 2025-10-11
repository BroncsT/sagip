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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class BarangayNotificationService {
    private static final String TAG = "BarangayNotificationService";
    private static final String CHANNEL_ID = "barangay_emergency_channel";
    private static final int NOTIFICATION_ID = 2000;
    
    private static BarangayNotificationService instance;
    private Context context;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration notificationListener;
    private String currentUserId;
    private long sessionStartTime; // Track when the current session started
    
    private BarangayNotificationService(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.mAuth = FirebaseAuth.getInstance();
        createNotificationChannel();
    }
    
    public static synchronized BarangayNotificationService getInstance(Context context) {
        if (instance == null) {
            instance = new BarangayNotificationService(context);
        } else {
            // Update context and auth references when switching users
            instance.context = context.getApplicationContext();
            instance.mAuth = FirebaseAuth.getInstance();
            instance.db = FirebaseFirestore.getInstance();
        }
        return instance;
    }
    
    public void startListening() {
        if (mAuth.getCurrentUser() == null) {
            Log.w(TAG, "No authenticated user, cannot start listening");
            return;
        }
        
        currentUserId = mAuth.getCurrentUser().getUid();
        sessionStartTime = System.currentTimeMillis(); // Set session start time
        Log.d(TAG, "🔔 Starting barangay notification listener for user: " + currentUserId);
        Log.d(TAG, "🔔 Session started at: " + sessionStartTime);
        
        String notificationPath = "Sagip/users/barangay/" + currentUserId + "/notifications";
        Log.d(TAG, "🔔 Listening to notification path: " + notificationPath);
        Log.d(TAG, "🔔 User type should be: barangay");
        
        // Check if we already have a listener running
        if (notificationListener != null) {
            Log.w(TAG, "🔔 Notification listener already running, stopping previous one");
            notificationListener.remove();
        }
        
        // Only listen for notifications created after the current session started
        // This prevents showing old notifications when user logs in
        Query query = db.collection(notificationPath)
                .whereEqualTo("isRead", false)
                .whereGreaterThan("timestamp", sessionStartTime)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10); // Limit to last 10 recent notifications
        
        Log.d(TAG, "🔔 Setting up Firestore listener for path: " + notificationPath);
        Log.d(TAG, "🔔 Query: whereEqualTo('isRead', false) orderBy('timestamp', DESCENDING)");
        
        notificationListener = query.addSnapshotListener((querySnapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "❌ Error listening to notifications: " + e.getMessage());
                if (e instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
                    com.google.firebase.firestore.FirebaseFirestoreException firestoreException = (com.google.firebase.firestore.FirebaseFirestoreException) e;
                    Log.e(TAG, "❌ Error code: " + firestoreException.getCode());
                }
                return;
            }
            
            if (querySnapshot != null) {
                Log.d(TAG, "📱 Notification listener triggered - documents: " + querySnapshot.size());
                Log.d(TAG, "📱 Query path: " + notificationPath);
                Log.d(TAG, "📱 Query metadata: fromCache=" + querySnapshot.getMetadata().isFromCache() + ", hasPendingWrites=" + querySnapshot.getMetadata().hasPendingWrites());
                processNotifications(querySnapshot);
            } else {
                Log.w(TAG, "📱 QuerySnapshot is null");
            }
        });
        
        Log.d(TAG, "🔔 Firestore listener setup completed");
    }
    
    public void stopListening() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
            Log.d(TAG, "🛑 Stopped barangay notification listener");
        }
    }
    
    /**
     * Reset the session start time - call this when user logs out
     */
    public void resetSession() {
        sessionStartTime = 0;
        currentUserId = null;
        Log.d(TAG, "🔄 Session reset - next login will start fresh");
    }
    
    /**
     * Reset the service when switching users to prevent cross-user notifications
     */
    public static void resetInstance() {
        if (instance != null) {
            instance.stopListening();
            instance.resetSession();
            instance = null;
            Log.d(TAG, "🔄 BarangayNotificationService instance reset for user switch");
        }
    }
    
    // Method to clear old notifications (older than 7 days) when user logs in
    public void clearOldNotifications() {
        if (mAuth.getCurrentUser() == null) {
            Log.w(TAG, "No authenticated user, cannot clear old notifications");
            return;
        }
        
        String notificationPath = "Sagip/users/barangay/" + mAuth.getCurrentUser().getUid() + "/notifications";
        long oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000); // 7 days ago
        
        Log.d(TAG, "🧹 Clearing old notifications older than 7 days");
        
        db.collection(notificationPath)
                .whereLessThan("timestamp", oneWeekAgo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "🧹 Found " + querySnapshot.size() + " old notifications to clear");
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        document.getReference().delete()
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "🧹 Deleted old notification: " + document.getId()))
                                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to delete old notification: " + document.getId(), e));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to query old notifications: " + e.getMessage());
                });
    }
    
    // Method to manually check for existing notifications
    public void checkExistingNotifications() {
        if (mAuth.getCurrentUser() == null) {
            Log.w(TAG, "No authenticated user, cannot check notifications");
            return;
        }
        
        String notificationPath = "Sagip/users/barangay/" + mAuth.getCurrentUser().getUid() + "/notifications";
        Log.d(TAG, "🔍 Manually checking for notifications at: " + notificationPath);
        
        // Only check for notifications created after the current session started
        // This prevents showing old notifications when user logs in
        if (sessionStartTime == 0) {
            sessionStartTime = System.currentTimeMillis();
            Log.d(TAG, "🔍 Session start time not set, using current time: " + sessionStartTime);
        }
        
        db.collection(notificationPath)
                .whereEqualTo("isRead", false)
                .whereGreaterThan("timestamp", sessionStartTime)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5) // Only show last 5 recent notifications
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "🔍 Recent notifications query found " + querySnapshot.size() + " unread notifications from current session");
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        Log.d(TAG, "🔍 Recent notification: " + document.getId() + " - " + document.getString("title"));
                    }
                    if (querySnapshot.size() > 0) {
                        processNotifications(querySnapshot);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Simple query failed: " + e.getMessage());
                    if (e instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
                        com.google.firebase.firestore.FirebaseFirestoreException firestoreException = (com.google.firebase.firestore.FirebaseFirestoreException) e;
                        Log.e(TAG, "❌ Error code: " + firestoreException.getCode());
                    }
                    
                    // If simple query fails, try even simpler query
                    db.collection(notificationPath)
                            .limit(5)
                            .get()
                            .addOnSuccessListener(simpleSnapshot -> {
                                Log.d(TAG, "🔍 Basic collection query found " + simpleSnapshot.size() + " documents");
                                for (QueryDocumentSnapshot document : simpleSnapshot) {
                                    Log.d(TAG, "🔍 Document: " + document.getId() + " - " + document.getData());
                                }
                            })
                            .addOnFailureListener(simpleError -> {
                                Log.e(TAG, "❌ Even basic query failed: " + simpleError.getMessage());
                            });
                });
    }
    
    private void processNotifications(com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        int unreadCount = 0;
        
        for (QueryDocumentSnapshot document : querySnapshot) {
            try {
                Map<String, Object> data = document.getData();
                String type = (String) data.get("type");
                String title = (String) data.get("title");
                String message = (String) data.get("message");
                Boolean isRead = (Boolean) data.get("isRead");
                
                Log.d(TAG, "📱 Processing notification document: " + document.getId());
                Log.d(TAG, "📱 Handling notification - Type: " + type + ", Title: " + title + ", isRead: " + isRead);
                
                if (isRead == null || !isRead) {
                    unreadCount++;
                    Log.d(TAG, "📱 Processing unread notification: " + type);
                    
                    if ("EMERGENCY_ALERT".equals(type)) {
                        handleEmergencyAlert(document.getId(), data);
                        // Mark notification as read after processing
                        markNotificationAsRead(document.getId());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing notification document: " + document.getId(), e);
            }
        }
        
        Log.d(TAG, "📱 Processed " + unreadCount + " unread notifications out of " + querySnapshot.size() + " total");
    }
    
    private void handleEmergencyAlert(String notificationId, Map<String, Object> data) {
        String seniorName = (String) data.get("seniorName");
        String seniorPhone = (String) data.get("seniorPhone");
        String locationAddress = (String) data.get("locationAddress");
        String barangay = (String) data.get("barangay");
        String requestId = (String) data.get("requestId");
        String emergencyType = (String) data.get("emergencyType");
        
        // Get senior coordinates for navigation
        Double seniorLatitude = null;
        Double seniorLongitude = null;
        if (data.get("seniorLatitude") instanceof Number) {
            seniorLatitude = ((Number) data.get("seniorLatitude")).doubleValue();
        }
        if (data.get("seniorLongitude") instanceof Number) {
            seniorLongitude = ((Number) data.get("seniorLongitude")).doubleValue();
        }
        
        Log.d(TAG, "🚨 Handling emergency alert for senior: " + seniorName);
        Log.d(TAG, "📱 Emergency details - Senior: " + seniorName + ", Phone: " + seniorPhone + ", Location: " + locationAddress);
        Log.d(TAG, "📱 Emergency details - Barangay: " + barangay + ", Type: " + emergencyType + ", Request ID: " + requestId);
        Log.d(TAG, "📱 Senior coordinates - Lat: " + seniorLatitude + ", Long: " + seniorLongitude);
        
        // Check if this notification has already been processed recently
        if (hasNotificationBeenProcessedRecently(notificationId)) {
            Log.d(TAG, "⚠️ Notification already processed recently, skipping: " + notificationId);
            return;
        }
        
        // Get currentLocation field
        String currentLocation = (String) data.get("currentLocation");
        
        // Show notification
        showEmergencyAlertNotification(notificationId, seniorName, seniorPhone, locationAddress, barangay, requestId, emergencyType, seniorLatitude, seniorLongitude, currentLocation);
    }
    
    private void showEmergencyAlertNotification(String notificationId, String seniorName, String seniorPhone, 
                                             String locationAddress, String barangay, String requestId, String emergencyType, 
                                             Double seniorLatitude, Double seniorLongitude, String currentLocation) {
        Log.d(TAG, "🚨 Showing EMERGENCY ALERT notification for: " + seniorName);
        
        // Create intent to open Barangay_Dashboard
        Intent intent = new Intent(context, Barangay_Dashboard.class);
        intent.putExtra("notification_id", notificationId);
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("senior_phone", seniorPhone);
        intent.putExtra("location_address", locationAddress);
        intent.putExtra("barangay", barangay);
        intent.putExtra("request_id", requestId);
        intent.putExtra("emergency_type", emergencyType);
        // Add senior coordinates for navigation
        if (seniorLatitude != null && seniorLongitude != null) {
            intent.putExtra("senior_latitude", seniorLatitude);
            intent.putExtra("senior_longitude", seniorLongitude);
            Log.d(TAG, "🚨 Added senior coordinates to intent: " + seniorLatitude + ", " + seniorLongitude);
        }
        
        // Add currentLocation field
        if (currentLocation != null && !currentLocation.isEmpty()) {
            intent.putExtra("current_location", currentLocation);
            Log.d(TAG, "🚨 Added currentLocation to intent: " + currentLocation);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                NOTIFICATION_ID, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create EMERGENCY ALERT notification with maximum priority
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_emergency)
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_emergency))
                .setContentTitle(context.getString(R.string.barangay_emergency_alert_title))
                .setContentText(context.getString(R.string.barangay_emergency_alert_content, barangay))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.barangay_emergency_alert_title) + "\n\n" +
                                "SENIOR: " + seniorName.toUpperCase() + "\n" +
                                "PHONE: " + seniorPhone + "\n" +
                                context.getString(R.string.barangay_emergency_alert_location, locationAddress) + "\n" +
                                "BARANGAY: " + barangay.toUpperCase() + "\n\n" +
                                context.getString(R.string.barangay_emergency_alert_priority)))
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
                context, 
                NOTIFICATION_ID + 1, 
                callIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create navigation intent for Google Maps (use currentLocation if available, otherwise locationAddress)
        String addressForNavigation = (currentLocation != null && !currentLocation.isEmpty()) ? currentLocation : locationAddress;
        Intent navIntent = createNavigationIntent(addressForNavigation, seniorLatitude, seniorLongitude);
        PendingIntent navPendingIntent = PendingIntent.getActivity(
                context, 
                NOTIFICATION_ID + 2, 
                navIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        builder.addAction(R.drawable.ic_emergency, context.getString(R.string.barangay_emergency_action_call), callPendingIntent);
        builder.addAction(R.drawable.ic_emergency, context.getString(R.string.barangay_emergency_action_view), pendingIntent);
        builder.addAction(android.R.drawable.ic_menu_directions, context.getString(R.string.barangay_emergency_action_navigate), navPendingIntent);
        
        // Show notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "🚨 EMERGENCY ALERT notification sent to barangay user");
            
            // Also show as heads-up notification
            showHeadsUpNotification(seniorName, barangay, seniorPhone, seniorLatitude, seniorLongitude, currentLocation);
        }
    }
    
    private void markNotificationAsRead(String notificationId) {
        if (currentUserId == null) {
            Log.w(TAG, "No current user ID, cannot mark notification as read");
            return;
        }
        
        String notificationPath = "Sagip/users/barangay/" + currentUserId + "/notifications/" + notificationId;
        Map<String, Object> updates = new HashMap<>();
        updates.put("isRead", true);
        updates.put("readTimestamp", System.currentTimeMillis());
        
        db.document(notificationPath)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Notification marked as read: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error marking notification as read: " + notificationId, e);
                });
    }
    
    /**
     * Check if a notification has been processed recently (within last 5 minutes)
     */
    private boolean hasNotificationBeenProcessedRecently(String notificationId) {
        // For now, we'll rely on the database isRead flag
        // In the future, we could implement a local cache of recently processed notifications
        return false; // Let the database isRead flag handle this
    }
    
    private Uri getNotificationSound() {
        // Use custom alarm sound if available, otherwise use default
        try {
            return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.emergency_alarm);
        } catch (Exception e) {
            Log.w(TAG, "Custom alarm sound not found, using default", e);
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }
    
    private Uri getEmergencyAlertSound() {
        // Use the most urgent alarm sound for emergency alerts
        try {
            return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.emergency_alarm);
        } catch (Exception e) {
            Log.w(TAG, "Emergency alarm sound not found, using system alarm", e);
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }
    
    private long[] getEmergencyVibrationPattern() {
        // More intense vibration pattern for emergency alerts
        return new long[]{0, 1000, 200, 1000, 200, 1000, 200, 1000, 200, 1000};
    }
    
    private void showHeadsUpNotification(String seniorName, String barangay, String seniorPhone, Double seniorLatitude, Double seniorLongitude, String currentLocation) {
        Log.d(TAG, "🚨 Showing heads-up emergency alert for: " + seniorName);
        
        // Create a separate heads-up notification
        Intent intent = new Intent(context, Barangay_Dashboard.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                NOTIFICATION_ID + 2, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create navigation intent for heads-up notification (use coordinates and currentLocation if available)
        String locationForNavigation = (currentLocation != null && !currentLocation.isEmpty()) ? currentLocation : "Emergency Location - " + barangay;
        Intent navIntent = createNavigationIntent(locationForNavigation, seniorLatitude, seniorLongitude);
        PendingIntent navPendingIntent = PendingIntent.getActivity(
                context, 
                NOTIFICATION_ID + 3, 
                navIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder headsUpBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_emergency)
                .setContentTitle(context.getString(R.string.barangay_emergency_heads_up_title, barangay))
                .setContentText(context.getString(R.string.barangay_emergency_heads_up_content))
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
                .setFullScreenIntent(pendingIntent, true)
                .addAction(android.R.drawable.ic_menu_directions, context.getString(R.string.barangay_emergency_action_navigate), navPendingIntent);
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID + 2, headsUpBuilder.build());
            Log.d(TAG, "🚨 Heads-up emergency alert sent with navigation button");
        }
    }
    
    
    private Intent createNavigationIntent(String locationAddress, Double latitude, Double longitude) {
        Log.d(TAG, "🗺️ Creating navigation intent for location: " + locationAddress);
        if (latitude != null && longitude != null) {
            Log.d(TAG, "🗺️ Using coordinates for navigation: " + latitude + ", " + longitude);
        }
        
        try {
            Intent navIntent;
            
            // Use coordinates if available for more accurate navigation
            if (latitude != null && longitude != null) {
                // First try to open Google Maps app with coordinates
                String navigationUri = String.format("google.navigation:q=%.6f,%.6f&mode=d", latitude, longitude);
                navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
                navIntent.setPackage("com.google.android.apps.maps");
                
                // Check if Google Maps app is available
                if (navIntent.resolveActivity(context.getPackageManager()) != null) {
                    Log.d(TAG, "🗺️ Google Maps app available, using coordinates navigation");
                    return navIntent;
                } else {
                    // Fallback to web-based Google Maps navigation with coordinates
                    Log.d(TAG, "🗺️ Google Maps app not available, using web navigation with coordinates");
                    String webUrl = String.format("https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving", latitude, longitude);
                    return new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                }
            } else {
                // Fallback to address-based navigation
                Log.d(TAG, "🗺️ No coordinates available, using address-based navigation");
                String navigationUri = "google.navigation:q=" + Uri.encode(locationAddress) + "&mode=d";
                navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
                navIntent.setPackage("com.google.android.apps.maps");
                
                // Check if Google Maps app is available
                if (navIntent.resolveActivity(context.getPackageManager()) != null) {
                    Log.d(TAG, "🗺️ Google Maps app available, using address navigation");
                    return navIntent;
                } else {
                    // Fallback to web-based Google Maps navigation
                    Log.d(TAG, "🗺️ Google Maps app not available, using web navigation with address");
                    String webUrl = "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(locationAddress) + "&travelmode=driving";
                    return new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating navigation intent: " + e.getMessage());
            // Final fallback to web navigation
            String webUrl = "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(locationAddress) + "&travelmode=driving";
            return new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.barangay_emergency_channel_name);
            String description = context.getString(R.string.barangay_emergency_channel_description);
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

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "🚨 EMERGENCY ALERT channel created with maximum priority");
            }
        }
    }
}
