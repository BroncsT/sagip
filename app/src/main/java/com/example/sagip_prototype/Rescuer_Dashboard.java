package com.example.sagip_prototype;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.sagip_prototype.ai.EmergencyRoomAI;
import com.example.sagip_prototype.models.Hospital;
import com.example.sagip_prototype.models.Emergency;
import com.example.sagip_prototype.models.RouteOption;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.sagip_prototype.models.Hospital;
import com.example.sagip_prototype.models.Emergency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;

public class Rescuer_Dashboard extends AppCompatActivity {

    private static final String TAG = "RescuerDashboard";
    
    // Emergency item class for FIFO queue management
    private static class EmergencyItem {
        String title;
        String message;
        String seniorName;
        String seniorPhone;
        String locationAddress;
        Double latitude;
        Double longitude;
        String helpRequestId;
        String emergencyId;
        long timestamp;
        int priority; // Higher number = higher priority
        int queuePosition; // Position in FIFO queue (1-based)
        long queueEntryTime; // When this item entered the queue
        double distance; // Distance from rescuer in km
        
        EmergencyItem(String title, String message, String seniorName, String seniorPhone,
                     String locationAddress, Double latitude, Double longitude, 
                     String helpRequestId, String emergencyId, int priority, int queuePosition, double distance) {
            this.title = title;
            this.message = message;
            this.seniorName = seniorName;
            this.seniorPhone = seniorPhone;
            this.locationAddress = locationAddress;
            this.latitude = latitude;
            this.longitude = longitude;
            this.helpRequestId = helpRequestId;
            this.emergencyId = emergencyId;
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
            this.queuePosition = queuePosition;
            this.queueEntryTime = System.currentTimeMillis();
            this.distance = distance;
        }
        
        // Get time spent in queue
        public long getTimeInQueue() {
            return System.currentTimeMillis() - queueEntryTime;
        }
        
        // Get formatted queue position
        public String getQueuePositionText() {
            return "#" + queuePosition;
        }
        
        // Get formatted distance text
        public String getDistanceText() {
            if (distance < 1.0) {
                return String.format("%.0f m", distance * 1000);
            } else {
                return String.format("%.1f km", distance);
            }
        }
    }
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_PHONE = "userPhone";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView brgyName;
    private TextView currentLocationText;
    private Button navigateToHospitalButton;
    private Button testNavigationButton;
    private long lastTapTime = 0;
    private String userType = "rescuer";
    private String userId;
    private SharedPreferences sharedPreferences;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private double currentLat = 0.0;
    private double currentLong = 0.0;

    // Emergency notification system variables
    private ListenerRegistration emergencyListener;
    private long lastLoginTime; // Track when rescuer logged in
    private AlertDialog currentEmergencyDialog; // Track current emergency popup
    
