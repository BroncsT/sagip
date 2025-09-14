package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service specifically for immediate hospital status update notifications
 * Polls Firestore every 1 second for new hospital status updates
 */
public class HospitalStatusNotificationService extends Service {
    private static final String TAG = "HospitalStatusNotificationService";
    private static final String CHANNEL_ID = "hospital_status_notifications";
    private static final int NOTIFICATION_ID = 4001;
    private static final int SERVICE_ID = 4002;
    
    private ScheduledExecutorService executor;
    private boolean isMonitoring = false;
    private String currentUserId;
    private String currentUserType;
    private long lastNotificationCheck = 0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "HospitalStatusNotificationService created");
        
        // Create notification channel
        createNotificationChannel();
        
        // Get user info
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currentUserId = prefs.getString("user_id", null);
        currentUserType = prefs.getString("user_type", null);
        
        executor = Executors.newSingleThreadScheduledExecutor();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "HospitalStatusNotificationService started");
        
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("start_monitoring".equals(action)) {
                startMonitoring();
            } else if ("stop_monitoring".equals(action)) {
                stopMonitoring();
            }
        }
        
        return START_STICKY;
    }
    
    private void startMonitoring() {
        if (isMonitoring || currentUserId == null || !"rescuer".equals(currentUserType)) {
            Log.d(TAG, "Already monitoring, no user ID, or not a rescuer");
            return;
        }
        
        Log.d(TAG, "Starting hospital status notification monitoring for rescuer: " + currentUserId);
        isMonitoring = true;
        
        // Start as foreground service
        startForeground(SERVICE_ID, createServiceNotification());
        
        // Start polling every 1 second for immediate notifications
        executor.scheduleAtFixedRate(this::checkForHospitalStatusUpdates, 0, 1, TimeUnit.SECONDS);
    }
    
    private void stopMonitoring() {
        Log.d(TAG, "Stopping hospital status notification monitoring");
        isMonitoring = false;
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        
        stopForeground(true);
        stopSelf();
    }
    
    private void checkForHospitalStatusUpdates() {
        if (!isMonitoring || currentUserId == null) {
            Log.d(TAG, "Not monitoring or no user ID");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Use the full collection path as used in NativeNotificationSender
        db.collection("Sagip")
            .document("users")
            .collection("rescuer")
            .document(currentUserId)
            .collection("notifications")
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                Log.d(TAG, "Found " + querySnapshot.size() + " unread notifications for rescuer: " + currentUserId);
                
                if (querySnapshot.isEmpty()) {
                    Log.d(TAG, "No unread notifications found for rescuer: " + currentUserId);
                    return;
                }
                
                // Filter for hospital status updates and recent notifications
                for (var doc : querySnapshot.getDocuments()) {
                    String notificationId = doc.getId();
                    var data = doc.getData();
                    String type = (String) data.get("type");
                    Object timestampObj = data.get("timestamp");
                    
                    Log.d(TAG, "Checking notification: " + notificationId + ", type: " + type);
                    
                    // Filter for hospital status updates
                    if ("hospital_status_update".equals(type)) {
                        long timestamp = 0;
                        if (timestampObj instanceof Long) {
                            timestamp = (Long) timestampObj;
                        } else if (timestampObj instanceof Double) {
                            timestamp = ((Double) timestampObj).longValue();
                        }
                        
                        // Only process recent notifications (within last 5 minutes)
                        long currentTime = System.currentTimeMillis();
                        if (timestamp > lastNotificationCheck && (currentTime - timestamp) < 300000) {
                            Log.d(TAG, "📱 Processing recent hospital status notification: " + notificationId);
                            handleHospitalStatusNotification(notificationId, data);
                        } else {
                            Log.d(TAG, "Skipping old notification: " + notificationId + " (age: " + (currentTime - timestamp) + "ms)");
                        }
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking hospital status notifications: " + e.getMessage(), e);
            });
    }
    
    private void handleHospitalStatusNotification(String notificationId, java.util.Map<String, Object> data) {
        String title = (String) data.get("title");
        String message = (String) data.get("message");
        String hospitalName = (String) data.get("hospitalName");
        String hospitalStatus = (String) data.get("hospitalStatus");
        Object availableBedsObj = data.get("availableBeds");
        Object availableDoctorsObj = data.get("availableDoctors");
        Object timestampObj = data.get("timestamp");
        
        Log.d(TAG, "Handling hospital status notification: " + hospitalName + " - " + hospitalStatus);
        
        // Convert objects to proper types
        int availableBeds = 0;
        int availableDoctors = 0;
        long timestamp = 0;
        
        if (availableBedsObj instanceof Long) {
            availableBeds = ((Long) availableBedsObj).intValue();
        } else if (availableBedsObj instanceof Double) {
            availableBeds = ((Double) availableBedsObj).intValue();
        }
        
        if (availableDoctorsObj instanceof Long) {
            availableDoctors = ((Long) availableDoctorsObj).intValue();
        } else if (availableDoctorsObj instanceof Double) {
            availableDoctors = ((Double) availableDoctorsObj).intValue();
        }
        
        if (timestampObj instanceof Long) {
            timestamp = (Long) timestampObj;
        } else if (timestampObj instanceof Double) {
            timestamp = ((Double) timestampObj).longValue();
        }
        
        // Update last check time
        if (timestamp > lastNotificationCheck) {
            lastNotificationCheck = timestamp;
        }
        
        // Show immediate notification
        showHospitalStatusNotification(notificationId, title, message, hospitalName, hospitalStatus, 
                                     availableBeds, availableDoctors);
        
        // Mark as read
        markNotificationAsRead(notificationId);
    }
    
    private void showHospitalStatusNotification(String notificationId, String title, String message, 
                                             String hospitalName, String hospitalStatus, 
                                             int availableBeds, int availableDoctors) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        Log.d(TAG, "Creating notification with data - Hospital: " + hospitalName + 
            ", Status: " + hospitalStatus + ", Beds: " + availableBeds + ", Doctors: " + availableDoctors);
        
        Intent intent = new Intent(this, Rescuer_Dashboard.class);
        intent.putExtra("notification_type", "hospital_status_update");
        intent.putExtra("hospital_name", hospitalName);
        intent.putExtra("hospital_status", hospitalStatus);
        intent.putExtra("available_beds", availableBeds);
        intent.putExtra("available_doctors", availableDoctors);
        intent.putExtra("highlight_hospital", hospitalName); // To highlight the specific hospital
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            notificationId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Log.d(TAG, "PendingIntent created for notification: " + notificationId.hashCode());
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(new long[]{0, 500, 200, 500})
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setTicker(message) // Show message in status bar
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .build();
        
        notificationManager.notify(notificationId.hashCode(), notification);
        Log.d(TAG, "Hospital status notification displayed: " + title);
        Log.d(TAG, "Notification ID: " + notificationId.hashCode() + ", Intent: " + intent.getComponent());
        Log.d(TAG, "Notification clickable: " + (pendingIntent != null ? "YES" : "NO"));
    }
    
    private void markNotificationAsRead(String notificationId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collectionPath = "Sagip/users/rescuer/" + currentUserId + "/notifications";
        
        db.collection(collectionPath)
            .document(notificationId)
            .update("read", true)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Hospital status notification marked as read: " + notificationId);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error marking hospital status notification as read: " + e.getMessage());
            });
    }
    
    private Notification createServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hospital Status Monitoring")
            .setContentText("Monitoring for hospital status updates...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Hospital Status Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Immediate hospital status update notifications");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            channel.enableLights(true);
            channel.setLightColor(0xFF2196F3);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created: " + CHANNEL_ID);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "HospitalStatusNotificationService destroyed");
        stopMonitoring();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}