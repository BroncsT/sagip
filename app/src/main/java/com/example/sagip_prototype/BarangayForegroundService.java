package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Dedicated foreground service for barangay officials to ensure they receive emergency notifications
 * even when the app is completely closed or the device is in deep sleep
 */
public class BarangayForegroundService extends Service {
    
    private static final String TAG = "BarangayForegroundService";
    private static final String CHANNEL_ID = "barangay_foreground_service";
    private static final String EMERGENCY_CHANNEL_ID = "barangay_emergency_channel";
    private static final String CHANNEL_NAME = "Barangay Emergency Service";
    private static final String CHANNEL_DESCRIPTION = "Ensures barangay officials receive emergency alerts when app is closed";
    private static final int FOREGROUND_NOTIFICATION_ID = 6001;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;
    private ListenerRegistration emergencyListener;
    private long listenerStartTime = 0;
    
    // MediaPlayer for emergency alarm sound - similar to rescuer service
    private static MediaPlayer currentMediaPlayer = null;
    private static AudioManager audioManager = null;
    
    // WakeLock to ensure emergency sound plays even in Doze mode
    private static PowerManager.WakeLock emergencySoundWakeLock = null;
    private static PowerManager powerManager = null;
    
    // Audio focus change listener
    private static AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        Log.d(TAG, "🔊 Audio focus changed: " + focusChange);
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚨 BarangayForegroundService created");
        
        // CRITICAL: Call startForeground() as ABSOLUTE FIRST THING
        try {
            createNotificationChannel();
            createEmergencyNotificationChannel();
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create notification channel: " + e.getMessage());
        }
        
        // Build minimal notification with guaranteed system resources
        Notification notification;
        try {
            notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("SAGIP Barangay Service Active")
                    .setContentText("Monitoring for emergency alerts")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to build notification: " + e.getMessage());
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("SAGIP Active")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        }
        
        // MUST call startForeground - this is the critical line
        startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        Log.d(TAG, "✅ BarangayForegroundService started in foreground mode");
        
        // Initialize Firebase AFTER foreground is established
        try {
            db = FirebaseFirestore.getInstance();
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize Firebase: " + e.getMessage());
        }
        
        // Update with full notification (non-critical)
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Failed to update notification (non-critical): " + e.getMessage());
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚨 BarangayForegroundService started");
        
        // Check if user has logged out - if so, don't restart
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (isLoggedOut) {
            Log.w(TAG, "⚠️ User has logged out, stopping BarangayForegroundService");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Get current user data
        String userType = prefs.getString("user_type", null);
        if (userType == null) {
            // Try SagipAppPrefs as alternative
            SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
            userType = sagipPrefs.getString("userType", null);
        }
        
        String userIdFromPrefs = prefs.getString("user_id", null);
        if (userIdFromPrefs == null) {
            // Try SagipAppPrefs as alternative
            SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
            userIdFromPrefs = sagipPrefs.getString("userId", null);
        }
        
        // Get current user from Firebase Auth
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        } else {
            userId = userIdFromPrefs;
        }
        
        Log.d(TAG, "🔍 [BARANGAY_SERVICE] userType: " + userType + ", userId: " + userId);
        
        // Check if user is still logged in and is a barangay official
        if (userType == null || !userType.equals("barangay") || userId == null) {
            Log.w(TAG, "⚠️ Invalid user session (userType: " + userType + ", userId: " + userId + "), stopping service");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Start emergency notification listener
        startEmergencyNotificationListener();
        
        // Update foreground notification
        updateForegroundNotification();
        
        // Mark service as running
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("barangayServiceRunning", true).apply();
        
        Log.d(TAG, "✅ Barangay foreground service running - emergency notifications will work when app is closed");
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "🛑 BarangayForegroundService destroyed");
        
