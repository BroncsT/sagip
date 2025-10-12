package com.example.sagip_prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
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
    private boolean isListening = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "EmergencySOSBackgroundService created");
        
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        // Get current user ID
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        }
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "EmergencySOSBackgroundService started");
        
        // Start as foreground service
        startForeground(FOREGROUND_SERVICE_ID, createForegroundNotification());
        
        // Start listening for emergency notifications
        if (userId != null && !isListening) {
            startEmergencySOSListener();
        }
        
        return START_STICKY; // Restart if killed by system
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "EmergencySOSBackgroundService destroyed");
        isListening = false;
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
                NotificationManager.IMPORTANCE_MAX
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
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_emergency_sos_service))
            .setContentText(getString(R.string.notification_ready_alerts))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setShowWhen(false)
            .build();
    }
    
    private void startEmergencySOSListener() {
        if (userId == null) {
            Log.w(TAG, "Cannot start emergency SOS listener - userId is null");
            return;
        }
        
        Log.d(TAG, "🚨 Starting emergency SOS listener for rescuer: " + userId);
        isListening = true;
        
        // Listen for emergency SOS notifications in real-time
        db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .document(userId)
          .collection("emergencyNotifications")
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(1)
          .addSnapshotListener((querySnapshot, error) -> {
              if (error != null) {
                  Log.e(TAG, "Error listening to emergency SOS notifications: " + error.getMessage(), error);
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
            String type = document.getString("type");
            String title = document.getString("title");
            String message = document.getString("message");
            String seniorName = document.getString("seniorName");
            String seniorPhone = document.getString("seniorPhone");
            String locationAddress = document.getString("locationAddress");
            String requestId = document.getString("requestId");
            Long timestamp = document.getLong("timestamp");
            Boolean isRead = document.getBoolean("isRead");
            
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] Document ID: " + document.getId());
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] Type: " + type);
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] IsRead: " + isRead);
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] RequestId: " + requestId);
            Log.d(TAG, "🔍 [HANDLE_NOTIFICATION] SeniorName: " + seniorName);
            
            // Only process unread emergency SOS notifications
            if ("EMERGENCY_SOS".equals(type) && (isRead == null || !isRead)) {
                Log.d(TAG, "🚨 Received emergency SOS notification: " + seniorName + " (Request ID: " + requestId + ")");
                
                // Show high-priority notification with alarm sound
                showEmergencySOSNotification(seniorName, seniorPhone, locationAddress, timestamp, requestId, document.getId());
                
                // Mark notification as read
                document.getReference().update("isRead", true);
                
                // Vibrate device
                vibrateDevice();
            } else if ("EMERGENCY_SOS".equals(type) && isRead != null && isRead) {
                Log.d(TAG, "🔇 Ignoring already read emergency SOS notification: " + seniorName + " (Request ID: " + requestId + ")");
            } else {
                Log.d(TAG, "🔇 Ignoring non-emergency notification: " + type + " (IsRead: " + isRead + ")");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling emergency SOS notification: " + e.getMessage(), e);
        }
    }
    
    private void showEmergencySOSNotification(String seniorName, String seniorPhone, String locationAddress, Long timestamp, String requestId, String notificationId) {
        Log.d(TAG, "🔔 Creating emergency SOS background notification for: " + seniorName + " (Request ID: " + requestId + ")");
        
        // Test sound playback directly
        testSoundPlayback();
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent for when notification is tapped - this will open the app even when closed
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.putExtra("emergency_sos_clicked", true);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("senior_phone", seniorPhone);
        notificationIntent.putExtra("location_address", locationAddress);
        notificationIntent.putExtra("request_id", requestId);
        notificationIntent.putExtra("from_emergency_notification", true);
        
        // Create pending intent
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                notificationId.hashCode(), 
                notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create call intent
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(android.net.Uri.parse("tel:" + seniorPhone));
        PendingIntent callPendingIntent = PendingIntent.getActivity(
                this,
                (notificationId + "_call").hashCode(),
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create navigation intent
        Intent navIntent = new Intent(Intent.ACTION_VIEW);
        navIntent.setData(android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + 
            android.net.Uri.encode("Angeles City, Pampanga") + "&travelmode=driving"));
        PendingIntent navPendingIntent = PendingIntent.getActivity(
                this,
                (notificationId + "_nav").hashCode(),
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
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(String.format(getString(R.string.notification_emergency_sos_title), seniorName))
                .setContentText(getString(R.string.notification_emergency_sos_text))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false) // Don't auto-cancel so user can tap it
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound()) // AudioAttributes are set on the channel, not here
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000) // Red light blinking
                .setFullScreenIntent(pendingIntent, true) // Show as full screen on lock screen
                .addAction(android.R.drawable.ic_menu_call, "📞 CALL", callPendingIntent)
                .addAction(android.R.drawable.ic_menu_directions, "🗺️ NAVIGATE", navPendingIntent)
                .setOngoing(true); // Make it persistent
        
        android.app.Notification notification = builder.build();
        notificationManager.notify(notificationId.hashCode(), notification);
        Log.d(TAG, "🔔 Emergency SOS notification sent for: " + seniorName);
        Log.d(TAG, "🔊 Notification sound URI: " + getCustomAlarmSound().toString());
        Log.d(TAG, "🔊 Notification flags: " + notification.flags);
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
            
            MediaPlayer mediaPlayer = MediaPlayer.create(this, soundUri);
            if (mediaPlayer != null) {
                Log.d(TAG, "🔊 MediaPlayer created successfully");
                mediaPlayer.setOnPreparedListener(mp -> {
                    Log.d(TAG, "🔊 MediaPlayer prepared, starting playback");
                    mp.start();
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "❌ MediaPlayer error: what=" + what + ", extra=" + extra);
                    mp.release();
                    return true;
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    Log.d(TAG, "🔊 Sound playback completed");
                    mp.release();
                });
            } else {
                Log.e(TAG, "❌ Failed to create MediaPlayer");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error testing sound playback: " + e.getMessage(), e);
        }
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
}