    // FIFO Emergency queue system for handling multiple simultaneous emergencies
    private Queue<EmergencyItem> emergencyQueue = new LinkedList<>(); // FIFO implementation
    private boolean isProcessingEmergency = false;
    private int totalActiveEmergencies = 0;
    private long queueStartTime = 0; // Track when first emergency was added

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_rescuer_dashboard);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Set login time to current time
        lastLoginTime = System.currentTimeMillis();

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        brgyName = findViewById(R.id.barangayStaffName);
        currentLocationText = findViewById(R.id.currentLocationValue);

        // Initialize navigate to hospital button
        navigateToHospitalButton = findViewById(R.id.navigateToHospitalButton);
        navigateToHospitalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                navigateToNearestHospital();
            }
        });

        // Initialize test navigation button
        testNavigationButton = findViewById(R.id.testNavigationButton);
        testNavigationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testNavigationToChristInYouHealeParish();
            }
        });
        
        // Test Navigation to Christ in You Heale Parish (long press on hospital button)
        navigateToHospitalButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // Test SOS Navigation
                Log.d("Rescuer_Dashboard", "Testing SOS Navigation");
                
                if (currentLat != 0.0 && currentLong != 0.0) {
                    // Use RescuerNavigationActivity to navigate to SOS location
                    openSOSNavigation();
                    Toast.makeText(Rescuer_Dashboard.this, "🚨 Opening SOS Navigation", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(Rescuer_Dashboard.this, "📍 Getting your current location first...", Toast.LENGTH_SHORT).show();
                    // Request location update and then start navigation
                    requestLocationAndStartSOSNavigation();
                }
                return true;
            }
        });

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize location components immediately in onCreate
        createLocationRequest();
        createLocationCallback();

        // Initialize emergency notification components
        initializeEmergencyNotificationComponents();
        
        // Initialize AI system
        initializeAISystem();

        // Setup bottom navigation
        setupBottomNavigation();

        // Check for location permissions
        checkLocationPermission();

        // Check authentication state
        checkAuthState();

        // Initialize FCM token for notifications
        initializeFCMToken();

        // Start rescuer background notification service (2-minute checks)
        // Notification services are already started in MainActivity (RescuerForegroundService, WebSocketNotificationService, etc.)
        // These foreground services will continue running when app is closed

        // Create notification channel
        createNotificationChannel();

        // Clear any old emergency notifications on startup
        clearOldEmergencyNotifications();
    }

    private void clearOldEmergencyNotifications() {
        // Clear any system notifications that might be from old sessions
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Handle notification click if this activity was opened from a notification
        handleNotificationClick();

        // Add safety check and ensure components are initialized
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }

        // Start emergency listener when activity resumes (only if not already started)
        if (emergencyListener == null) {
            Log.d(TAG, "Starting emergency listener in onResume()");
            startEmergencyListener();
        } else {
            Log.d(TAG, "Emergency listener already active, skipping start");
        }
        
        // Test emergency notification system
        testEmergencyNotificationSystem();

        // Clear any old notifications when app comes to foreground
        clearOldEmergencyNotifications();
        
        // Clear any emergency notifications when returning to dashboard
        clearAllEmergencyNotifications();
        
        // Send test notification to verify system is working (for debugging)
        if (userType != null && userType.equals("rescuer") && userId != null) {
            // Uncomment the line below to send a test notification
            // NativeNotificationSender.sendTestNotification(userId, userType);
            
            // Test alternative notification system (no FCM) - disabled for production
            // AlternativeNotificationManager.getInstance(this).sendTestNotification();
            // SimpleNotificationManager.getInstance(this).sendImmediateTestNotification();
            // NativeNotificationSender.sendHospitalUpdateNotificationToRescuers("Test Hospital", "Open", 5, 3);
        }
        
        // Check for new hospital status update notifications when returning to app
        // Only check if we haven't already checked recently to avoid unnecessary refreshes
        if (userType != null && userType.equals("rescuer") && userId != null) {
            long currentTime = System.currentTimeMillis();
            long lastCheckTime = sharedPreferences.getLong("lastNotificationCheck", 0);
            long timeSinceLastCheck = currentTime - lastCheckTime;
            
            // Only check notifications if it's been more than 30 seconds since last check
            if (timeSinceLastCheck > 30000) {
                Log.d(TAG, "=== CHECKING FOR REAL-TIME FCM NOTIFICATIONS IN ONRESUME ===");
                Log.d(TAG, "User Type: " + userType);
                Log.d(TAG, "User ID: " + userId);
                
                // Stop old background service since we now use dedicated rescuer foreground service
                // Check for FCM notifications locally since app is active
                HospitalStatusUpdateNotificationService.checkAndDisplayNotificationsForRescuer(this, userId);
                
                // Update last check time
                sharedPreferences.edit().putLong("lastNotificationCheck", currentTime).apply();
            } else {
                Log.d(TAG, "Skipping notification check - checked recently (" + (timeSinceLastCheck/1000) + " seconds ago)");
            }
        } else {
            Log.d(TAG, "Skipping notification check - User Type: " + userType + ", User ID: " + userId);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        
        // Stop emergency listener when app goes to background - EmergencyNotificationService will handle it
        if (emergencyListener != null) {
            Log.d(TAG, "🚨 Stopping emergency listener in activity - EmergencyNotificationService will handle background notifications");
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Foreground services will continue running to handle notifications when app is closed
        if (userType != null && userType.equals("rescuer") && userId != null) {
            Log.d(TAG, "App going to background - Foreground services will continue monitoring notifications");
            Log.d(TAG, "Active services: EmergencyNotificationService, RescuerForegroundService, WebSocketNotificationService, WorkManager, AlternativeNotificationManager");
        }
        
        // Clear tracking status when app is paused (optional - you might want to keep tracking active)
        // clearTrackingStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }

        // Clear any pending emergency alerts
        clearPendingEmergencyAlerts();
        
        // NOTE: Do NOT stop notification services here - they should continue running when app is closed
        // The foreground services (RescuerForegroundService, WebSocketNotificationService, etc.) 
        // started in MainActivity will continue running to handle notifications when app is closed
        
        // Clear tracking status when app is destroyed
        clearTrackingStatus();
    }

    private void clearPendingEmergencyAlerts() {
        // Clear any system notifications related to emergencies
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Cancel all emergency notifications
            notificationManager.cancelAll();
        }
        
        // Dismiss any active emergency popup dialog
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            currentEmergencyDialog = null;
            Log.d(TAG, "Dismissed emergency popup dialog");
        }
    }

    private void handleNotificationClick() {
        // Check if this activity was opened from a notification click
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("notification_clicked", false)) {
            String helpRequestId = intent.getStringExtra("helpRequestId");
            Log.d(TAG, "Activity opened from notification click for helpRequestId: " + helpRequestId);
            
            // Clear the specific notification
            if (helpRequestId != null) {
                clearEmergencyNotification(helpRequestId);
                Log.d(TAG, "Cleared notification for helpRequestId: " + helpRequestId);
            }
            
            // Show a toast to confirm
            Toast.makeText(this, getString(R.string.emergency_notification_cleared), Toast.LENGTH_SHORT).show();
            
            // Clear the intent extras to prevent repeated handling
            intent.removeExtra("notification_clicked");
            intent.removeExtra("helpRequestId");
        }
    }

    // Method to handle logout and clear emergency state
    private void handleLogout() {
        // Remove emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Stop background notification service
        // Stop all notification services when logging out
        stopAllNotificationServices();

        // Clear stored credentials
        clearStoredCredentials();

        // Navigate to login
        navigateToLogin();
    }

    // =============== EMERGENCY NOTIFICATION SYSTEM ===============

    /**
     * Test method to verify emergency notification system is working
     */
    private void testEmergencyNotificationSystem() {
        Log.d(TAG, "🧪 Testing emergency notification system...");
        
        // Check if emergency listener is active
        if (emergencyListener != null) {
            Log.d(TAG, "✅ Emergency listener is active");
        } else {
            Log.e(TAG, "❌ Emergency listener is NOT active");
        }
        
        // Check if user is logged in
        if (userId != null && !userId.isEmpty()) {
            Log.d(TAG, "✅ User ID available: " + userId);
        } else {
            Log.e(TAG, "❌ User ID not available");
        }
        
        // Check if user type is rescuer
        if (userType != null && userType.equals("rescuer")) {
            Log.d(TAG, "✅ User type is rescuer");
        } else {
            Log.e(TAG, "❌ User type is not rescuer: " + userType);
        }
        
        // Check location
        if (currentLat != 0.0 && currentLong != 0.0) {
            Log.d(TAG, "✅ Location available: " + currentLat + ", " + currentLong);
        } else {
            Log.w(TAG, "⚠️ Location not available yet");
        }
        
        Log.d(TAG, "🧪 Emergency notification system test completed");
        Log.d(TAG, "✅ OneSignal has been completely removed from the project");
        Log.d(TAG, "✅ Emergency alerts now use EmergencyNotificationService (background service)");
        Log.d(TAG, "✅ Hospital notifications use FCM (Firebase Cloud Messaging)");
        Log.d(TAG, "✅ EmergencyNotificationService runs continuously for real-time SOS alerts");
    }

    private void initializeEmergencyNotificationComponents() {
        Log.d(TAG, "🚨 Emergency notification components initialized");
        Log.d(TAG, "🚨 This system is INDEPENDENT from hospital notifications");
        Log.d(TAG, "🚨 Emergency alerts use Firestore real-time listeners");
        Log.d(TAG, "🚨 Hospital notifications use FCM (separate system)");
    }

    private void startEmergencyListener() {
        Log.d(TAG, "🚨 Starting emergency listener...");

        // Prevent duplicate listeners
        if (emergencyListener != null) {
            Log.w(TAG, "Emergency listener already exists, removing old one first");
            emergencyListener.remove();
            emergencyListener = null;
        }

        // Clean up old emergencies first (older than 1 hour)
        cleanupOldEmergencies();

        // Listen for new emergency notifications
        emergencyListener = db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "🚨 Emergency listener failed.", e);
                        return;
                    }

                    Log.d(TAG, "🚨 Emergency listener triggered - snapshots: " + (snapshots != null ? snapshots.size() : "null"));

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            Log.d(TAG, "🚨 Document change type: " + dc.getType() + " for document: " + dc.getDocument().getId());
                            
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // New emergency detected!
                                DocumentSnapshot emergency = dc.getDocument();
                                Log.d(TAG, "🚨 NEW EMERGENCY DETECTED: " + emergency.getId());
                                handleNewEmergency(emergency);
                            } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                                // Emergency was modified (likely responded to by another rescuer)
                                DocumentSnapshot emergency = dc.getDocument();
                                Boolean isActive = emergency.getBoolean("isActive");
                                Log.d(TAG, "🚨 Emergency modified - isActive: " + isActive);
                                
                                if (isActive != null && !isActive) {
                                    // Emergency was deactivated, clear the notification
                                    String helpRequestId = emergency.getString("helpRequestId");
                                    if (helpRequestId != null) {
                                        clearEmergencyNotification(helpRequestId);
                                        Log.d(TAG, "🚨 Emergency was responded to by another rescuer, clearing notification");
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "🚨 No active emergencies found");
                    }
                });

        Log.d(TAG, "🚨 Emergency listener started successfully");
    }

    private void cleanupOldEmergencies() {
        // Clean up emergencies older than 1 hour
        long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);

        db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereLessThan("timestamp", oneHourAgo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        // Mark old emergencies as inactive
                        document.getReference().update("isActive", false)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Cleaned up old emergency: " + document.getId()))
                                .addOnFailureListener(e -> Log.e(TAG, "Error cleaning up old emergency", e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error querying old emergencies", e));
    }

    private void handleNewEmergency(DocumentSnapshot emergency) {
        String title = emergency.getString("title");
        String message = emergency.getString("message");
        String seniorName = emergency.getString("seniorName");
        String seniorPhone = emergency.getString("seniorPhone");
        String locationAddress = emergency.getString("locationAddress");
        Double latitude = emergency.getDouble("latitude");
        Double longitude = emergency.getDouble("longitude");
        String helpRequestId = emergency.getString("helpRequestId");

        Log.d(TAG, "�� NEW EMERGENCY: " + seniorName + " at " + locationAddress);

        // Play notification sound
        playNotificationSound();

        // Check if this rescuer has already responded to this emergency
        String respondedBy = emergency.getString("respondedBy");
        if (respondedBy != null && respondedBy.equals(userId)) {
            Log.d(TAG, "Current rescuer already responded to this emergency, skipping notification for: " + helpRequestId);
            return;
        }

        // Check if emergency is within 5km radius
        if (latitude != null && longitude != null) {
            if (!isWithinRadius(latitude, longitude)) {
                Log.d(TAG, "Emergency is outside 5km radius, skipping notification for: " + helpRequestId);
                return;
            }
        } else {
            Log.w(TAG, "Emergency location data missing, allowing notification for: " + helpRequestId);
        }

        // Additional safety check: verify help request status in database
        if (helpRequestId != null && !helpRequestId.isEmpty()) {
            db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .document(helpRequestId)
                .get()
                .addOnSuccessListener(helpRequestDoc -> {
                    if (helpRequestDoc.exists()) {
                        String status = helpRequestDoc.getString("status");
                        String helpRequestRespondedBy = helpRequestDoc.getString("respondedBy");
                        
                        // If already responded by this rescuer, skip notification
                        if ("responded".equals(status) && userId.equals(helpRequestRespondedBy)) {
                            Log.d(TAG, "Help request already responded by current rescuer, skipping notification for: " + helpRequestId);
                            return;
                        }
                        
                        // If responded by someone else, also skip (other rescuer is handling it)
                        if ("responded".equals(status) && helpRequestRespondedBy != null && !userId.equals(helpRequestRespondedBy)) {
                            Log.d(TAG, "Help request already responded by another rescuer, skipping notification for: " + helpRequestId);
                            return;
                        }
                        
                        // If we reach here, it's safe to show the notification
                        showEmergencyNotification(emergency, title, message, seniorName, seniorPhone, 
                                locationAddress, latitude, longitude, helpRequestId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking help request status, showing notification anyway", e);
                    // If we can't check the status, show the notification to be safe
                    showEmergencyNotification(emergency, title, message, seniorName, seniorPhone, 
                            locationAddress, latitude, longitude, helpRequestId);
                });
            return; // Exit early since we're handling the notification asynchronously
        }

        // If no helpRequestId, show notification directly (fallback)
        showEmergencyNotification(emergency, title, message, seniorName, seniorPhone, 
                locationAddress, latitude, longitude, helpRequestId);
    }

    private void showEmergencyNotification(DocumentSnapshot emergency, String title, String message, 
            String seniorName, String seniorPhone, String locationAddress, Double latitude, 
            Double longitude, String helpRequestId) {
        
        // FIFO: Add emergency to the END of the queue (FIFO - First In, First Out)
        int queuePosition = totalActiveEmergencies + 1;
        double distance = calculateDistance(currentLat, currentLong, latitude, longitude);
        EmergencyItem emergencyItem = new EmergencyItem(title, message, seniorName, seniorPhone,
                locationAddress, latitude, longitude, helpRequestId, emergency.getId(), 1, queuePosition, distance);
        
        // FIFO operation: offer() adds to the end of the queue
        emergencyQueue.offer(emergencyItem);
        totalActiveEmergencies++;
        
        // Track queue start time for first emergency
        if (queueStartTime == 0) {
            queueStartTime = System.currentTimeMillis();
        }
        
        Log.d(TAG, "FIFO: Emergency #" + queuePosition + " added to queue. Total active emergencies: " + totalActiveEmergencies);
        
        // Play sound for new emergency
        playNotificationSound();
        
        // Show system notification with FIFO position
        String fifoMessage = message + " - " + locationAddress + " (Queue #" + queuePosition + ")";
        showSystemNotification(title, fifoMessage, helpRequestId);
        
        // Process the queue using FIFO
        processEmergencyQueueFIFO();
    }

    private void processEmergencyQueueFIFO() {
        // If already processing an emergency or queue is empty, return
        if (isProcessingEmergency || emergencyQueue.isEmpty()) {
            return;
        }
        
        // FIFO operation: poll() removes and returns the FIRST item from the queue
        EmergencyItem nextEmergency = emergencyQueue.poll();
        if (nextEmergency != null) {
            isProcessingEmergency = true;
            
            Log.d(TAG, "FIFO: Processing emergency #" + nextEmergency.queuePosition + 
                      " - " + nextEmergency.seniorName + " (Time in queue: " + 
                      (nextEmergency.getTimeInQueue() / 1000) + "s)");
            
            // Show emergency alert dialog with FIFO information
            showEmergencyAlertFIFO(nextEmergency);
        }
    }
    
    // Legacy method for backward compatibility
    private void processEmergencyQueue() {
        processEmergencyQueueFIFO();
    }


    private void playNotificationSound() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), notification);
            if (mp != null) {
                mp.start();
                // Stop sound after 5 seconds
                mp.setOnCompletionListener(MediaPlayer::release);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing notification sound", e);
        }
    }

    private void showEmergencyAlertFIFO(EmergencyItem emergency) {
        // Update title to show FIFO queue status
        String queueInfo = totalActiveEmergencies > 1 ? 
            " (FIFO Queue: #" + emergency.queuePosition + " of " + totalActiveEmergencies + ")" : 
            " (FIFO Queue: #" + emergency.queuePosition + ")";
        String fullTitle = emergency.title + queueInfo;
        
        String fullMessage = emergency.message + "\n\n" +
                "Senior: " + emergency.seniorName + "\n" +
                "Phone: " + (emergency.seniorPhone != null && !emergency.seniorPhone.isEmpty() ? emergency.seniorPhone : "Not provided") + "\n" +
                "Location: " + emergency.locationAddress + "\n" +
                "📍 Distance: " + emergency.getDistanceText() + "\n" +
                "⏰ Time in Queue: " + getTimeInQueueText(emergency.getTimeInQueue());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(fullTitle);
        builder.setMessage(fullMessage);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        // RESPOND button - most important action
        builder.setPositiveButton("🚑 RESPOND NOW", (dialog, which) -> {
            clearEmergencyNotification(emergency.helpRequestId);
            respondToEmergency(emergency.helpRequestId, emergency.emergencyId);
            
            // Show AI hospital selection
            selectOptimalHospitalWithAI(emergency.helpRequestId, "general", "high", 
                                      emergency.latitude, emergency.longitude);
            
            dialog.dismiss();
            handleEmergencyDialogDismissed();
        });

        // GOOGLE NAVIGATION button - opens embedded Google Navigation
        builder.setNeutralButton("🗺️ GOOGLE NAV", (dialog, which) -> {
            clearEmergencyNotification(emergency.helpRequestId);
            Log.d("Rescuer_Dashboard", "Opening Google Navigation with data: " + emergency.seniorName + " at " + emergency.locationAddress);
            openGoogleNavigation(emergency.latitude, emergency.longitude, emergency.locationAddress, 
                    emergency.seniorName, emergency.seniorPhone, emergency.helpRequestId);
            dialog.dismiss();
            handleEmergencyDialogDismissed();
        });

        // Add buttons based on conditions
        if (totalActiveEmergencies > 1) {
            // Multiple emergencies - show SKIP and VIEW ALL buttons
            builder.setNeutralButton("⏭️ SKIP (FIFO)", (dialog, which) -> {
                dialog.dismiss();
                handleEmergencyDialogDismissed();
            });
            
            builder.setNegativeButton("📋 VIEW EMERGENCY LIST", (dialog, which) -> {
                openEmergencyListActivity();
                dialog.dismiss();
            });
        } else if (emergency.seniorPhone != null && !emergency.seniorPhone.isEmpty()) {
            // Single emergency with phone - show CALL button
            builder.setNegativeButton("📞 CALL", (dialog, which) -> {
                clearEmergencyNotification(emergency.helpRequestId);
                callSenior(emergency.seniorPhone);
                dialog.dismiss();
                handleEmergencyDialogDismissed();
            });
        }

        // Make dialog not cancelable so rescuer must choose an action
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Store reference to current emergency dialog
        currentEmergencyDialog = dialog;

        // Make RESPOND button red and larger
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
        }
    }

    private void showEmergencyAlert(String title, String message, String seniorName,
                                    String seniorPhone, String locationAddress, Double latitude,
                                    Double longitude, String helpRequestId, String emergencyId) {

        // Update title to show queue status
        String queueInfo = totalActiveEmergencies > 1 ? " (" + totalActiveEmergencies + " emergencies)" : "";
        String fullTitle = title + queueInfo;

        String fullMessage = message + "\n\n" +
                "Senior: " + seniorName + "\n" +
                "Phone: " + (seniorPhone != null && !seniorPhone.isEmpty() ? seniorPhone : "Not provided") + "\n" +
                "Location: " + locationAddress;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(fullTitle);
        builder.setMessage(fullMessage);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        // RESPOND button - most important action
        builder.setPositiveButton("🚑 RESPOND NOW", (dialog, which) -> {
            clearEmergencyNotification(helpRequestId);
            respondToEmergency(helpRequestId, emergencyId);
            openGoogleNavigation(latitude, longitude, locationAddress, seniorName, seniorPhone, helpRequestId);
            dialog.dismiss();
            handleEmergencyDialogDismissed();
        });

        // GOOGLE NAVIGATION button - opens embedded Google Navigation
        builder.setNeutralButton("🗺️ GOOGLE NAV", (dialog, which) -> {
            clearEmergencyNotification(helpRequestId);
            Log.d("Rescuer_Dashboard", "Opening Google Navigation with data: " + seniorName + " at " + locationAddress);
            openGoogleNavigation(latitude, longitude, locationAddress, seniorName, seniorPhone, helpRequestId);
            dialog.dismiss();
            handleEmergencyDialogDismissed();
        });

        // Add buttons based on conditions
        if (totalActiveEmergencies > 1) {
            // Multiple emergencies - show SKIP and VIEW ALL buttons
            builder.setNeutralButton("⏭️ SKIP", (dialog, which) -> {
                dialog.dismiss();
                handleEmergencyDialogDismissed();
            });
            
            builder.setNegativeButton("📋 VIEW ALL", (dialog, which) -> {
                showEmergencySummary();
                dialog.dismiss();
            });
        } else if (seniorPhone != null && !seniorPhone.isEmpty()) {
            // Single emergency with phone - show CALL button
            builder.setNegativeButton("📞 CALL", (dialog, which) -> {
                clearEmergencyNotification(helpRequestId);
                callSenior(seniorPhone);
                dialog.dismiss();
                handleEmergencyDialogDismissed();
            });
        }

        // Make dialog not cancelable so rescuer must choose an action
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Store reference to current emergency dialog
        currentEmergencyDialog = dialog;

        // Make RESPOND button red and larger
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
        }
    }

    private void handleEmergencyDialogDismissed() {
        // Mark that we're no longer processing an emergency
        isProcessingEmergency = false;
        totalActiveEmergencies--;
        
        // Clear the current dialog reference
        currentEmergencyDialog = null;
        
        Log.d(TAG, "FIFO: Emergency dialog dismissed. Remaining emergencies: " + totalActiveEmergencies);
        
        // Reset queue start time if queue is empty
        if (emergencyQueue.isEmpty()) {
            queueStartTime = 0;
            Log.d(TAG, "FIFO: Queue is now empty, resetting queue start time");
        }
        
        // Process the next emergency in FIFO queue if any
        if (!emergencyQueue.isEmpty()) {
            // Small delay to allow UI to update
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                processEmergencyQueueFIFO();
            }, 500);
        }
    }
    
    // Method to clear the entire FIFO queue (useful for testing or emergency situations)
    private void clearFIFOQueue() {
        emergencyQueue.clear();
        totalActiveEmergencies = 0;
        queueStartTime = 0;
        isProcessingEmergency = false;
        currentEmergencyDialog = null;
        Log.d(TAG, "FIFO: Queue cleared completely");
    }
    
    // Method to get FIFO queue statistics
    private String getFIFOQueueStats() {
        if (emergencyQueue.isEmpty()) {
            return "FIFO Queue: Empty";
        }
        
        long totalQueueTime = System.currentTimeMillis() - queueStartTime;
        return String.format("FIFO Queue: %d emergencies, %s total time", 
                totalActiveEmergencies, getTimeInQueueText(totalQueueTime));
    }
    
    // Calculate distance between two coordinates using Haversine formula
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c; // convert to kilometers
        
        return distance;
    }
    
    // Check if emergency is within 5km radius
    private boolean isWithinRadius(double emergencyLat, double emergencyLon) {
        if (currentLat == 0.0 || currentLong == 0.0) {
            Log.w(TAG, "Rescuer location not available, allowing emergency notification");
            return true; // Allow notification if rescuer location is not available
        }
        
        double distance = calculateDistance(currentLat, currentLong, emergencyLat, emergencyLon);
        boolean withinRadius = distance <= 5.0; // 5km radius
        
        Log.d(TAG, String.format("Distance to emergency: %.2f km, Within 5km radius: %s", 
                distance, withinRadius));
        
        return withinRadius;
    }
    
    // Get formatted distance text
    private String getDistanceText(double emergencyLat, double emergencyLon) {
        if (currentLat == 0.0 || currentLong == 0.0) {
            return "Distance: Unknown";
        }
        
        double distance = calculateDistance(currentLat, currentLong, emergencyLat, emergencyLon);
        if (distance < 1.0) {
            return String.format("Distance: %.0f m", distance * 1000);
        } else {
            return String.format("Distance: %.1f km", distance);
        }
    }

    private void openEmergencyListActivity() {
        Intent intent = new Intent(this, EmergencyListActivity.class);
        startActivity(intent);
    }
    
    // AI System Integration
    private EmergencyRoomAI emergencyRoomAI;
    private AlertDialog loadingDialog;
    
    private void initializeAISystem() {
        emergencyRoomAI = new EmergencyRoomAI(db);
    }
    
    private void selectOptimalHospitalWithAI(String helpRequestId, String emergencyType, 
                                           String severity, double seniorLat, double seniorLon) {
        
        if (emergencyRoomAI == null) {
            initializeAISystem();
        }
        
        // Show loading dialog
        showLoadingDialog("Finding optimal hospital...");
        
        // Create emergency object for AI
        Emergency emergency = new Emergency();
        emergency.helpRequestId = helpRequestId;
        emergency.emergencyType = emergencyType;
        emergency.severity = severity;
        emergency.location = new com.google.firebase.firestore.GeoPoint(seniorLat, seniorLon);
        emergency.timestamp = System.currentTimeMillis();
        
        // Get AI recommendation (async)
        emergencyRoomAI.selectOptimalHospital(emergency, currentLat, currentLong, 
            new EmergencyRoomAI.HospitalSelectionCallback() {
                @Override
                public void onResult(EmergencyRoomAI.AIRecommendationResult result) {
                    // Hide loading dialog
                    hideLoadingDialog();
                    
                    // Show AI recommendation
                    showAIRecommendation(result, helpRequestId);
                }
            });
    }
    
    private void showAIRecommendation(EmergencyRoomAI.AIRecommendationResult result, String helpRequestId) {
        
        if (result.recommendedHospital == null) {
            // Show better error dialog with options
            showNoHospitalFoundDialog(helpRequestId, result.message);
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append("🤖 AI RECOMMENDATION\n\n");
        message.append("🏥 Hospital: ").append(result.recommendedHospital.name).append("\n");
        message.append("📍 Distance: ").append(String.format("%.1f km", result.recommendedHospital.distanceFromSenior)).append("\n");
        message.append("⏱️ Travel Time: ").append(result.optimalRoute != null ? result.optimalRoute.getDurationInMinutes() : "Unknown").append(" min\n");
        message.append("🎯 Confidence: ").append(String.format("%.1f%%", result.confidenceScore * 100)).append("\n\n");
        
        // Add confidence indicator
        if (result.isHighConfidence()) {
            message.append("✅ High confidence recommendation");
        } else if (result.isMediumConfidence()) {
            message.append("⚠️ Medium confidence recommendation");
        } else {
            message.append("❌ Low confidence - consider alternatives");
        }
        
        // Show alternatives if available
        if (!result.alternativeHospitals.isEmpty()) {
            message.append("\n\n🔄 Alternative Options:\n");
            for (int i = 0; i < Math.min(2, result.alternativeHospitals.size()); i++) {
                Hospital alt = result.alternativeHospitals.get(i);
                message.append("• ").append(alt.name).append(" (").append(String.format("%.1f km", alt.distanceFromSenior)).append(")\n");
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🤖 AI Hospital Recommendation");
        builder.setMessage(message.toString());
        builder.setIcon(android.R.drawable.ic_dialog_info);
        
        // Accept AI recommendation
        builder.setPositiveButton("✅ ACCEPT AI RECOMMENDATION", (dialog, which) -> {
            navigateToRecommendedHospital(result, helpRequestId);
            dialog.dismiss();
        });
        
        // Show alternatives
        if (!result.alternativeHospitals.isEmpty()) {
            builder.setNeutralButton("🔄 VIEW ALTERNATIVES", (dialog, which) -> {
                showAlternativeHospitals(result.alternativeHospitals, helpRequestId);
                dialog.dismiss();
            });
        }
        
        // Manual selection
        builder.setNegativeButton("👤 SELECT MANUALLY", (dialog, which) -> {
            // Open hospital list for manual selection
            openHospitalSelection(helpRequestId);
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void navigateToRecommendedHospital(EmergencyRoomAI.AIRecommendationResult result, String helpRequestId) {
        
        Hospital hospital = result.recommendedHospital;
        RouteOption route = result.optimalRoute;
        
        // Update help request with selected hospital
        Map<String, Object> updates = new HashMap<>();
        updates.put("selectedHospitalId", hospital.hospitalId);
        updates.put("selectedHospitalName", hospital.name);
        updates.put("aiRecommendation", true);
        updates.put("aiConfidenceScore", result.confidenceScore);
        updates.put("selectedAt", System.currentTimeMillis());
        
        db.collection("Sagip")
          .document("helpRequests")
          .collection("activeRequests")
          .document(helpRequestId)
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              // Send notification to hospital staff
              sendHospitalNotification(hospital, helpRequestId, result);
              
              // Navigate to hospital
              openGoogleNavigation(
                  hospital.location.getLatitude(), 
                  hospital.location.getLongitude(),
                  hospital.address,
                  "AI Recommended: " + hospital.name,
                  hospital.phone,
                  helpRequestId
              );
              
              // Show success message
              Toast.makeText(this, "Navigating to AI recommended hospital: " + hospital.name, Toast.LENGTH_LONG).show();
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to update help request with hospital selection", e);
              Toast.makeText(this, "Failed to save hospital selection", Toast.LENGTH_SHORT).show();
          });
    }
    
    private void showAlternativeHospitals(List<Hospital> alternatives, String helpRequestId) {
        
        StringBuilder message = new StringBuilder();
        message.append("🔄 ALTERNATIVE HOSPITALS\n\n");
        
        for (int i = 0; i < alternatives.size(); i++) {
            Hospital hospital = alternatives.get(i);
            message.append((i + 1)).append(". ").append(hospital.name).append("\n");
            message.append("   📍 ").append(String.format("%.1f km", hospital.distanceFromSenior)).append("\n");
            message.append("   🏥 Beds: ").append(hospital.availableBeds).append("/").append(hospital.totalBeds).append("\n");
            message.append("   ⏱️ Response: ").append(String.format("%.1f min", hospital.avgResponseTime)).append("\n\n");
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Alternative Hospital Options");
        builder.setMessage(message.toString());
        builder.setIcon(android.R.drawable.ic_dialog_info);
        
        // Create buttons for each alternative
        for (int i = 0; i < Math.min(3, alternatives.size()); i++) {
            final Hospital hospital = alternatives.get(i);
            final int index = i;
            
            builder.setPositiveButton("Select #" + (i + 1), (dialog, which) -> {
                // Send notification to selected alternative hospital
                sendAlternativeHospitalNotification(hospital, helpRequestId);
                
                // Navigate to selected alternative
                openGoogleNavigation(
                    hospital.location.getLatitude(),
                    hospital.location.getLongitude(),
                    hospital.address,
                    "Alternative: " + hospital.name,
                    hospital.phone,
                    helpRequestId
                );
            });
        }
        
        builder.setNegativeButton("❌ Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void openHospitalSelection(String helpRequestId) {
        // Open hospital list activity for manual selection
        Intent intent = new Intent(this, Hospital_List.class);
        intent.putExtra("helpRequestId", helpRequestId);
        intent.putExtra("selectionMode", "emergency");
        startActivity(intent);
    }
    
    // Hospital Notification System
    private void sendHospitalNotification(Hospital hospital, String helpRequestId, 
                                        EmergencyRoomAI.AIRecommendationResult aiResult) {
        
        // Get emergency details from help request
        db.collection("Sagip")
          .document("helpRequests")
          .collection("activeRequests")
          .document(helpRequestId)
          .get()
          .addOnSuccessListener(documentSnapshot -> {
              if (documentSnapshot.exists()) {
                  String seniorName = documentSnapshot.getString("seniorName");
                  String seniorPhone = documentSnapshot.getString("seniorPhone");
                  String locationAddress = documentSnapshot.getString("locationAddress");
                  String emergencyType = documentSnapshot.getString("emergencyType");
                  String severity = documentSnapshot.getString("severity");
                  String rescuerName = documentSnapshot.getString("rescuerName");
                  String rescuerPhone = documentSnapshot.getString("rescuerPhone");
                  
                  // Create hospital notification
                  createHospitalNotification(hospital, helpRequestId, seniorName, seniorPhone, 
                                           locationAddress, emergencyType, severity, rescuerName, 
                                           rescuerPhone, aiResult);
              }
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to get emergency details for hospital notification", e);
          });
    }
    
    private void createHospitalNotification(Hospital hospital, String helpRequestId, String seniorName, 
                                         String seniorPhone, String locationAddress, String emergencyType, 
                                         String severity, String rescuerName, String rescuerPhone,
                                         EmergencyRoomAI.AIRecommendationResult aiResult) {
        
        // Create notification data for hospital
        Map<String, Object> hospitalNotification = new HashMap<>();
        hospitalNotification.put("notificationId", "hospital_" + helpRequestId + "_" + System.currentTimeMillis());
        hospitalNotification.put("helpRequestId", helpRequestId);
        hospitalNotification.put("hospitalId", hospital.hospitalId);
        hospitalNotification.put("hospitalName", hospital.name);
        hospitalNotification.put("notificationType", "incoming_emergency");
        hospitalNotification.put("timestamp", System.currentTimeMillis());
        hospitalNotification.put("status", "pending");
        
        // Emergency details
        hospitalNotification.put("seniorName", seniorName);
        hospitalNotification.put("seniorPhone", seniorPhone);
        hospitalNotification.put("locationAddress", locationAddress);
        hospitalNotification.put("emergencyType", emergencyType != null ? emergencyType : "General Emergency");
        hospitalNotification.put("severity", severity != null ? severity : "High");
        
        // Rescuer details
        hospitalNotification.put("rescuerName", rescuerName);
        hospitalNotification.put("rescuerPhone", rescuerPhone);
        hospitalNotification.put("rescuerId", userId);
        
        // AI recommendation details
        hospitalNotification.put("aiRecommended", true);
        hospitalNotification.put("aiConfidenceScore", aiResult.confidenceScore);
        hospitalNotification.put("estimatedArrivalTime", aiResult.optimalRoute != null ? 
                               aiResult.optimalRoute.getDurationInMinutes() : 0);
        hospitalNotification.put("distanceFromHospital", hospital.distanceFromSenior);
        
        // Priority based on severity and AI confidence
        int priority = calculateHospitalNotificationPriority(severity, aiResult.confidenceScore);
        hospitalNotification.put("priority", priority);
        
        // Save notification to Firestore
        db.collection("Sagip")
          .document("hospitalNotifications")
          .collection("pendingNotifications")
          .add(hospitalNotification)
          .addOnSuccessListener(documentReference -> {
              Log.d(TAG, "Hospital notification sent successfully to " + hospital.name);
              
              // Send push notification to hospital staff
              sendPushNotificationToHospital(hospital, helpRequestId, seniorName, emergencyType, severity);
              
              // Show confirmation to rescuer
              Toast.makeText(this, "🏥 Hospital " + hospital.name + " has been notified!", Toast.LENGTH_SHORT).show();
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to send hospital notification", e);
              Toast.makeText(this, "Failed to notify hospital", Toast.LENGTH_SHORT).show();
          });
    }
    
    private int calculateHospitalNotificationPriority(String severity, double aiConfidence) {
        int basePriority = 1; // Default priority
        
        // Severity multiplier
        if (severity != null) {
            switch (severity.toLowerCase()) {
                case "critical":
                    basePriority = 4;
                    break;
                case "high":
                    basePriority = 3;
                    break;
                case "medium":
                    basePriority = 2;
                    break;
                case "low":
                    basePriority = 1;
                    break;
            }
        }
        
        // AI confidence bonus
        if (aiConfidence >= 0.9) {
            basePriority += 1; // High confidence gets priority boost
        }
        
        return Math.min(5, basePriority); // Cap at priority 5
    }
    
    private void sendPushNotificationToHospital(Hospital hospital, String helpRequestId, 
                                             String seniorName, String emergencyType, String severity) {
        
        // Create push notification payload
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("title", "🚨 INCOMING EMERGENCY");
        notificationData.put("body", "Emergency from " + seniorName + " - " + 
                           (emergencyType != null ? emergencyType : "General Emergency"));
        notificationData.put("helpRequestId", helpRequestId);
        notificationData.put("hospitalId", hospital.hospitalId);
        notificationData.put("notificationType", "incoming_emergency");
        notificationData.put("severity", severity);
        notificationData.put("timestamp", System.currentTimeMillis());
        
        // Send to hospital staff devices
        db.collection("Sagip")
          .document("hospitals")
          .collection("registeredHospitals")
          .document(hospital.hospitalId)
          .collection("staff")
          .whereEqualTo("isActive", true)
          .whereEqualTo("notificationEnabled", true)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  String staffToken = document.getString("fcmToken");
                  String staffName = document.getString("name");
                  String staffRole = document.getString("role");
                  
                  if (staffToken != null) {
                      // Send FCM notification (simplified for now)
                      Log.d(TAG, "Would send FCM notification to " + staffName + " (" + staffRole + ")");
                  }
              }
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to get hospital staff for notifications", e);
          });
    }
    
    
    private void sendAlternativeHospitalNotification(Hospital hospital, String helpRequestId) {
        
        // Get emergency details from help request
        db.collection("Sagip")
          .document("helpRequests")
          .collection("activeRequests")
          .document(helpRequestId)
          .get()
          .addOnSuccessListener(documentSnapshot -> {
              if (documentSnapshot.exists()) {
                  String seniorName = documentSnapshot.getString("seniorName");
                  String seniorPhone = documentSnapshot.getString("seniorPhone");
                  String locationAddress = documentSnapshot.getString("locationAddress");
                  String emergencyType = documentSnapshot.getString("emergencyType");
                  String severity = documentSnapshot.getString("severity");
                  String rescuerName = documentSnapshot.getString("rescuerName");
                  String rescuerPhone = documentSnapshot.getString("rescuerPhone");
                  
                  // Create alternative hospital notification
                  createAlternativeHospitalNotification(hospital, helpRequestId, seniorName, seniorPhone, 
                                                      locationAddress, emergencyType, severity, rescuerName, rescuerPhone);
              }
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to get emergency details for alternative hospital notification", e);
          });
    }
    
    private void createAlternativeHospitalNotification(Hospital hospital, String helpRequestId, String seniorName, 
                                                     String seniorPhone, String locationAddress, String emergencyType, 
                                                     String severity, String rescuerName, String rescuerPhone) {
        
        // Create notification data for alternative hospital
        Map<String, Object> hospitalNotification = new HashMap<>();
        hospitalNotification.put("notificationId", "alt_hospital_" + helpRequestId + "_" + System.currentTimeMillis());
        hospitalNotification.put("helpRequestId", helpRequestId);
        hospitalNotification.put("hospitalId", hospital.hospitalId);
        hospitalNotification.put("hospitalName", hospital.name);
        hospitalNotification.put("notificationType", "incoming_emergency_alternative");
        hospitalNotification.put("timestamp", System.currentTimeMillis());
        hospitalNotification.put("status", "pending");
        hospitalNotification.put("isAlternative", true);
        
        // Emergency details
        hospitalNotification.put("seniorName", seniorName);
        hospitalNotification.put("seniorPhone", seniorPhone);
        hospitalNotification.put("locationAddress", locationAddress);
        hospitalNotification.put("emergencyType", emergencyType != null ? emergencyType : "General Emergency");
        hospitalNotification.put("severity", severity != null ? severity : "High");
        
        // Rescuer details
        hospitalNotification.put("rescuerName", rescuerName);
        hospitalNotification.put("rescuerPhone", rescuerPhone);
        hospitalNotification.put("rescuerId", userId);
        
        // Alternative selection details
        hospitalNotification.put("aiRecommended", false);
        hospitalNotification.put("isAlternative", true);
        hospitalNotification.put("distanceFromHospital", hospital.distanceFromSenior);
        
        // Priority based on severity
        int priority = calculateHospitalNotificationPriority(severity, 0.7); // Medium confidence for alternatives
        hospitalNotification.put("priority", priority);
        
        // Save notification to Firestore
        db.collection("Sagip")
          .document("hospitalNotifications")
          .collection("pendingNotifications")
          .add(hospitalNotification)
          .addOnSuccessListener(documentReference -> {
              Log.d(TAG, "Alternative hospital notification sent successfully to " + hospital.name);
              
              // Send push notification to hospital staff
              sendPushNotificationToHospital(hospital, helpRequestId, seniorName, emergencyType, severity);
              
              // Show confirmation to rescuer
              Toast.makeText(this, "🏥 Alternative hospital " + hospital.name + " has been notified!", Toast.LENGTH_SHORT).show();
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to send alternative hospital notification", e);
              Toast.makeText(this, "Failed to notify alternative hospital", Toast.LENGTH_SHORT).show();
          });
    }

    private String getTimeInQueueText(long timeInQueueMs) {
        long seconds = timeInQueueMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return hours + " hour" + (hours != 1 ? "s" : "") + " " + (minutes % 60) + " min";
        }
    }

    private void showEmergencySummaryFIFO() {
        if (emergencyQueue.isEmpty()) {
            return;
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("🔄 FIFO EMERGENCY QUEUE (").append(totalActiveEmergencies).append(")\n");
        summary.append("First In, First Out Processing\n\n");
        
        int index = 1;
        for (EmergencyItem emergency : emergencyQueue) {
            summary.append("📍 POSITION #").append(emergency.queuePosition)
                   .append(" - ").append(emergency.seniorName)
                   .append("\n   📍 ").append(emergency.locationAddress)
                   .append("\n   📏 Distance: ").append(emergency.getDistanceText())
                   .append("\n   📞 ").append(emergency.seniorPhone != null ? emergency.seniorPhone : "No phone")
                   .append("\n   ⏰ In Queue: ").append(getTimeInQueueText(emergency.getTimeInQueue()))
                   .append("\n   🕐 Reported: ").append(getTimeAgo(emergency.timestamp))
                   .append("\n\n");
            index++;
        }
        
        // Add FIFO explanation
        summary.append("📋 FIFO ALGORITHM:\n");
        summary.append("• First emergency reported = First to be processed\n");
        summary.append("• Queue position determines processing order\n");
        summary.append("• No emergency can 'cut in line'\n");
        summary.append("• Fair and predictable processing");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔄 FIFO Emergency Queue");
        builder.setMessage(summary.toString());
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        builder.setPositiveButton("🚑 PROCESS FIRST", (dialog, which) -> {
            // Process the first emergency in FIFO queue
            processEmergencyQueueFIFO();
        });
        
        builder.setNegativeButton("❌ CLOSE", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showEmergencySummary() {
        if (emergencyQueue.isEmpty()) {
            return;
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("🚨 ACTIVE EMERGENCIES (").append(totalActiveEmergencies).append(")\n\n");
        
        int index = 1;
        for (EmergencyItem emergency : emergencyQueue) {
            summary.append(index).append(". ").append(emergency.seniorName)
                   .append(" - ").append(emergency.locationAddress)
                   .append("\n   📞 ").append(emergency.seniorPhone != null ? emergency.seniorPhone : "No phone")
                   .append("\n   ⏰ ").append(getTimeAgo(emergency.timestamp))
                   .append("\n\n");
            index++;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📋 Emergency Summary");
        builder.setMessage(summary.toString());
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        builder.setPositiveButton("🚑 RESPOND TO FIRST", (dialog, which) -> {
            // Process the first emergency in queue
            processEmergencyQueue();
        });
        
        builder.setNegativeButton("❌ CLOSE", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        
        if (minutes < 1) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else {
            long hours = minutes / 60;
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        }
    }

    private void respondToEmergency(String helpRequestId, String emergencyId) {
        // Clear the system notification immediately
        clearEmergencyNotification(helpRequestId);
        
        // Update help request status
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "responded");
        updates.put("respondedBy", userId);
        updates.put("respondedAt", System.currentTimeMillis());
        updates.put("rescuerLocation", new GeoPoint(currentLat, currentLong));

        db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .document(helpRequestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, getString(R.string.response_recorded), Toast.LENGTH_LONG).show();

                    // Also update the rescuer's own document with current location for tracking
                    updateRescuerLocationForTracking();

                    // Deactivate the emergency notification so other rescuers know it's handled
                    db.collection("Sagip")
                            .document("emergencyNotifications")
                            .collection("activeEmergencies")
                            .document(emergencyId)
                            .update("isActive", false,
                                    "respondedBy", userId,
                                    "respondedAt", System.currentTimeMillis(),
                                    "rescuerLocation", new GeoPoint(currentLat, currentLong))
                            .addOnSuccessListener(aVoid1 -> {
                                Log.d(TAG, "Emergency notification deactivated");
                                // Also update the timestamp to prevent it from showing again
                                db.collection("Sagip")
                                        .document("emergencyNotifications")
                                        .collection("activeEmergencies")
                                        .document(emergencyId)
                                        .update("timestamp", System.currentTimeMillis() - (2 * 60 * 60 * 1000)) // Set to 2 hours ago
                                        .addOnSuccessListener(aVoid2 -> Log.d(TAG, "Emergency timestamp updated to prevent re-showing"));
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating emergency response", e);
                    Toast.makeText(this, getString(R.string.error_recording_response), Toast.LENGTH_SHORT).show();
                });
    }

    // Method to clear emergency notification
    private void clearEmergencyNotification(String helpRequestId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Cancel the specific emergency notification
            notificationManager.cancel(helpRequestId.hashCode());
            Log.d(TAG, "Cleared emergency notification for: " + helpRequestId);
        }
    }

    // Method to clear all emergency notifications
    private void clearAllEmergencyNotifications() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Cancel all emergency notifications
            notificationManager.cancelAll();
            Log.d(TAG, "Cleared all emergency notifications");
        }
        
        // Dismiss any active emergency popup dialog
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            currentEmergencyDialog = null;
            Log.d(TAG, "Dismissed emergency popup dialog when returning to dashboard");
        }
    }

    private void openLocationInInternalMap(Double latitude, Double longitude, String address,
                                           String seniorName, String seniorPhone, String helpRequestId) {
        if (latitude != null && longitude != null) {
            Intent mapIntent = new Intent(this, RescuerNavigationActivity.class);

            // Pass emergency data to the dedicated navigation activity
            mapIntent.putExtra("latitude", latitude);
            mapIntent.putExtra("longitude", longitude);
            mapIntent.putExtra("locationAddress", address);
            mapIntent.putExtra("seniorName", seniorName);
            mapIntent.putExtra("seniorPhone", seniorPhone != null ? seniorPhone : "");
            mapIntent.putExtra("helpRequestId", helpRequestId);

            startActivity(mapIntent);
            
            // Show toast to guide rescuer
            Toast.makeText(this, "🗺️ Opening dedicated navigation to " + seniorName + "'s location", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.emergency_location_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGoogleNavigation(Double latitude, Double longitude, String address,
                                     String seniorName, String seniorPhone, String helpRequestId) {
        Log.d("Rescuer_Dashboard", "openGoogleNavigation called with: " + latitude + ", " + longitude);
        
        if (latitude != null && longitude != null) {
            try {
                Intent navigationIntent = new Intent(this, RescuerNavigationSDKActivity.class);

                // Pass emergency data to the Rescuer Navigation activity
                navigationIntent.putExtra("latitude", latitude);
                navigationIntent.putExtra("longitude", longitude);
                navigationIntent.putExtra("locationAddress", address);
                navigationIntent.putExtra("seniorName", seniorName);
                navigationIntent.putExtra("seniorPhone", seniorPhone != null ? seniorPhone : "");
                navigationIntent.putExtra("helpRequestId", helpRequestId);

                Log.d("Rescuer_Dashboard", "Starting RescuerNavigationActivity");
                startActivity(navigationIntent);
                
                // Show toast to guide rescuer
                Toast.makeText(this, "🗺️ Opening Google Maps to " + seniorName + "'s location", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e("Rescuer_Dashboard", "Error starting RescuerNavigationActivity", e);
                Toast.makeText(this, "Error opening navigation: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e("Rescuer_Dashboard", "Invalid coordinates: " + latitude + ", " + longitude);
            Toast.makeText(this, getString(R.string.emergency_location_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void callSenior(String phoneNumber) {
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        startActivity(callIntent);
    }

    private void openGoogleMapsNavigation(Double latitude, Double longitude, String destinationAddress) {
        if (latitude == null || longitude == null) {
            Toast.makeText(this, getString(R.string.destination_location_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.current_location_not_available_wait), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Create Google Maps navigation intent
            String destination = latitude + "," + longitude;
            String source = currentLat + "," + currentLong;
            
            // Use Google Maps navigation URL
            String navigationUrl = "https://www.google.com/maps/dir/" + source + "/" + destination;
            
            Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUrl));
            navigationIntent.setPackage("com.google.android.apps.maps");
            navigationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Check if Google Maps is installed
            if (navigationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navigationIntent);
                Toast.makeText(this, getString(R.string.opening_google_maps_navigation, destinationAddress), Toast.LENGTH_SHORT).show();
            } else {
                // Fallback to web browser if Google Maps app is not installed
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUrl));
                startActivity(webIntent);
                Toast.makeText(this, getString(R.string.opening_browser_navigation, destinationAddress), Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Maps navigation", e);
            Toast.makeText(this, getString(R.string.error_opening_navigation), Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternalGoogleMapsNavigation(Double latitude, Double longitude, String destinationAddress, String seniorName, String seniorPhone, String helpRequestId) {
        if (latitude == null || longitude == null) {
            Toast.makeText(this, "Destination location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, "Your current location is not available yet. Please wait for location update.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Create Google Maps navigation intent with turn-by-turn directions
            String navigationUri = String.format("google.navigation:q=%f,%f&mode=d", latitude, longitude);
            Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
            navigationIntent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps is installed
            if (navigationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navigationIntent);
                Toast.makeText(this, getString(R.string.opening_google_maps_to_senior, seniorName), Toast.LENGTH_LONG).show();
                
                // Also show a dialog with emergency details
                showEmergencyDetailsDialog(seniorName, seniorPhone, destinationAddress, helpRequestId);
            } else {
                // Fallback to web-based Google Maps
                String webMapsUri = String.format("https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving", 
                    latitude, longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webMapsUri));
                startActivity(webIntent);
                Toast.makeText(this, getString(R.string.opening_web_navigation_to_senior, seniorName), Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Maps navigation", e);
            Toast.makeText(this, getString(R.string.error_opening_navigation), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEmergencyDetailsDialog(String seniorName, String seniorPhone, String destinationAddress, String helpRequestId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 Emergency Response Details");
        builder.setMessage(String.format(
            "Senior: %s\n" +
            "Phone: %s\n" +
            "Address: %s\n" +
            "Help Request ID: %s\n\n" +
            "Google Maps navigation is now active. " +
            "You can return to this app to call the senior or view more details.",
            seniorName != null ? seniorName : "Unknown",
            seniorPhone != null ? seniorPhone : "Not available",
            destinationAddress != null ? destinationAddress : "Location only",
            helpRequestId
        ));
        
        builder.setPositiveButton("📞 Call Senior", (dialog, which) -> {
            if (seniorPhone != null && !seniorPhone.isEmpty()) {
                callSenior(seniorPhone);
            } else {
                Toast.makeText(this, getString(R.string.phone_number_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showSystemNotification(String title, String message, String helpRequestId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create intent for when notification is tapped
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationIntent.putExtra("notification_clicked", true);
        notificationIntent.putExtra("helpRequestId", helpRequestId);

        // Create pending intent
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, 
                helpRequestId.hashCode(), 
                notificationIntent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "emergency_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent) // Set the pending intent
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000); // Red light blinking

        notificationManager.notify(helpRequestId.hashCode(), builder.build());
        Log.d(TAG, "System notification sent with ID: " + helpRequestId.hashCode());
    }

    private void checkNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Request notification permission for Android 13+
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "emergency_channel",
                    "Emergency Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Emergency help requests from seniors");
            channel.enableVibration(true);
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // =============== HOSPITAL NAVIGATION ===============

    private void testNavigationToChristInYouHealeParish() {
        Log.d("Rescuer_Dashboard", "Testing Navigation to Christ in You Heale Parish");
        
        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🧪 Test Navigation");
        builder.setMessage("Test navigation to Christ in You Heale Parish in Magalang, Pampanga?");
        builder.setIcon(android.R.drawable.ic_dialog_map);
        
        builder.setPositiveButton("🗺️ Start Test", (dialog, which) -> {
            // Get current location and start navigation
            requestLocationAndStartTestNavigation();
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void requestLocationAndStartTestNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLong = location.getLongitude();
                        
                        // Get hospital location from database instead of hardcoded coordinates
                        getHospitalLocationAndStartNavigation("Christ in You Heale Parish");
                    } else {
                        Toast.makeText(this, "❌ Could not get current location. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, "❌ Error getting location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, "❌ Location permission required for navigation", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void getHospitalLocationAndStartNavigation(String hospitalName) {
        // Query hospitals from database to get the actual location
        db.collection("Sagip")
          .document("users")
          .collection("hospital")
          .whereEqualTo("hospitalName", hospitalName)
          .whereEqualTo("isOperational", true)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              if (!queryDocumentSnapshots.isEmpty()) {
                  // Get the first matching hospital
                  QueryDocumentSnapshot document = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                  Hospital hospital = document.toObject(Hospital.class);
                  hospital.hospitalId = document.getId();
                  
                  if (hospital.location != null) {
                      // Use the hospital's actual location from database
                      openGoogleNavigation(
                          hospital.location.getLatitude(), 
                          hospital.location.getLongitude(),
                          hospital.address != null ? hospital.address : hospitalName,
                          "Test Senior", 
                          "09123456789", 
                          "test123"
                      );
                      Toast.makeText(this, "🗺️ Testing Navigation to " + hospitalName + " (Database Location)", Toast.LENGTH_LONG).show();
                  } else {
                      Toast.makeText(this, "❌ Hospital location not found in database", Toast.LENGTH_SHORT).show();
                  }
              } else {
                  Toast.makeText(this, "❌ Hospital '" + hospitalName + "' not found in database", Toast.LENGTH_SHORT).show();
              }
          })
          .addOnFailureListener(e -> {
              Log.e("Rescuer_Dashboard", "Error retrieving hospital location from database", e);
              Toast.makeText(this, "❌ Error retrieving hospital location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }
    
    private void navigateToNearestHospital() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.current_location_not_available_permissions),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent mapIntent = new Intent(this, MyGoogleMAp.class);

        // Use consistent extra names that match MyGoogleMAp expectations
        mapIntent.putExtra("latitude", currentLat);
        mapIntent.putExtra("longitude", currentLong);
        mapIntent.putExtra("locationAddress", "Navigate to nearest hospital");
        mapIntent.putExtra("isEmergencyMode", false);
        mapIntent.putExtra("isRescuerMode", false);

        startActivity(mapIntent);
    }

    // =============== AUTHENTICATION & USER MANAGEMENT ===============

    private void checkAuthState() {
        Log.d(TAG, "Checking authentication state...");

        // Always check Firebase Auth first to ensure user is still authenticated
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Log.d(TAG, "Firebase currentUser: " + (currentUser != null ? currentUser.getUid() : "null"));

        if (currentUser != null) {
            // User is authenticated in Firebase
            userId = currentUser.getUid();
            String phoneNumber = currentUser.getPhoneNumber();

            // Check if we have stored user type, otherwise detect it
            String storedUserType = sharedPreferences.getString(KEY_USER_TYPE, null);

            if (storedUserType != null) {
                Log.d(TAG, "Using stored user type: " + storedUserType);
                this.userType = storedUserType;
                // Check user status before loading data
                checkUserStatusAndRedirect();
            } else {
                Log.d(TAG, "No stored user type, detecting from database...");
                // User type not stored, need to detect it from database
                detectAndLoadUserType(userId, phoneNumber);
            }
        } else {
            // No Firebase user, check if we have any stored credentials to clear
            boolean wasLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
            if (wasLoggedIn) {
                Log.d(TAG, "User was logged in but Firebase session expired, clearing data...");
                clearStoredCredentials();
            }

            Log.d(TAG, "No authenticated user found, redirecting to login...");
            navigateToLogin();
        }
    }

    private void saveUserToPreferences(String userId, String userType, String phoneNumber) {
        Log.d(TAG, "Saving user to SharedPreferences: " + userId + ", " + userType);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_TYPE, userType);
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        editor.apply();
    }

    private void checkUserStatusAndRedirect() {
        if (userId == null) {
            Log.w(TAG, "userId is null, cannot check status");
            return;
        }

        Log.d(TAG, "Checking user status for userId: " + userId);

        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        Log.d(TAG, "User status: " + status);
                        
                        if ("new".equals(status)) {
                            Log.d(TAG, "User status is 'new', redirecting to registration");
                            // User status is "new", redirect to registration
                            Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Log.d(TAG, "User status is not 'new', proceeding to dashboard");
                            // User is registered, proceed with dashboard initialization
                            loadUserData(userId);
                        }
                    } else {
                        Log.w(TAG, "User document does not exist, redirecting to registration");
                        // User document doesn't exist, redirect to registration
                        Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user status: " + e.getMessage(), e);
                    // On error, redirect to registration to be safe
                    Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void navigateToLogin() {
        Log.d(TAG, "Navigating to login screen...");
        Intent intent = new Intent(Rescuer_Dashboard.this, MainActivity.class);
        // Clear the back stack so user can't press back to return after logging out
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadUserData(String uid) {
        Log.d(TAG, "Loading user data for: " + uid + " in collection: " + userType);

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                Log.d(TAG, "User document found, loading data...");
                                loadUserDataFromDocument(document);

                                // Ensure user credentials are saved
                                FirebaseUser currentUser = mAuth.getCurrentUser();
                                if (currentUser != null) {
                                    saveUserToPreferences(uid, userType, currentUser.getPhoneNumber());
                                }

                                // Emergency listener will be started in onResume()
                            } else {
                                Log.e(TAG, "User document does not exist for UID: " + uid + " in collection: " + userType);

                                // Document doesn't exist, try to detect correct user type
                                FirebaseUser currentUser = mAuth.getCurrentUser();
                                if (currentUser != null) {
                                    detectAndLoadUserType(uid, currentUser.getPhoneNumber());
                                } else {
                                    Toast.makeText(Rescuer_Dashboard.this,
                                            "User profile not found. Please login again.",
                                            Toast.LENGTH_LONG).show();
                                    clearStoredCredentials();
                                    navigateToLogin();
                                }
                            }
                        } else {
                            Log.e(TAG, "Error loading user data: " + task.getException().getMessage());
                            Toast.makeText(Rescuer_Dashboard.this,
                                    "Error loading user data. Please check your connection and try again.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void detectAndLoadUserType(String uid, String phoneNumber) {
        Log.d(TAG, "Detecting user type for UID: " + uid);

        // Check for phone-based users first (seniors, user, rescuer, admin)
        String[] phoneUserTypes = {"rescuer", "seniors"};

        if (phoneNumber != null) {
            Log.d(TAG, "Phone number available: " + phoneNumber + ", checking phone-based collections...");
            checkPhoneBasedUserTypes(uid, phoneNumber, phoneUserTypes, 0);
        } else {
            Log.d(TAG, "No phone number, checking UID-based collections...");
            // Check UID-based users (hospital, barangay, etc.)
            String[] uidUserTypes = {"rescuer", "hospital", "barangay", "senior"};
            checkUIDBasedUserTypes(uid, uidUserTypes, 0);
        }
    }

    private void checkPhoneBasedUserTypes(String uid, String phoneNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.d(TAG, "Phone-based user not found, checking UID-based collections...");
            // Not found in phone-based collections, try UID-based
            String[] uidUserTypes = {"rescuer", "hospital", "barangay", "senior"};
            checkUIDBasedUserTypes(uid, uidUserTypes, 0);
            return;
        }

        String currentUserType = userTypes[index];
        Log.d(TAG, "Checking phone-based user type: " + currentUserType);

        // Try both with and without +63 prefix
        String searchNumber = phoneNumber;
        if (phoneNumber.startsWith("+63")) {
            searchNumber = phoneNumber.substring(3); // Remove +63 prefix
        }
        
        db.collection("Sagip")
                .document("users")
                .collection(currentUserType)
                .whereEqualTo("mobileNumber", searchNumber)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Log.d(TAG, "User found in phone-based collection: " + currentUserType);
                        this.userType = currentUserType;
                        saveUserToPreferences(uid, currentUserType, phoneNumber);
                        loadUserData(uid);
                    } else {
                        // Try next user type
                        checkPhoneBasedUserTypes(uid, phoneNumber, userTypes, index + 1);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking phone-based user type " + currentUserType + ": " + e.getMessage());
                    // Try next user type
                    checkPhoneBasedUserTypes(uid, phoneNumber, userTypes, index + 1);
                });
    }

    private void checkUIDBasedUserTypes(String uid, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User not found in any collection");
            Toast.makeText(this, getString(R.string.user_profile_not_found), Toast.LENGTH_LONG).show();
            clearStoredCredentials();
            mAuth.signOut();
            navigateToLogin();
            return;
        }

        String currentUserType = userTypes[index];
        Log.d(TAG, "Checking UID-based user type: " + currentUserType);

        db.collection("Sagip")
                .document("users")
                .collection(currentUserType)
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        Log.d(TAG, "User found in UID-based collection: " + currentUserType);
                        this.userType = currentUserType;
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        String phoneNumber = currentUser != null ? currentUser.getPhoneNumber() : null;
                        saveUserToPreferences(uid, currentUserType, phoneNumber);
                        loadUserDataFromDocument(document);

                        // Emergency listener will be started in onResume()
                    } else {
                        // Try next user type
                        checkUIDBasedUserTypes(uid, userTypes, index + 1);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking UID-based user type " + currentUserType + ": " + e.getMessage());
                    // Try next user type
                    checkUIDBasedUserTypes(uid, userTypes, index + 1);
                });
    }

    private void loadUserDataFromDocument(DocumentSnapshot document) {
        // Check for different name fields based on user type
        String displayName = null;

        if (userType.equals("rescuer")) {
            displayName = document.getString("rescuegroup");
        }

        if (displayName == null) {
            displayName = document.getString("firstName");
        }

        if (displayName == null) {
            displayName = document.getString("name");
        }

        if (displayName != null) {
            brgyName.setText(displayName);
        } else {
            brgyName.setText(getString(R.string.user_name_not_available));
        }

        // Check if there's stored location data
        GeoPoint geoPoint = document.getGeoPoint("currentLocation");
        if (geoPoint != null) {
            currentLat = geoPoint.getLatitude();
            currentLong = geoPoint.getLongitude();
            updateLocationDisplay(currentLat, currentLong);
        }
        
        // Check for new hospital status update notifications
        if (userType.equals("rescuer") && userId != null) {
            Log.d(TAG, "=== CHECKING FOR HOSPITAL STATUS UPDATE NOTIFICATIONS IN LOADUSERDATA ===");
            Log.d(TAG, "User Type: " + userType);
            Log.d(TAG, "User ID: " + userId);
            
            // Stop background service since app is now active (prevents double notifications)
            // Get and store FCM token for real-time notifications
            getAndStoreFCMToken(userId, userType);
            
            // Check for notifications locally since app is active
            HospitalStatusUpdateNotificationService.checkAndDisplayNotificationsForRescuer(this, userId);
        } else {
            Log.d(TAG, "Skipping notification check in loadUserData - User Type: " + userType + ", User ID: " + userId);
        }
        
        // Add a test button to manually check notifications (for debugging)
        addTestNotificationButton();
    }

    /**
     * Starts the background notification service for rescuers
     */
    private void startRescuerBackgroundNotificationService() {
        Log.d(TAG, "Starting RescuerBackgroundNotificationService");
        
        // Check if service is already running
        if (isServiceRunning(RescuerBackgroundNotificationService.class)) {
            Log.d(TAG, "RescuerBackgroundNotificationService is already running");
            return;
        }
        
        // Request battery optimization exemption for better background execution
        requestBatteryOptimizationExemption();
        
        Intent serviceIntent = new Intent(this, RescuerBackgroundNotificationService.class);
        serviceIntent.putExtra("action", "start_monitoring");
        startService(serviceIntent);
    }
    
    /**
     * Stops the background notification service for rescuers
     */
    private void stopRescuerBackgroundNotificationService() {
        Log.d(TAG, "Stopping RescuerBackgroundNotificationService");
        
        Intent serviceIntent = new Intent(this, RescuerBackgroundNotificationService.class);
        serviceIntent.putExtra("action", "stop_monitoring");
        startService(serviceIntent);
    }
    
    /**
     * Tests the hospital status update notification system
     * This method can be called to verify notifications work when app is closed
     * DISABLED FOR PRODUCTION
     */
    public void testHospitalStatusNotification() {
        Log.d(TAG, "Test hospital status notification - DISABLED FOR PRODUCTION");
        Toast.makeText(this, "Test notifications disabled for production", Toast.LENGTH_SHORT).show();
        
        // Uncomment below for testing:
        // NativeNotificationSender.sendHospitalUpdateNotificationToRescuers("Test Hospital", "Open", 5, 3);
    }
    
    /**
     * Stops all notification services when user logs out
     * This should only be called during logout, not when app is closing
     */
    private void stopAllNotificationServices() {
        Log.d(TAG, "Stopping all notification services due to logout");
        
        try {
            // Stop WorkManager
            NotificationWorkManager.stopNotificationMonitoring(this);
            
            // Stop AlternativeNotificationManager
            AlternativeNotificationManager.getInstance(this).stopMonitoring();
            
            // Stop WebSocketNotificationService
            Intent webSocketIntent = new Intent(this, WebSocketNotificationService.class);
            webSocketIntent.putExtra("action", "stop_monitoring");
            startService(webSocketIntent);
            
            // Stop RescuerForegroundService
            Intent rescuerIntent = new Intent(this, RescuerForegroundService.class);
            rescuerIntent.putExtra("action", "stop");
            startService(rescuerIntent);
            
            // Stop BackgroundNotificationService
            Intent backgroundIntent = new Intent(this, BackgroundNotificationService.class);
            backgroundIntent.putExtra("action", "stop");
            startService(backgroundIntent);
            
            // Stop HospitalStatusNotificationService
            Intent hospitalStatusIntent = new Intent(this, HospitalStatusNotificationService.class);
            hospitalStatusIntent.putExtra("action", "stop_monitoring");
            startService(hospitalStatusIntent);
            
            Log.d(TAG, "All notification services stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping notification services: " + e.getMessage());
        }
    }
    
    /**
     * Requests battery optimization exemption for better background service execution
     */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent();
                String packageName = getPackageName();
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    Log.d(TAG, "Requesting battery optimization exemption");
                    intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + packageName));
                    startActivity(intent);
                } else {
                    Log.d(TAG, "Battery optimization already disabled for this app");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to request battery optimization exemption: " + e.getMessage());
            }
        }
    }
    
    /**
     * Checks if a service is currently running
     */
    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Initialize FCM token for notifications when app starts
     */
    private void initializeFCMToken() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (userId != null && userType != null && "rescuer".equals(userType)) {
            Log.d(TAG, "Initializing FCM token for rescuer: " + userId);
            getAndStoreFCMToken(userId, userType);
        }
    }


    /**
     * Gets and stores FCM token for real-time notifications
     */
    private void getAndStoreFCMToken(String userId, String userType) {
        Log.d(TAG, "Getting FCM token for user: " + userId);
        
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();
                        Log.d(TAG, "FCM Registration Token: " + token);

                        // Store token in database
                        FCMNotificationSender.updateUserFCMToken(userId, userType, token);
                    }
                });
    }

    /**
     * Adds a test button to manually check for notifications (for debugging)
     */
    private void addTestNotificationButton() {
        // Find the test navigation button and add a long press listener for notification testing
        if (testNavigationButton != null) {
            testNavigationButton.setOnLongClickListener(v -> {
                Log.d(TAG, "=== MANUAL NOTIFICATION CHECK TRIGGERED ===");
                if (userType != null && userType.equals("rescuer") && userId != null) {
                    Log.d(TAG, "Manually checking notifications for rescuer: " + userId);
                    
                    // Check notifications locally
                    HospitalStatusUpdateNotificationService.checkAndDisplayNotificationsForRescuer(this, userId);
                    Toast.makeText(this, "🔔 Checking for notifications...", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "Cannot check notifications - User Type: " + userType + ", User ID: " + userId);
                    Toast.makeText(this, "❌ Cannot check notifications - not a rescuer user", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            
            // Add single-click test functionality
            testNavigationButton.setOnClickListener(v -> {
                long currentTime = System.currentTimeMillis();
                if (lastTapTime != 0 && (currentTime - lastTapTime) < 500) {
                    // Double tap - create test notification
                    createTestHospitalStatusNotification();
                }
                lastTapTime = currentTime;
            });
        }
    }

    /**
     * Creates a test hospital status notification in Firestore
     */
    private void createTestHospitalStatusNotification() {
        if (userId == null || !"rescuer".equals(userType)) {
            Toast.makeText(this, "❌ Not a rescuer user", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Creating test hospital status notification for rescuer: " + userId);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "hospital_status_update");
        notificationData.put("title", "🏥 Test Hospital Status Update");
        notificationData.put("message", "Test Hospital is now OPEN (Beds: 5, Doctors: 2)");
        notificationData.put("hospitalName", "Test Hospital");
        notificationData.put("hospitalStatus", "open");
        notificationData.put("availableBeds", 5);
        notificationData.put("availableDoctors", 2);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        
        db.collection("Sagip")
            .document("users")
            .collection("rescuer")
            .document(userId)
            .collection("notifications")
            .add(notificationData)
            .addOnSuccessListener(documentReference -> {
                Log.d(TAG, "✅ Test notification created: " + documentReference.getId());
                Toast.makeText(this, "✅ Test notification created!", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Failed to create test notification", e);
                Toast.makeText(this, "❌ Failed to create test notification", Toast.LENGTH_SHORT).show();
            });
    }

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials...");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        editor.apply();
    }

    // =============== LOCATION SERVICES ===============

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission already granted, start location updates
            startLocationUpdates();
            
            // Also check notification permissions since location is already granted
            checkNotificationPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                startLocationUpdates();
                
                // Now request notification permission immediately after location permission is granted
                checkNotificationPermissions();
            } else {
                // Permission denied, show a message
                Toast.makeText(this, getString(R.string.location_permission_denied),
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Notification permission granted
                Log.d(TAG, "Notification permission granted");
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                // Notification permission denied
                Log.w(TAG, "Notification permission denied");
                Toast.makeText(this, "Notification permission denied - you may not receive emergency alerts", 
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(10000) // Update every 10 seconds
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(5000) // Minimum 5 seconds
                .build();
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // Update location
                    currentLat = location.getLatitude();
                    currentLong = location.getLongitude();

                    // Update UI and save to Firebase
                    updateLocationDisplay(currentLat, currentLong);
                    saveLocationToFirestore(currentLat, currentLong);
                }
            }
        };
    }

    private void startLocationUpdates() {
        // Ensure locationCallback is initialized
        if (locationCallback == null) {
            Log.w(TAG, "LocationCallback is null, creating callback...");
            createLocationCallback();
        }

        // Ensure locationRequest is initialized
        if (locationRequest == null) {
            Log.w(TAG, "LocationRequest is null, creating request...");
            createLocationRequest();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            try {
                fusedLocationClient.requestLocationUpdates(locationRequest,
                        locationCallback,
                        Looper.getMainLooper());
                Log.d(TAG, "Location updates started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error starting location updates: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_starting_location_updates), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                Log.d(TAG, "Location updates stopped successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping location updates: " + e.getMessage());
            }
        }
    }

    private void updateLocationDisplay(double latitude, double longitude) {
        String locationText = getAddressFromLocation(latitude, longitude);
        if (locationText != null) {
            currentLocationText.setText(locationText);
        } else {
            // Fallback to coordinates if address can't be determined
            currentLocationText.setText(String.format(Locale.getDefault(),
                    "%.6f, %.6f", latitude, longitude));
        }
    }

    private String getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);

                // Format the address
                StringBuilder sb = new StringBuilder();

                // Add thoroughfare (street) if available
                if (address.getThoroughfare() != null) {
                    sb.append(address.getThoroughfare());
                }

                // Add locality (city/municipality)
                if (address.getLocality() != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(address.getLocality());
                }

                // Add subAdminArea (province/region) if different from locality
                if (address.getSubAdminArea() != null &&
                        (address.getLocality() == null ||
                                !address.getSubAdminArea().equals(address.getLocality()))) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(address.getSubAdminArea());
                }

                return sb.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveLocationToFirestore(double latitude, double longitude) {
        // Make sure we have a valid user ID
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            } else {
                // No user is signed in, can't save data
                return;
            }
        }

        // Create data object with location - use both formats for compatibility
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("currentLocation", new GeoPoint(latitude, longitude));
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("lastUpdated", com.google.firebase.Timestamp.now());

        // Save to Firestore
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .update(locationData)
                .addOnSuccessListener(aVoid -> {
                    // Location saved successfully
                    Log.d(TAG, "Location updated successfully - lat: " + latitude + ", lng: " + longitude);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update location: " + e.getMessage());
                });
    }

    // Method to update rescuer location specifically for tracking purposes
    private void updateRescuerLocationForTracking() {
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            } else {
                Log.e(TAG, "No user ID available for location tracking update");
                return;
            }
        }

        // Create tracking-specific location data
        Map<String, Object> trackingData = new HashMap<>();
        trackingData.put("latitude", currentLat);
        trackingData.put("longitude", currentLong);
        trackingData.put("currentLocation", new GeoPoint(currentLat, currentLong));
        trackingData.put("isResponding", true);
        trackingData.put("lastLocationUpdate", com.google.firebase.Timestamp.now());

        // Update the rescuer's document for tracking
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .update(trackingData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Rescuer location updated for tracking - lat: " + currentLat + ", lng: " + currentLong);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update rescuer location for tracking: " + e.getMessage());
                });
    }

    // Method to clear tracking status when rescuer finishes responding
    private void clearTrackingStatus() {
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            } else {
                Log.e(TAG, "No user ID available for clearing tracking status");
                return;
            }
        }

        // Clear tracking-specific data
        Map<String, Object> trackingData = new HashMap<>();
        trackingData.put("isResponding", false);
        trackingData.put("lastLocationUpdate", com.google.firebase.Timestamp.now());

        // Update the rescuer's document
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .update(trackingData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Rescuer tracking status cleared");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to clear tracking status: " + e.getMessage());
                });
    }

    // =============== NAVIGATION SETUP ===============

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.rescuer_dashboard);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.rescuer_dashboard) {
                return true;
            } else if (itemId == R.id.rescuer_hospital) {
                startActivity(new Intent(getApplicationContext(), Rescuer_List.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.rescuer_profile) {
                startActivity(new Intent(getApplicationContext(), Rescuer_Profile.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
    
    // Request current location and start navigation
    private void requestLocationAndStartNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLong = location.getLongitude();
                        
                        // Get hospital location from database instead of hardcoded coordinates
                        getHospitalLocationAndStartNavigation("Christ in You Heale Parish");
                    } else {
                        Toast.makeText(this, "❌ Could not get current location. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, "❌ Error getting location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, "❌ Location permission required for navigation", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Open SOS Navigation using basic Google Maps
    private void openSOSNavigation() {
        Log.d("Rescuer_Dashboard", "openSOSNavigation called");
        
        try {
            Intent rescuerNavigationIntent = new Intent(this, RescuerNavigationActivity.class);
            
            // Pass SOS emergency data to the RescuerNavigationActivity
            rescuerNavigationIntent.putExtra("latitude", 15.22514); // Test SOS location
            rescuerNavigationIntent.putExtra("longitude", 120.62861);
            rescuerNavigationIntent.putExtra("locationAddress", "Emergency Location - Test SOS Call");
            rescuerNavigationIntent.putExtra("seniorName", "Test Senior");
            rescuerNavigationIntent.putExtra("seniorPhone", "09123456789");
            rescuerNavigationIntent.putExtra("helpRequestId", "test_sos_123");
            
            Log.d("Rescuer_Dashboard", "Starting RescuerNavigationActivity for SOS navigation");
            startActivity(rescuerNavigationIntent);
            
        } catch (Exception e) {
            Log.e("Rescuer_Dashboard", "Error starting RescuerNavigationActivity", e);
            Toast.makeText(this, "Error opening SOS navigation: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // Request current location and start SOS navigation
    private void requestLocationAndStartSOSNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLong = location.getLongitude();
                        
                        // Now start SOS navigation with current location
                        openSOSNavigation();
                        Toast.makeText(this, "🚨 SOS Navigation from your current location", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "❌ Could not get current location. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, "❌ Error getting location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, "❌ Location permission required for navigation", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Loading dialog methods
    private void showLoadingDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🤖 AI Processing");
        builder.setMessage(message);
        builder.setCancelable(false);
        
        // Add progress bar
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        
        loadingDialog = builder.create();
        loadingDialog.show();
    }
    
    private void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }
    
    private void showNoHospitalFoundDialog(String helpRequestId, String errorMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🏥 No Hospital Found");
        builder.setMessage("❌ " + (errorMessage != null ? errorMessage : "No suitable hospital found within 15km radius") + 
                          "\n\nPlease try one of these options:");
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        // Option 1: Expand search radius
        builder.setPositiveButton("🔍 EXPAND SEARCH (30km)", (dialog, which) -> {
            expandHospitalSearch(helpRequestId, 30.0);
        });
        
        // Option 2: Manual selection
        builder.setNeutralButton("👤 SELECT MANUALLY", (dialog, which) -> {
            openHospitalSelection(helpRequestId);
        });
        
        // Option 3: Navigate to emergency location first
        builder.setNegativeButton("🚑 GO TO EMERGENCY", (dialog, which) -> {
            // Navigate to emergency location first, then find nearest hospital
            navigateToEmergencyFirst(helpRequestId);
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void expandHospitalSearch(String helpRequestId, double radiusKm) {
        // Show loading dialog
        showLoadingDialog("Searching hospitals within " + radiusKm + "km...");
        
        // Get emergency details and search with expanded radius
        db.collection("Sagip")
          .document("helpRequests")
          .collection("activeRequests")
          .document(helpRequestId)
          .get()
          .addOnSuccessListener(documentSnapshot -> {
              if (documentSnapshot.exists()) {
                  double seniorLat = documentSnapshot.getDouble("latitude");
                  double seniorLon = documentSnapshot.getDouble("longitude");
                  String emergencyType = documentSnapshot.getString("emergencyType");
                  String severity = documentSnapshot.getString("severity");
                  
                  // Create emergency object for AI
                  Emergency emergency = new Emergency();
                  emergency.helpRequestId = helpRequestId;
                  emergency.emergencyType = emergencyType != null ? emergencyType : "general";
                  emergency.severity = severity != null ? severity : "high";
                  emergency.location = new com.google.firebase.firestore.GeoPoint(seniorLat, seniorLon);
                  emergency.timestamp = System.currentTimeMillis();
                  
                  // Search with expanded radius
                  searchHospitalsWithRadius(emergency, radiusKm, helpRequestId);
              } else {
                  hideLoadingDialog();
                  Toast.makeText(this, "Emergency details not found", Toast.LENGTH_SHORT).show();
              }
          })
          .addOnFailureListener(e -> {
              hideLoadingDialog();
              Toast.makeText(this, "Error loading emergency details", Toast.LENGTH_SHORT).show();
          });
    }
    
    private void searchHospitalsWithRadius(Emergency emergency, double radiusKm, String helpRequestId) {
        // Query hospitals with expanded radius
        db.collection("Sagip")
          .document("users")
          .collection("hospital")
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              List<Hospital> hospitals = new ArrayList<>();
              
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  // Create hospital object manually to handle field name differences
                  Hospital hospital = new Hospital();
                  hospital.hospitalId = document.getId();
                  
                  // Map field names from your database structure
                  hospital.name = document.getString("hospitalName");
                  if (hospital.name == null) {
                      hospital.name = document.getString("name");
                  }
                  
                  // Handle location field - try different possible field names
                  com.google.firebase.firestore.GeoPoint location = document.getGeoPoint("currentLocation");
                  if (location == null) {
                      location = document.getGeoPoint("location");
                  }
                  hospital.location = location;
                  
                  // Set other fields
                  hospital.address = document.getString("hospitalAddress");
                  if (hospital.address == null) {
                      hospital.address = document.getString("address");
                  }
                  
                  hospital.phone = document.getString("mobileNumber");
                  if (hospital.phone == null) {
                      hospital.phone = document.getString("phone");
                  }
                  
                  // Set operational status - default to true if not specified
                  Boolean isOperational = document.getBoolean("isOperational");
                  hospital.isOperational = isOperational != null ? isOperational : true;
                  
                  // Only process if we have required fields
                  if (hospital.name != null && hospital.location != null) {
                      // Check if within expanded radius
                      double distance = calculateDistance(
                          emergency.location.getLatitude(), emergency.location.getLongitude(),
                          hospital.location.getLatitude(), hospital.location.getLongitude()
                      );
                      
                      if (distance <= radiusKm) {
                          hospital.distanceFromSenior = distance;
                          hospitals.add(hospital);
                      }
                  }
              }
              
              hideLoadingDialog();
              
              if (hospitals.isEmpty()) {
                  Toast.makeText(this, "No hospitals found within " + radiusKm + "km", Toast.LENGTH_LONG).show();
                  openHospitalSelection(helpRequestId);
              } else {
                  // Show found hospitals for manual selection
                  showHospitalSelectionDialog(hospitals, helpRequestId);
              }
          })
          .addOnFailureListener(e -> {
              hideLoadingDialog();
              Toast.makeText(this, "Error searching hospitals", Toast.LENGTH_SHORT).show();
          });
    }
    
    private void showHospitalSelectionDialog(List<Hospital> hospitals, String helpRequestId) {
        // Sort hospitals by distance
        hospitals.sort((h1, h2) -> Double.compare(h1.distanceFromSenior, h2.distanceFromSenior));
        
        StringBuilder message = new StringBuilder();
        message.append("🏥 Found ").append(hospitals.size()).append(" hospitals:\n\n");
        
        for (int i = 0; i < Math.min(5, hospitals.size()); i++) {
            Hospital hospital = hospitals.get(i);
            message.append("• ").append(hospital.name)
                   .append(" (").append(String.format("%.1f km", hospital.distanceFromSenior)).append(")\n");
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🏥 Select Hospital");
        builder.setMessage(message.toString());
        
        // Show first 3 hospitals as quick options
        for (int i = 0; i < Math.min(3, hospitals.size()); i++) {
            final Hospital hospital = hospitals.get(i);
            builder.setPositiveButton("🏥 " + hospital.name, (dialog, which) -> {
                navigateToSelectedHospital(hospital, helpRequestId);
            });
        }
        
        // Show all hospitals option
        builder.setNeutralButton("📋 VIEW ALL", (dialog, which) -> {
            openHospitalSelection(helpRequestId);
        });
        
        builder.setNegativeButton("❌ Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void navigateToEmergencyFirst(String helpRequestId) {
        // Get emergency location and navigate there first
        db.collection("Sagip")
          .document("helpRequests")
          .collection("activeRequests")
          .document(helpRequestId)
          .get()
          .addOnSuccessListener(documentSnapshot -> {
              if (documentSnapshot.exists()) {
                  double seniorLat = documentSnapshot.getDouble("latitude");
                  double seniorLon = documentSnapshot.getDouble("longitude");
                  String locationAddress = documentSnapshot.getString("locationAddress");
                  String seniorName = documentSnapshot.getString("seniorName");
                  String seniorPhone = documentSnapshot.getString("seniorPhone");
                  
                  // Navigate to emergency location
                  openGoogleNavigation(seniorLat, seniorLon, locationAddress, seniorName, seniorPhone, helpRequestId);
                  
                  Toast.makeText(this, "🚑 Navigating to emergency location. Find nearest hospital when you arrive.", Toast.LENGTH_LONG).show();
              }
          });
    }
    
    private void navigateToSelectedHospital(Hospital hospital, String helpRequestId) {
        // Update help request with selected hospital
        Map<String, Object> updates = new HashMap<>();
        updates.put("selectedHospitalId", hospital.hospitalId);
        updates.put("selectedHospitalName", hospital.name);
        updates.put("aiRecommendation", false);
        updates.put("selectedAt", System.currentTimeMillis());
        
        db.collection("Sagip")
          .document("helpRequests")
          .collection("activeRequests")
          .document(helpRequestId)
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              // Navigate to hospital
              openGoogleNavigation(
                  hospital.location.getLatitude(), 
                  hospital.location.getLongitude(),
                  hospital.address,
                  "Selected: " + hospital.name,
                  hospital.phone,
                  helpRequestId
              );
              
              Toast.makeText(this, "Navigating to " + hospital.name, Toast.LENGTH_SHORT).show();
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Failed to save hospital selection", Toast.LENGTH_SHORT).show();
          });
    }
}