        // Stop emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Clear service running flag
        SharedPreferences sharedPreferences = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        sharedPreferences.edit().putBoolean("barangayServiceRunning", false).apply();
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Creates the foreground notification to keep the service running
     */
    private Notification createForegroundNotification() {
        Intent notificationIntent = new Intent(this, Barangay_Dashboard.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚨 SAGIPP Barangay Service")
                .setContentText("Monitoring for emergency notifications...")
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .build();
    }
    
    /**
     * Updates the foreground notification
     */
    private void updateForegroundNotification() {
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                Notification updatedNotification = createForegroundNotification();
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, updatedNotification);
                Log.d(TAG, "✅ Foreground notification updated");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update foreground notification: " + e.getMessage(), e);
        }
    }
    
    /**
     * Starts listening for emergency notifications
     */
    private void startEmergencyNotificationListener() {
        if (userId == null) {
            Log.w(TAG, "Cannot start emergency listener - userId is null");
            return;
        }
        
        // Remove any existing listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Set listener start time to filter out old notifications
        listenerStartTime = System.currentTimeMillis();
        
        Log.d(TAG, "🚨 Starting barangay emergency notification listener");
        Log.d(TAG, "🚨 Listener path: Sagip/users/barangay/" + userId + "/emergencyNotifications");
        Log.d(TAG, "⏰ Listener start time: " + listenerStartTime);
        
        // Listen for emergency notifications in real-time
        // Only get notifications created AFTER service start to avoid duplicates
        emergencyListener = db.collection("Sagip")
                .document("users")
                .collection("barangay")
                .document(userId)
                .collection("emergencyNotifications")
                .whereGreaterThan("timestamp", listenerStartTime)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to emergency notifications: " + error.getMessage(), error);
                        return;
                    }
                    
                    // Check if user is still a barangay official
                    SharedPreferences currentPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                    String currentUserType = currentPrefs.getString("user_type", null);
                    if (currentUserType == null) {
                        SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
                        currentUserType = sagipPrefs.getString("userType", null);
                    }
                    boolean isLoggedOut = currentPrefs.getBoolean("user_logged_out", false);
                    
                    if (isLoggedOut || currentUserType == null || !currentUserType.equals("barangay")) {
                        Log.w(TAG, "⚠️ User is no longer a barangay official or has logged out, stopping service");
                        stopSelf();
                        return;
                    }
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        Log.d(TAG, "🔔 Received " + querySnapshot.size() + " new emergency notifications");
                        
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            handleEmergencyNotification(document);
                        }
                    }
                });
    }
    
    /**
     * Handles an emergency notification
     */
    private void handleEmergencyNotification(QueryDocumentSnapshot document) {
        try {
            String notificationId = document.getId();
            String type = document.getString("type");
            String seniorName = document.getString("seniorName");
            String seniorPhone = document.getString("seniorPhone");
            String locationAddress = document.getString("locationAddress");
            String barangay = document.getString("barangay");
            String requestId = document.getString("requestId");
            String emergencyType = document.getString("emergencyType");
            Boolean isRead = document.getBoolean("isRead");
            Double seniorLatitude = document.getDouble("seniorLatitude");
            Double seniorLongitude = document.getDouble("seniorLongitude");
            String currentLocation = document.getString("currentLocation");
            
            Log.d(TAG, "🚨 Processing emergency notification: " + seniorName + " (Request ID: " + requestId + ")");
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] IsRead: " + isRead);
            
            // CRITICAL FIX: Check if dashboard is currently active - same as rescuer service
            // If dashboard is active, defer to dashboard for in-app alert
            boolean isDashboardActive = Barangay_Dashboard.isDashboardActive;
            Log.d(TAG, "📱 [HANDLE_NOTIFICATION] Dashboard active check - isDashboardActive: " + isDashboardActive);
            
            if ("EMERGENCY_ALERT".equals(type) && seniorName != null && (isRead == null || !isRead)) {
                // CRITICAL FIX: If dashboard is active, defer to dashboard for in-app alert
                if (isDashboardActive) {
                    Log.d(TAG, "📱 [BACKGROUND] Dashboard is ACTIVE - deferring to dashboard for in-app alert");
                    Log.d(TAG, "📱 [BACKGROUND] NOT processing - dashboard will handle this notification");
                    return; // Let dashboard handle it
                }
                
                Log.d(TAG, "📱 [BACKGROUND] Dashboard is INACTIVE - background service will show system notification");
                
                // CRITICAL FIX: Mark as read FIRST to prevent duplicates - same as rescuer
                // Show notification ONLY in success callback
                document.getReference().update("isRead", true, "processedBy", "backgroundService")
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ [BACKGROUND] Marked notification as read and processing");
                            
                            // Show high-priority emergency notification with alarm sound
                            showEmergencyNotification(notificationId, seniorName, seniorPhone, locationAddress, barangay, requestId, emergencyType, seniorLatitude, seniorLongitude, currentLocation);
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "⚠️ [BACKGROUND] Failed to mark as read (might already be processed): " + e.getMessage());
                        });
            } else if ("EMERGENCY_ALERT".equals(type) && isRead != null && isRead) {
                Log.d(TAG, "🔇 Ignoring already read emergency notification: " + seniorName + " (Request ID: " + requestId + ")");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling emergency notification: " + e.getMessage(), e);
        }
    }
    
    /**
     * Shows emergency notification to barangay official
     */
    private void showEmergencyNotification(String notificationId, String seniorName, String seniorPhone, 
                                           String locationAddress, String barangay, String requestId, 
                                           String emergencyType, Double seniorLatitude, Double seniorLongitude, 
                                           String currentLocation) {
        Log.d(TAG, "🔔 Showing emergency notification for barangay official: " + seniorName);
        
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Notification permission denied - cannot show notification");
                // Still play sound even if notifications are blocked
                playEmergencyAlarmSound();
                return;
            }
        }
        
        // CRITICAL: Play emergency alarm sound - same as rescuer
        playEmergencyAlarmSound();
        
        // Vibrate device for emergency
        vibrateDevice();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent for notification click
        Intent notificationIntent = new Intent(this, Barangay_Dashboard.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra("emergency_notification", true);
        notificationIntent.putExtra("notification_id", notificationId);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("senior_phone", seniorPhone);
        notificationIntent.putExtra("location_address", locationAddress);
        notificationIntent.putExtra("barangay", barangay);
        notificationIntent.putExtra("request_id", requestId);
        notificationIntent.putExtra("emergency_type", emergencyType);
        notificationIntent.putExtra("from_foreground_service", true);
        if (seniorLatitude != null) {
            notificationIntent.putExtra("senior_latitude", seniorLatitude);
        }
        if (seniorLongitude != null) {
            notificationIntent.putExtra("senior_longitude", seniorLongitude);
        }
        if (currentLocation != null) {
            notificationIntent.putExtra("current_location", currentLocation);
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create call intent
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(android.net.Uri.parse("tel:" + seniorPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis() + 1,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create navigation intent - same as rescuer
        Intent navIntent = new Intent(Intent.ACTION_VIEW);
        navIntent.setData(android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + 
            android.net.Uri.encode(locationAddress != null ? locationAddress : "Angeles City, Pampanga") + "&travelmode=driving"));
        PendingIntent navPendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis() + 2,
                navIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create dismiss intent to stop sound - opens dashboard with dismiss flag
        Intent dismissIntent = new Intent(this, Barangay_Dashboard.class);
        dismissIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        dismissIntent.putExtra("dismiss_sound", true);
        dismissIntent.putExtra("from_foreground_service", true);
        PendingIntent dismissPendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis() + 3,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String bigText = "🚨 URGENT: Senior needs immediate help!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not provided") + "\n" +
                        "📍 Location: " + (locationAddress != null ? locationAddress : "Not provided") + "\n" +
                        "🆘 Emergency: " + (emergencyType != null ? emergencyType : "SOS") + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        // Use emergency channel with alarm sound - same as rescuer
        Notification notification = new NotificationCompat.Builder(this, EMERGENCY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_emergency)
                .setContentTitle("🚨 EMERGENCY ALERT 🚨")
                .setContentText(seniorName + " needs immediate help!")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000)
                .setFullScreenIntent(pendingIntent, true)
                .addAction(android.R.drawable.ic_menu_call, "📞 CALL", callPendingIntent)
                .addAction(android.R.drawable.ic_menu_directions, "🗺️ NAVIGATE", navPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🔇 DISMISS", dismissPendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build();
        
        int notifyId = (int) System.currentTimeMillis() % Integer.MAX_VALUE;
        notificationManager.notify(notifyId, notification);
        
        Log.d(TAG, "🔔 Emergency notification sent to barangay official for: " + seniorName);
        Log.d(TAG, "🔊 Emergency alarm sound triggered for barangay");
    }
    
    /**
     * Creates notification channel for the foreground service
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            
            Log.d(TAG, "✅ Barangay foreground service notification channel created");
        }
    }
    
    /**
     * Creates high-priority emergency notification channel with alarm sound - same as rescuer
     */
    private void createEmergencyNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    EMERGENCY_CHANNEL_ID,
                    "Barangay Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            
            // Configure custom alarm sound for emergency notifications - same as rescuer
            Uri alarmSound = getCustomAlarmSound();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build();
            
            channel.setSound(alarmSound, audioAttributes);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light
            channel.enableVibration(true);
            channel.setDescription("High-priority emergency alerts for barangay officials");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "🔊 Barangay emergency notification channel created with alarm sound: " + alarmSound.toString());
            }
        }
    }
    
    /**
     * Gets custom alarm sound URI - same as rescuer service
     */
    private Uri getCustomAlarmSound() {
        try {
            // Try to use custom alarm sound from raw resources
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
     * Acquire a WakeLock to ensure CPU stays awake during emergency sound playback.
     * This is critical for playing sounds when the device is in Doze mode.
     */
    private void acquireEmergencyWakeLock() {
        try {
            // Initialize PowerManager if not already done
            if (powerManager == null) {
                powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            }
            
            // Release any existing wake lock first
            releaseEmergencyWakeLock();
            
            if (powerManager != null) {
                // Use PARTIAL_WAKE_LOCK to keep CPU running for audio playback
                emergencySoundWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SAGIP:BarangayEmergencySoundWakeLock"
                );
                
                // Acquire with a 60-second timeout to prevent battery drain if something goes wrong
                emergencySoundWakeLock.acquire(60 * 1000L);
                Log.d(TAG, "🔓 Barangay Emergency WakeLock ACQUIRED - CPU will stay awake for sound playback");
            } else {
                Log.e(TAG, "❌ PowerManager is null, cannot acquire WakeLock");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error acquiring emergency WakeLock: " + e.getMessage(), e);
        }
    }
    
    /**
     * Release the emergency sound WakeLock
     */
    private static void releaseEmergencyWakeLock() {
        try {
            if (emergencySoundWakeLock != null && emergencySoundWakeLock.isHeld()) {
                emergencySoundWakeLock.release();
                Log.d(TAG, "🔒 Barangay Emergency WakeLock RELEASED");
            }
            emergencySoundWakeLock = null;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error releasing emergency WakeLock: " + e.getMessage(), e);
            emergencySoundWakeLock = null;
        }
    }
    
    /**
     * Plays emergency alarm sound - same functionality as rescuer service
     */
    private void playEmergencyAlarmSound() {
        Log.d(TAG, "🔊 Playing emergency alarm sound for barangay...");
        try {
            Uri soundUri = getCustomAlarmSound();
            Log.d(TAG, "🔊 Testing with sound URI: " + soundUri.toString());
            
            // Stop any currently playing sound
            stopEmergencySound();
            
            // CRITICAL: Acquire WakeLock BEFORE playing sound to ensure CPU stays awake in Doze mode
            acquireEmergencyWakeLock();
            
            // Initialize AudioManager if not already done
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }
            
            // Check current ringer mode and log it
            int ringerMode = audioManager.getRingerMode();
            Log.d(TAG, "🔊 Current ringer mode: " + ringerMode + " (0=SILENT, 1=VIBRATE, 2=NORMAL)");
            
            // Ensure alarm volume is at maximum for emergency
            int maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int currentAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            Log.d(TAG, "🔊 Current alarm volume: " + currentAlarmVolume + "/" + maxAlarmVolume);
            
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0);
            Log.d(TAG, "🔊 Set alarm volume to maximum: " + maxAlarmVolume);
            
            // Request audio focus for emergency sound
            int result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            );
            
            Log.d(TAG, "🔊 Audio focus request result: " + result + " (1=GRANTED, 0=FAILED)");
            
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "🔊 Audio focus granted for emergency sound");
                playMediaPlayer(soundUri);
            } else {
                Log.w(TAG, "⚠️ Audio focus not granted - but will play emergency sound anyway!");
                // For emergency sounds, play anyway even without audio focus
                playMediaPlayer(soundUri);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error playing emergency alarm sound: " + e.getMessage(), e);
            if (audioManager != null) {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }
    }
    
    /**
     * Plays the MediaPlayer with the given sound URI
     */
    private void playMediaPlayer(Uri soundUri) {
        try {
            currentMediaPlayer = MediaPlayer.create(this, soundUri);
            if (currentMediaPlayer != null) {
                Log.d(TAG, "🔊 MediaPlayer created successfully");
                
                // Set audio attributes for emergency sound
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();
                    currentMediaPlayer.setAudioAttributes(audioAttributes);
                    Log.d(TAG, "🔊 Audio attributes set for API " + Build.VERSION.SDK_INT);
                } else {
                    currentMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                    Log.d(TAG, "🔊 Audio stream type set to ALARM for API " + Build.VERSION.SDK_INT);
                }
                
                // Set volume to maximum for emergency
                currentMediaPlayer.setVolume(1.0f, 1.0f);
                Log.d(TAG, "🔊 MediaPlayer volume set to maximum");
                
                // Start playback
                currentMediaPlayer.start();
                Log.d(TAG, "🔊 MediaPlayer started successfully");
                
                currentMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "❌ MediaPlayer error: what=" + what + ", extra=" + extra);
                    mp.release();
                    currentMediaPlayer = null;
                    if (audioManager != null) {
                        audioManager.abandonAudioFocus(audioFocusChangeListener);
                    }
                    // Release WakeLock on error
                    releaseEmergencyWakeLock();
                    return true;
                });
                
                currentMediaPlayer.setOnCompletionListener(mp -> {
                    Log.d(TAG, "🔊 Sound playback completed");
                    mp.release();
                    currentMediaPlayer = null;
                    if (audioManager != null) {
                        audioManager.abandonAudioFocus(audioFocusChangeListener);
                    }
                    // Release WakeLock when done
                    releaseEmergencyWakeLock();
                });
            } else {
                Log.e(TAG, "❌ Failed to create MediaPlayer");
                if (audioManager != null) {
                    audioManager.abandonAudioFocus(audioFocusChangeListener);
                }
                // Release WakeLock if MediaPlayer creation failed
                releaseEmergencyWakeLock();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error playing MediaPlayer: " + e.getMessage(), e);
            // Release WakeLock on error
            releaseEmergencyWakeLock();
        }
    }
    
    /**
     * Stops the emergency alarm sound
     */
    public static void stopEmergencySound() {
        Log.d(TAG, "🔇 Stopping emergency sound");
        if (currentMediaPlayer != null) {
            try {
                // Set volume to 0 immediately to mute any buffered audio
                currentMediaPlayer.setVolume(0.0f, 0.0f);
                Log.d(TAG, "🔇 Volume set to 0 (muted)");
                
                if (currentMediaPlayer.isPlaying()) {
                    currentMediaPlayer.stop();
                    Log.d(TAG, "🔇 Emergency sound stopped successfully");
                }
                
                // Reset before releasing to clear any buffered audio
                currentMediaPlayer.reset();
                Log.d(TAG, "🔇 MediaPlayer reset (cleared buffers)");
                
                currentMediaPlayer.release();
                Log.d(TAG, "🔇 MediaPlayer released and cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaPlayer: " + e.getMessage());
                try {
                    if (currentMediaPlayer != null) {
                        currentMediaPlayer.release();
                    }
                } catch (Exception e2) {
                    Log.e(TAG, "Error releasing MediaPlayer: " + e2.getMessage());
                }
            }
            currentMediaPlayer = null;
        }
        
        if (audioManager != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
            Log.d(TAG, "🔇 Audio focus abandoned");
        }
        
        // Release WakeLock when stopping sound
        releaseEmergencyWakeLock();
    }
    
    /**
     * Vibrates the device for emergency alert - same as rescuer
     */
    private void vibrateDevice() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                    new long[]{0, 1000, 500, 1000, 500, 1000}, 
                    -1 // Don't repeat
                );
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000}, -1);
            }
            Log.d(TAG, "📳 Device vibration triggered");
        }
    }
}
