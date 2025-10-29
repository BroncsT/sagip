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
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class EmergencySOSBackgroundService extends Service {
    
    private static final String TAG = "EmergencySOSService";
    private static final String CHANNEL_ID = "emergency_sos_channel";
    private static final int NOTIFICATION_ID = 9999;
    private static final int FOREGROUND_SERVICE_ID = 1001;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;
    
    // Static listener management to prevent duplicates across service restarts
    private static boolean isListening = false;
    private static com.google.firebase.firestore.ListenerRegistration emergencyListener = null;
    
    // Static MediaPlayer to track current playing sound
    private static MediaPlayer currentMediaPlayer = null;
    private static AudioManager audioManager = null;
    
    // Track notification IDs to dismiss them when user responds
    private static java.util.Set<Integer> activeNotificationIds = new java.util.HashSet<>();
    private static Context appContext = null;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "⚡ EmergencySOSBackgroundService onCreate() START");
        
        // CRITICAL FIX: Call startForeground() IMMEDIATELY with minimal notification
        // Android O+ enforces a 5-second timeout from startForegroundService() to startForeground()
        // We MUST call startForeground() before doing ANYTHING else, even creating notification channels
        
        try {
            // Create absolute minimal notification FIRST, without channel setup
            Notification notification;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android O+, we need a channel, but we'll create it inline with minimal code
                try {
                    NotificationManager nm = getSystemService(NotificationManager.class);
                    if (nm != null) {
                        // Check if channel already exists, if not create it quickly
                        NotificationChannel existingChannel = nm.getNotificationChannel(CHANNEL_ID);
                        if (existingChannel == null) {
                            NotificationChannel channel = new NotificationChannel(
                                CHANNEL_ID,
                                "Emergency Service",
                                NotificationManager.IMPORTANCE_LOW
                            );
                            nm.createNotificationChannel(channel);
                        }
                    }
                } catch (Exception ignored) {
                    // If channel creation fails, try with a basic channel
                    try {
                        NotificationManager nm = getSystemService(NotificationManager.class);
                        if (nm != null) {
                            NotificationChannel fallbackChannel = new NotificationChannel(
                                "emergency_minimal",
                                "Service",
                                NotificationManager.IMPORTANCE_LOW
                            );
                            nm.createNotificationChannel(fallbackChannel);
                        }
                    } catch (Exception ignored2) {}
                }
                
                // Create notification - use whichever channel was created
                notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Service")
                    .setContentText("Running")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
            } else {
                // For Android N and below, no channel needed
                notification = new Notification.Builder(this)
                    .setContentTitle("Service")
                    .setContentText("Running")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
            }
            
            // CALL STARTFOREGROUND IMMEDIATELY - This is the most critical line
            startForeground(FOREGROUND_SERVICE_ID, notification);
            Log.d(TAG, "✅ startForeground() called successfully with minimal notification");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ CRITICAL ERROR calling startForeground: " + e.getMessage(), e);
            
            // ABSOLUTE LAST RESORT - try to call startForeground with anything
            try {
                // Try creating the most basic notification possible
                Notification emergencyNotification;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    Notification.Builder builder = new Notification.Builder(this)
                        .setContentTitle("Service")
                        .setSmallIcon(android.R.drawable.ic_dialog_info);
                    emergencyNotification = builder.build();
                } else {
                    emergencyNotification = new Notification();
                    emergencyNotification.icon = android.R.drawable.ic_dialog_info;
                }
                
                startForeground(FOREGROUND_SERVICE_ID, emergencyNotification);
                Log.d(TAG, "✅ startForeground() called with emergency fallback notification");
            } catch (Exception e2) {
                Log.e(TAG, "❌ CATASTROPHIC FAILURE - Cannot start foreground service: " + e2.getMessage(), e2);
                // At this point, the service will crash, but we've tried everything
            }
        }
        
        Log.d(TAG, "⚡ EmergencySOSBackgroundService foreground mode COMPLETE");
        
        // NOW it's safe to do the rest of initialization
        // Update the notification channel with proper settings
        createNotificationChannel();
        
        // Update foreground notification to a better one
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                Notification betterNotification = createForegroundNotification();
                if (betterNotification != null) {
                    notificationManager.notify(FOREGROUND_SERVICE_ID, betterNotification);
                    Log.d(TAG, "✅ Updated foreground notification with better version");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Could not update foreground notification, but service is running: " + e.getMessage());
        }
        
        Log.d(TAG, "EmergencySOSBackgroundService created");
        
        // Store application context for static methods
        if (appContext == null) {
            appContext = getApplicationContext();
        }
        
        // Initialize Firebase
        try {
            db = FirebaseFirestore.getInstance();
            mAuth = FirebaseAuth.getInstance();
            Log.d(TAG, "✅ Firebase initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Firebase initialization failed: " + e.getMessage(), e);
        }
        
        // Note: User data and emergency listener will be set up in onStartCommand()
        // to ensure we always have fresh user data when the service starts
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "EmergencySOSBackgroundService started");
        
        // Foreground notification already started and updated in onCreate()
        // No need to update it again here - it would be redundant
        
        // Check if user has logged out - if so, don't restart
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (isLoggedOut) {
            Log.w(TAG, "⚠️ User has logged out, stopping EmergencySOSBackgroundService");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Refresh user data from SharedPreferences and Firebase Auth on each start
        // Try multiple SharedPreferences keys to find user info
        String userType = prefs.getString("user_type", null);
        if (userType == null) {
            // Try SagipAppPrefs as alternative
            SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
            userType = sagipPrefs.getString("userType", null);
            Log.d(TAG, "🔍 [START_SERVICE] Trying SagipAppPrefs, userType: " + userType);
        }
        
        String userIdFromPrefs = prefs.getString("user_id", null);
        if (userIdFromPrefs == null) {
            // Try SagipAppPrefs as alternative
            SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
            userIdFromPrefs = sagipPrefs.getString("userId", null);
            Log.d(TAG, "🔍 [START_SERVICE] Trying SagipAppPrefs, userId: " + userIdFromPrefs);
        }
        
        // Get current user from Firebase Auth
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        } else {
            userId = userIdFromPrefs; // Fallback to SharedPreferences
        }
        
        Log.d(TAG, "🔍 [START_SERVICE] userType: " + userType + ", userId: " + userId);
        
        // Check if user is still logged in and is a rescuer
        if (userType == null || !userType.equals("rescuer") || userId == null) {
            Log.w(TAG, "⚠️ EmergencySOSBackgroundService onStartCommand - Invalid user session (userType: " + userType + ", userId: " + userId + "), stopping service");
            stopSelf();
            return START_NOT_STICKY; // Don't restart if killed
        }
        
        // Start listening for emergency notifications with fresh user data
        if (!isListening) {
            startEmergencySOSListener();
        }
        
        return START_STICKY; // Restart if killed by system
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "EmergencySOSBackgroundService destroyed");
        
        // Only remove listener and reset flag if service is truly being destroyed
        // (not just restarting)
        if (emergencyListener != null) {
            Log.d(TAG, "🛑 Removing Firestore listener on service destroy");
            emergencyListener.remove();
            emergencyListener = null;
        }
        isListening = false;
        
        // Stop any playing emergency sound when service is destroyed
        stopEmergencySound();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            
            // Configure custom alarm sound for emergency notifications
            Uri alarmSound = getCustomAlarmSound();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // Ensure sound plays even in silent mode
                .build();
            
            channel.setSound(alarmSound, audioAttributes);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light
            channel.enableVibration(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "🔊 Notification channel created with custom sound: " + alarmSound.toString());
                Log.d(TAG, "🔊 Channel importance: " + channel.getImportance());
                Log.d(TAG, "🔊 Channel sound enabled: " + (channel.getSound() != null));
            } else {
                Log.e(TAG, "❌ NotificationManager is null");
            }
        }
    }
    
    private Notification createForegroundNotification() {
        try {
            Intent notificationIntent = getDashboardIntentForCurrentUser();
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Emergency Service")
                .setContentText("Monitoring for emergency alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setShowWhen(false);
            
            Notification notification = builder.build();
            Log.d(TAG, "✅ Foreground notification created successfully");
            return notification;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating foreground notification: " + e.getMessage(), e);
            // Return a minimal notification as fallback
            try {
                return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Service Running")
                    .setContentText("Active")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setSilent(true)
                    .build();
            } catch (Exception e2) {
                Log.e(TAG, "❌ Critical error creating minimal notification: " + e2.getMessage(), e2);
                return null;
            }
        }
    }
    
    private void startEmergencySOSListener() {
        if (userId == null) {
            Log.w(TAG, "Cannot start emergency SOS listener - userId is null");
            return;
        }
        
        // Check if current user is a rescuer - if not, stop the service
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userType = prefs.getString("user_type", null);
        if (userType == null) {
            // Try SagipAppPrefs as alternative
            SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
            userType = sagipPrefs.getString("userType", null);
        }
        
        Log.d(TAG, "🔍 [START_LISTENER] userType from prefs: " + userType);
        
        if (userType == null || !userType.equals("rescuer")) {
            Log.w(TAG, "⚠️ User is not a rescuer (userType: " + userType + "), stopping EmergencySOSBackgroundService");
            stopSelf();
            return;
        }
        
        // PREVENT DUPLICATE LISTENERS - Check if already listening
        if (isListening && emergencyListener != null) {
            Log.d(TAG, "✅ [DUPLICATE_PREVENTION] Already listening for emergency notifications, skipping listener creation");
            Log.d(TAG, "✅ [DUPLICATE_PREVENTION] Existing listener is active, this prevents double notifications");
            return;
        }
        
        // If flag is set but listener is null, something went wrong - clean up
        if (isListening && emergencyListener == null) {
            Log.w(TAG, "⚠️ [DUPLICATE_PREVENTION] isListening flag was set but listener was null, resetting");
            isListening = false;
        }
        
        // Remove any existing listener before creating a new one
        if (emergencyListener != null) {
            Log.d(TAG, "🔄 [DUPLICATE_PREVENTION] Removing old listener before creating new one");
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // NOTE: We don't mark notifications as read in the background service
        // The dashboard already handles this when the app is opened
        // Background service only runs when app is closed, so old notifications
        // should already be filtered by the dashboard
        
        Log.d(TAG, "🚨 Starting emergency SOS listener for rescuer: " + userId);
        Log.d(TAG, "🚨 Listener path: Sagip/users/rescuer/" + userId + "/emergencyNotifications");
        isListening = true;
        
        // Listen for emergency SOS notifications in real-time
        // REMOVED .limit(1) to process ALL unread notifications, not just the most recent
        emergencyListener = db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .document(userId)
          .collection("emergencyNotifications")
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .addSnapshotListener((querySnapshot, error) -> {
              if (error != null) {
                  Log.e(TAG, "Error listening to emergency SOS notifications: " + error.getMessage(), error);
                  return;
              }
              
              // Check if user is still a rescuer before processing notifications
              SharedPreferences currentPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
              String currentUserType = currentPrefs.getString("user_type", null);
              if (currentUserType == null) {
                  // Try SagipAppPrefs as alternative
                  SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
                  currentUserType = sagipPrefs.getString("userType", null);
              }
              boolean isLoggedOut = currentPrefs.getBoolean("user_logged_out", false);
              
              if (isLoggedOut || currentUserType == null || !currentUserType.equals("rescuer")) {
                  Log.w(TAG, "⚠️ User is no longer a rescuer or has logged out (userType: " + currentUserType + ", isLoggedOut: " + isLoggedOut + "), stopping EmergencySOSBackgroundService");
                  stopSelf();
                  return;
              }
              
              Log.d(TAG, "🔍 [FIRESTORE_LISTENER] Query snapshot received");
              Log.d(TAG, "🔍 [FIRESTORE_LISTENER] Has documents: " + (querySnapshot != null && !querySnapshot.isEmpty()));
              if (querySnapshot != null) {
                  Log.d(TAG, "🔍 [FIRESTORE_LISTENER] Document count: " + querySnapshot.size());
              }
              
              if (querySnapshot != null && !querySnapshot.isEmpty()) {
                  for (QueryDocumentSnapshot document : querySnapshot) {
                      Log.d(TAG, "🔍 [FIRESTORE_LISTENER] Processing document: " + document.getId());
                      handleEmergencySOSNotification(document);
                  }
              } else {
                  Log.d(TAG, "🔍 [FIRESTORE_LISTENER] No documents in snapshot");
              }
          });
    }
    
    private void handleEmergencySOSNotification(QueryDocumentSnapshot document) {
        try {
            // Double-check user type before processing notification
            SharedPreferences currentPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            String currentUserType = currentPrefs.getString("user_type", null);
            if (currentUserType == null) {
                // Try SagipAppPrefs as alternative
                SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", Context.MODE_PRIVATE);
                currentUserType = sagipPrefs.getString("userType", null);
            }
            boolean isLoggedOut = currentPrefs.getBoolean("user_logged_out", false);
            
            if (isLoggedOut || currentUserType == null || !currentUserType.equals("rescuer")) {
                Log.w(TAG, "⚠️ User is no longer a rescuer or has logged out (userType: " + currentUserType + ", isLoggedOut: " + isLoggedOut + "), ignoring emergency notification");
                stopSelf();
                return;
            }
            
            String type = document.getString("type");
            String title = document.getString("title");
            String message = document.getString("message");
            String seniorName = document.getString("seniorName");
            String seniorPhone = document.getString("seniorPhone");
            String locationAddress = document.getString("locationAddress");
            String requestId = document.getString("requestId");
            Long timestamp = document.getLong("timestamp");
            Boolean isRead = document.getBoolean("isRead");
            String notificationStatus = document.getString("notificationStatus");
            
            // Read GPS coordinates from notification data
            Double seniorLat = document.getDouble("seniorLat");
            Double seniorLng = document.getDouble("seniorLng");
            
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] Document ID: " + document.getId());
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] Type: " + type);
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] IsRead: " + isRead);
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] NotificationStatus: " + notificationStatus);
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] RequestId: " + requestId);
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] SeniorName: " + seniorName);
            
            // Process emergency SOS notifications
            // Note: We rely on the isRead flag to prevent duplicate processing
            // Old notifications should already be marked as read
            if ("EMERGENCY_SOS".equals(type) && (isRead == null || !isRead)) {
                // Only process unread emergency SOS notifications that are NOT assigned
                Log.d(TAG, "🚨 Received emergency SOS notification: " + seniorName + " (Request ID: " + requestId + ")");
                
                // Mark as read FIRST to prevent race condition with dashboard
                // Use atomic update to ensure only one process marks it as read
                document.getReference().update("isRead", true, "processedBy", "backgroundService")
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ [BACKGROUND] Marked notification as read and processing");
                        
                        // Show high-priority notification with alarm sound
                        showEmergencySOSNotification(seniorName, seniorPhone, locationAddress, timestamp, requestId, document.getId(), seniorLat, seniorLng);
                        
                        // Vibrate device
                        vibrateDevice();
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "⚠️ [BACKGROUND] Failed to mark as read (might already be processed by dashboard): " + e.getMessage());
                    });
            } else if ("EMERGENCY_SOS".equals(type) && isRead != null && isRead) {
                Log.d(TAG, "🔇 Ignoring already read emergency SOS notification: " + seniorName + " (Request ID: " + requestId + ")");
            } else {
                Log.d(TAG, "🔇 Ignoring non-emergency notification: " + type + " (IsRead: " + isRead + ")");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling emergency SOS notification: " + e.getMessage(), e);
        }
    }
    
    private void showEmergencySOSNotification(String seniorName, String seniorPhone, String locationAddress, Long timestamp, String requestId, String notificationId, Double seniorLat, Double seniorLng) {
        Log.d(TAG, "🔔 Creating emergency SOS background notification for: " + seniorName + " (Request ID: " + requestId + ")");
        
        // CRITICAL: Check notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ NOTIFICATION PERMISSION DENIED - Cannot show notifications!");
                Log.e(TAG, "❌ User must grant notification permission in Settings → Apps → SAGIP → Permissions → Notifications");
                Log.e(TAG, "❌ Playing sound anyway...");
                // Still try to play sound even if notifications are blocked
                testSoundPlayback();
                return; // Exit early - can't show notification without permission
            } else {
                Log.d(TAG, "✅ Notification permission granted - proceeding with notification");
            }
        } else {
            Log.d(TAG, "ℹ️ Android < 13 - notification permission not required");
        }
        
        // Test sound playback directly
        testSoundPlayback();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent for when notification is tapped - this will open the app even when closed
        Intent notificationIntent = getDashboardIntentForCurrentUser();
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra("emergency_sos_clicked", true);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("senior_phone", seniorPhone);
        notificationIntent.putExtra("location_address", locationAddress);
        notificationIntent.putExtra("request_id", requestId);
        notificationIntent.putExtra("notification_id", notificationId); // CRITICAL FIX: Add notification ID for fetching fresh data
        notificationIntent.putExtra("from_emergency_notification", true);
        
        Log.d(TAG, "📋 [NOTIFICATION_FIX] Added notification_id to intent: " + notificationId);
        
        // Add GPS coordinates for accurate navigation
        if (seniorLat != null && seniorLng != null) {
            notificationIntent.putExtra("senior_lat", seniorLat);
            notificationIntent.putExtra("senior_lng", seniorLng);
            Log.d(TAG, "📍 Added GPS coordinates to notification intent: " + seniorLat + ", " + seniorLng);
        } else {
            Log.w(TAG, "⚠️ No GPS coordinates available for notification intent");
        }
        
        // Create pending intent with unique request code
        int requestCode = (int) System.currentTimeMillis() % Integer.MAX_VALUE;
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                requestCode, 
                notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create call intent
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(android.net.Uri.parse("tel:" + seniorPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                this,
                requestCode + 1,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create navigation intent
        Intent navIntent = new Intent(Intent.ACTION_VIEW);
        navIntent.setData(android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + 
            android.net.Uri.encode("Angeles City, Pampanga") + "&travelmode=driving"));
        PendingIntent navPendingIntent = PendingIntent.getActivity(
                this,
                requestCode + 2,
                navIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String timeStr = "Unknown time";
        if (timestamp != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault());
            timeStr = sdf.format(new java.util.Date(timestamp));
        }
        
        String bigText = "🚨 URGENT: Senior needs immediate help!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + seniorPhone + "\n" +
                        "📍 Location: " + locationAddress + "\n" +
                        "⏰ Time: " + timeStr + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // Use app's notification icon
                .setContentTitle("🚨 EMERGENCY ALERT 🚨")
                .setContentText(seniorName + " needs immediate help!")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false) // CRITICAL: Don't dismiss when tapped - keep it visible
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound()) // AudioAttributes are set on the channel, not here
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000) // Red light blinking
                .setFullScreenIntent(pendingIntent, true) // Show as full screen on lock screen
                .addAction(android.R.drawable.ic_menu_call, "📞 CALL", callPendingIntent)
                .addAction(android.R.drawable.ic_menu_directions, "🗺️ NAVIGATE", navPendingIntent)
                .setOngoing(true) // CRITICAL: Make persistent - cannot be dismissed by swiping
                // Removed setTimeoutAfter - notification stays until emergency is handled
                .setDefaults(NotificationCompat.DEFAULT_ALL); // Add default notification behavior
        
        android.app.Notification notification = builder.build();
        notificationManager.notify(requestCode, notification);
        
        // Track this notification ID so it can be dismissed later
        activeNotificationIds.add(requestCode);
        
        Log.d(TAG, "🔔 Emergency SOS notification sent for: " + seniorName);
        Log.d(TAG, "🔊 Notification ID: " + requestCode);
        Log.d(TAG, "🔊 Notification sound URI: " + getCustomAlarmSound().toString());
        Log.d(TAG, "🔊 Notification flags: " + notification.flags);
        Log.d(TAG, "🔊 Notification has content intent: " + (notification.contentIntent != null));
        Log.d(TAG, "🔊 Notification is clickable: " + notification.contentIntent);
    }
    
    private void vibrateDevice() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                    new long[]{0, 1000, 500, 1000, 500, 1000}, 
                    -1 // Repeat indefinitely
                );
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000}, -1);
            }
        }
    }
    
    private void testSoundPlayback() {
        Log.d(TAG, "🔊 Testing sound playback directly...");
        try {
            Uri soundUri = getCustomAlarmSound();
            Log.d(TAG, "🔊 Testing with sound URI: " + soundUri.toString());
            
            // Stop any currently playing sound
            stopEmergencySound();
            
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
                    
                    currentMediaPlayer.setOnPreparedListener(mp -> {
                        Log.d(TAG, "🔊 MediaPlayer prepared, starting playback");
                        mp.start();
                        Log.d(TAG, "🔊 MediaPlayer started successfully");
                    });
                    
                    currentMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        Log.e(TAG, "❌ MediaPlayer error: what=" + what + ", extra=" + extra);
                        mp.release();
                        currentMediaPlayer = null;
                        // Abandon audio focus on error
                        if (audioManager != null) {
                            audioManager.abandonAudioFocus(audioFocusChangeListener);
                        }
                        return true;
                    });
                    
                    currentMediaPlayer.setOnCompletionListener(mp -> {
                        Log.d(TAG, "🔊 Sound playback completed");
                        mp.release();
                        currentMediaPlayer = null;
                        // Abandon audio focus when done
                        if (audioManager != null) {
                            audioManager.abandonAudioFocus(audioFocusChangeListener);
                        }
                    });
                } else {
                    Log.e(TAG, "❌ Failed to create MediaPlayer");
                    // Abandon audio focus if MediaPlayer creation failed
                    if (audioManager != null) {
                        audioManager.abandonAudioFocus(audioFocusChangeListener);
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Audio focus not granted for emergency sound");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error testing sound playback: " + e.getMessage(), e);
            // Abandon audio focus on error
            if (audioManager != null) {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        }
    }
    
    /**
     * Public method to test emergency sound playback - can be called for debugging
     */
    public void testEmergencySound() {
        Log.d(TAG, "🔊 Testing emergency sound from public method...");
        testSoundPlayback();
    }
    
    /**
     * Static method to stop the emergency sound from anywhere in the app
     * Uses aggressive stopping to ensure sound stops immediately
     */
    public static void stopEmergencySound() {
        Log.d(TAG, "🔇 Stopping emergency sound...");
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
                currentMediaPlayer = null;
                Log.d(TAG, "🔇 MediaPlayer released and cleared");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error stopping emergency sound: " + e.getMessage(), e);
                try {
                    if (currentMediaPlayer != null) {
                        currentMediaPlayer.release();
                    }
                } catch (Exception e2) {
                    Log.e(TAG, "❌ Error releasing MediaPlayer: " + e2.getMessage());
                }
                currentMediaPlayer = null;
            }
        } else {
            Log.d(TAG, "🔇 No emergency sound currently playing");
        }
        
        // Abandon audio focus when stopping sound
        if (audioManager != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
            Log.d(TAG, "🔇 Audio focus abandoned");
        }
    }
    
    /**
     * Static method to dismiss all active emergency notifications
     * This stops both the MediaPlayer sound AND the notification system sound
     */
    public static void dismissAllEmergencyNotifications() {
        Log.d(TAG, "🔕 Dismissing all active emergency notifications...");
        
        // First stop the MediaPlayer sound
        stopEmergencySound();
        
        // Then dismiss all tracked notifications
        if (appContext != null && !activeNotificationIds.isEmpty()) {
            NotificationManager notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                for (Integer notificationId : activeNotificationIds) {
                    notificationManager.cancel(notificationId);
                    Log.d(TAG, "🔕 Dismissed notification ID: " + notificationId);
                }
                activeNotificationIds.clear();
                Log.d(TAG, "🔕 All emergency notifications dismissed and cleared");
            } else {
                Log.e(TAG, "❌ NotificationManager is null, cannot dismiss notifications");
            }
        } else {
            if (appContext == null) {
                Log.w(TAG, "⚠️ App context is null, cannot dismiss notifications");
            }
            if (activeNotificationIds.isEmpty()) {
                Log.d(TAG, "🔕 No active notifications to dismiss");
            }
        }
    }
    
    /**
     * Audio focus change listener for emergency sounds
     */
    private static final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "🔊 Audio focus changed: " + focusChange);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(TAG, "🔊 Audio focus gained - emergency sound can play");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.w(TAG, "🔊 Audio focus lost - emergency sound interrupted");
                    stopEmergencySound();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.w(TAG, "🔊 Audio focus lost temporarily - emergency sound paused");
                    if (currentMediaPlayer != null && currentMediaPlayer.isPlaying()) {
                        currentMediaPlayer.pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.w(TAG, "🔊 Audio focus lost temporarily - emergency sound can duck");
                    // For emergency sounds, we don't duck - we keep playing at full volume
                    break;
            }
        }
    };
    
    /**
     * Show informational notification for emergencies already assigned to another rescuer
     * This is low priority, no alarm sound, just to keep rescuers informed
     */
    private void showEmergencyAlreadyAssignedNotification(String seniorName, String assignedRescuerName, 
                                                         String assignedRescuerTeam, String requestId, 
                                                         String notificationId) {
        Log.d(TAG, "ℹ️ [ASSIGNED_NOTIFICATION] Showing 'already assigned' notification");
        Log.d(TAG, "ℹ️ [ASSIGNED_NOTIFICATION] Senior: " + seniorName);
        Log.d(TAG, "ℹ️ [ASSIGNED_NOTIFICATION] Assigned to: " + assignedRescuerName + " from " + assignedRescuerTeam);
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent for when notification is tapped
        Intent notificationIntent = getDashboardIntentForCurrentUser();
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra("emergency_already_assigned", true);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("assigned_rescuer_name", assignedRescuerName);
        notificationIntent.putExtra("request_id", requestId);
        
        // Create pending intent with unique request code
        int requestCode = (int) System.currentTimeMillis() % Integer.MAX_VALUE;
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                requestCode, 
                notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String bigText = "ℹ️ Emergency Already Assigned\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "✅ Assigned to: " + assignedRescuerName + "\n" +
                        "🏢 Team: " + (assignedRescuerTeam != null ? assignedRescuerTeam : "Emergency Response") + "\n\n" +
                        "This emergency is being handled by another rescuer.";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("ℹ️ Emergency Already Assigned")
                .setContentText(assignedRescuerName + " is handling the emergency for " + seniorName)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority - just informational
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setOngoing(false)
                .setSilent(true); // NO sound for informational notification
        
        android.app.Notification notification = builder.build();
        notificationManager.notify(requestCode, notification);
        
        Log.d(TAG, "ℹ️ [ASSIGNED_NOTIFICATION] Informational notification sent (no alarm)");
        Log.d(TAG, "ℹ️ [ASSIGNED_NOTIFICATION] Notification ID: " + requestCode);
    }
    
    private Uri getCustomAlarmSound() {
        try {
            // Try to use custom alarm sound
            Uri customSound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.emergency_alarm);
            Log.d(TAG, "🔊 Custom alarm sound URI: " + customSound.toString());
            Log.d(TAG, "🔊 Package name: " + getPackageName());
            Log.d(TAG, "🔊 Resource ID: " + R.raw.emergency_alarm);
            
            // Test if the resource exists and is accessible
            try {
                android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.emergency_alarm);
                if (afd != null) {
                    long length = afd.getLength();
                    Log.d(TAG, "✅ Custom alarm sound file exists and is accessible. Length: " + length + " bytes");
                    afd.close();
                } else {
                    Log.w(TAG, "⚠️ Custom alarm sound file descriptor is null");
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Custom alarm sound file not accessible: " + e.getMessage());
            }
            
            return customSound;
        } catch (Exception e) {
            // Fallback to system alarm sound if custom file doesn't exist
            Log.w(TAG, "❌ Custom alarm sound not found, using system alarm sound. Error: " + e.getMessage());
            Uri fallbackSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Log.d(TAG, "🔊 Using fallback alarm sound: " + fallbackSound.toString());
            return fallbackSound;
        }
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
