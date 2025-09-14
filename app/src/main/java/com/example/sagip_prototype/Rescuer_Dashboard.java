package com.example.sagip_prototype;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioAttributes;
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

import com.example.sagip_prototype.models.Hospital;
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
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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
    private static final String KEY_CACHED_DISPLAY_NAME = "cachedDisplayName";

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
        
        // Initialize emergency queue manager
        EmergencyQueueManager.getInstance(this).loadActiveEmergenciesFromDatabase();
        
        // Handle emergency notification if app was opened from notification
        handleEmergencyNotificationIntent();
        
        // Test custom alarm sound removed as requested

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
                    Toast.makeText(Rescuer_Dashboard.this, getString(R.string.toast_opening_sos_navigation), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(Rescuer_Dashboard.this, getString(R.string.toast_getting_location), Toast.LENGTH_SHORT).show();
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "=== ON_NEW_INTENT CALLED ===");
        setIntent(intent);
        handleNotificationClick();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load cached display name immediately when returning to dashboard
        loadCachedDisplayName();

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
        
        // Start emergency SOS notification listener
        startEmergencySOSListener();
        
        // Start background service for emergency SOS monitoring
        startEmergencySOSBackgroundService();
        
        // Disable RescuerNotificationManager to prevent duplicate notifications
        // RescuerNotificationManager.startMonitoring(this);
        
        // Test emergency notification system
        testEmergencyNotificationSystem();
        
        // Debug: Check if there are any existing emergency notifications
        checkForExistingEmergencyNotifications();

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
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle language change without recreating activity
        Log.d(TAG, "Configuration changed - language change detected");
        
        // Reload cached display name to ensure it's still displayed
        loadCachedDisplayName();
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

    private void handleEmergencyNotificationIntent() {
        // Check if this activity was opened from an emergency SOS notification
        Intent intent = getIntent();
        if (intent != null) {
            boolean isEmergencySOS = intent.getBooleanExtra("emergency_sos_clicked", false) || 
                                   intent.getBooleanExtra("from_emergency_notification", false);
            
            if (isEmergencySOS) {
                String seniorName = intent.getStringExtra("senior_name");
                String seniorPhone = intent.getStringExtra("senior_phone");
                String locationAddress = intent.getStringExtra("location_address");
                
                Log.d(TAG, "🚨 App opened from emergency SOS notification in onCreate - Senior: " + seniorName);
                
                // Show emergency alert dialog immediately after a short delay to ensure UI is ready
                if (seniorName != null && locationAddress != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        showEmergencySOSAlert(seniorName, seniorPhone, locationAddress, System.currentTimeMillis());
                    }, 1000); // 1 second delay to ensure UI is fully loaded
                }
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("emergency_sos_clicked");
                intent.removeExtra("from_emergency_notification");
                intent.removeExtra("senior_name");
                intent.removeExtra("senior_phone");
                intent.removeExtra("location_address");
            }
        }
    }

    private void handleNotificationClick() {
        // Check if this activity was opened from a notification click
        Intent intent = getIntent();
        if (intent != null) {
            String notificationType = intent.getStringExtra("notification_type");
            Log.d(TAG, "Activity opened from notification - Type: " + notificationType);
            
            if ("hospital_update".equals(notificationType) || "hospital_status_update".equals(notificationType)) {
                // Handle hospital status update notification
                String hospitalName = intent.getStringExtra("hospital_name");
                String hospitalStatus = intent.getStringExtra("hospital_status");
                int availableBeds = intent.getIntExtra("available_beds", 0);
                int availableDoctors = intent.getIntExtra("available_doctors", 0);
                
                Log.d(TAG, "Hospital status update notification - Hospital: " + hospitalName + 
                    ", Status: " + hospitalStatus + ", Beds: " + availableBeds + ", Doctors: " + availableDoctors);
                
                // Show hospital status update info
                showHospitalStatusUpdateDialog(hospitalName, hospitalStatus, availableBeds, availableDoctors);
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("notification_type");
                intent.removeExtra("hospital_name");
                intent.removeExtra("hospital_status");
                intent.removeExtra("available_beds");
                intent.removeExtra("available_doctors");
                
            } else if (intent.getBooleanExtra("assignment_confirmed", false)) {
                // Handle assignment confirmation notification
                String seniorName = intent.getStringExtra("senior_name");
                String locationAddress = intent.getStringExtra("location_address");
                
                Log.d(TAG, "App opened from assignment confirmation - Senior: " + seniorName);
                
                if (seniorName != null && locationAddress != null) {
                    // Show assignment confirmation dialog
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        showRescuerAssignmentPopup(seniorName, locationAddress, mAuth.getCurrentUser().getUid(), null);
                    }, 1000);
                }
                
                // Clear the intent extras
                intent.removeExtra("assignment_confirmed");
                intent.removeExtra("senior_name");
                intent.removeExtra("location_address");
                
            } else if (intent.getBooleanExtra("emergency_sos_clicked", false) || intent.getBooleanExtra("from_emergency_notification", false)) {
                // Handle emergency SOS notification - app opened from closed state
                String seniorName = intent.getStringExtra("senior_name");
                String seniorPhone = intent.getStringExtra("senior_phone");
                String locationAddress = intent.getStringExtra("location_address");
                
                Log.d(TAG, "🚨 App opened from emergency SOS notification - Senior: " + seniorName);
                
                // Show emergency alert dialog immediately
                if (seniorName != null && locationAddress != null) {
                    showEmergencySOSAlert(seniorName, seniorPhone, locationAddress, System.currentTimeMillis());
                }
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("emergency_sos_clicked");
                intent.removeExtra("from_emergency_notification");
                intent.removeExtra("senior_name");
                intent.removeExtra("senior_phone");
                intent.removeExtra("location_address");
                
            } else if (intent.getBooleanExtra("notification_clicked", false)) {
                // Handle emergency notification
                String helpRequestId = intent.getStringExtra("helpRequestId");
                Log.d(TAG, "Activity opened from emergency notification click for helpRequestId: " + helpRequestId);
                
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
    }
    
    private void showHospitalStatusUpdateDialog(String hospitalName, String hospitalStatus, int availableBeds, int availableDoctors) {
        String statusEmoji = getStatusEmoji(hospitalStatus);
        String message = "🏥 " + hospitalName + "\n\n" +
                        "Status: " + statusEmoji + " " + hospitalStatus.toUpperCase() + "\n" +
                        "Available Beds: " + availableBeds + "\n" +
                        "Available Doctors: " + availableDoctors + "\n\n" +
                        "This information will help with emergency response planning.";
        
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_hospital_status_update))
                .setMessage(message)
                .setPositiveButton("View Hospital List", (dialog, which) -> {
                    // Navigate to hospital list with highlighting
                    Intent intent = new Intent(this, Rescuer_List.class);
                    intent.putExtra("highlight_hospital", hospitalName);
                    intent.putExtra("notification_type", "hospital_status_update");
                    intent.putExtra("hospital_status", hospitalStatus);
                    intent.putExtra("available_beds", availableBeds);
                    intent.putExtra("available_doctors", availableDoctors);
                    startActivity(intent);
                })
                .setNeutralButton("View Dashboard", (dialog, which) -> {
                    // Stay on dashboard but scroll to hospital section if available
                    Toast.makeText(this, getString(R.string.text_hospital_status_updated, hospitalName, statusEmoji, hospitalStatus.toUpperCase()), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Dismiss", (dialog, which) -> {
                    // Just dismiss the dialog
                    dialog.dismiss();
                })
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
    
    private String getStatusEmoji(String status) {
        if (status == null) return "❓";
        
        switch (status.toLowerCase()) {
            case "operational":
                return "🟢";
            case "busy":
                return "🟡";
            case "overcrowded":
                return "🟠";
            case "closed":
                return "🔴";
            case "emergency_only":
                return "🚨";
            default:
                return "❓";
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
    }
    
    private void checkForExistingEmergencyNotifications() {
        Log.d(TAG, "🔍 Checking for existing emergency notifications...");
        
        if (userId == null) {
            Log.w(TAG, "Cannot check notifications - userId is null");
            return;
        }
        
        // Check for unread emergency notifications
        db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .document(userId)
          .collection("emergencyNotifications")
          .whereEqualTo("isRead", false)
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(5)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              Log.d(TAG, "Found " + querySnapshot.size() + " unread emergency notifications");
              for (QueryDocumentSnapshot document : querySnapshot) {
                  String type = document.getString("type");
                  String seniorName = document.getString("seniorName");
                  Long timestamp = document.getLong("timestamp");
                  Log.d(TAG, "Unread notification: " + type + " from " + seniorName + " at " + timestamp);
              }
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Error checking existing notifications: " + e.getMessage());
          });
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
            Uri notification = getCustomAlarmSound();
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
        String fullMessage = emergency.message + "\n\n" +
                "Senior: " + emergency.seniorName + "\n" +
                "Phone: " + (emergency.seniorPhone != null && !emergency.seniorPhone.isEmpty() ? emergency.seniorPhone : "Not provided") + "\n" +
                "Location: " + emergency.locationAddress + "\n" +
                "📍 Distance: " + emergency.getDistanceText() + "\n" +
                "⏰ Time in Queue: " + getTimeInQueueText(emergency.getTimeInQueue());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(fullMessage);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        // RESPOND button - most important action
        builder.setPositiveButton("🚑 RESPOND NOW", (dialog, which) -> {
            Log.d(TAG, "🚨 OLD SYSTEM: RESPOND NOW clicked for emergency: " + emergency.helpRequestId);
            clearEmergencyNotification(emergency.helpRequestId);
            respondToEmergency(emergency.helpRequestId, emergency.emergencyId);
            
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
        builder.setTitle(getString(R.string.text_fifo_emergency_queue));
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
        builder.setTitle(getString(R.string.text_emergency_summary));
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
        Log.d(TAG, "🚨 [RESCUER_DASHBOARD] Responding to emergency - Help Request ID: " + helpRequestId + ", Emergency ID: " + emergencyId);
        
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

                    // Show assignment popup
                    showAssignmentPopupForOldSystem(helpRequestId);

                    // Also update the rescuer's own document with current location for tracking
                    updateRescuerLocationForTracking();

                    // Send notification to senior about rescuer response
                    sendRescuerResponseNotificationToSenior(helpRequestId);

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
                                        .addOnSuccessListener(aVoid2 -> {
                                            Log.d(TAG, "Emergency timestamp updated to prevent re-showing");
                                            
                                        });
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating emergency response", e);
                    Toast.makeText(this, getString(R.string.error_recording_response), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void sendRescuerResponseNotificationToSenior(String helpRequestId) {
        Log.d(TAG, "📤 [RESCUER_DASHBOARD] Sending rescuer response notification to senior for help request: " + helpRequestId);
        Log.d(TAG, "📤 [RESCUER_DASHBOARD] Current user ID: " + userId);
        
        // First get the help request details to find the senior information
        db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .document(helpRequestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String seniorUid = documentSnapshot.getString("seniorUid");
                        String seniorName = documentSnapshot.getString("seniorName");
                        String seniorPhone = documentSnapshot.getString("seniorPhone");
                        String locationAddress = documentSnapshot.getString("locationAddress");
                        
                        Log.d(TAG, "📤 Help request details - Senior UID: " + seniorUid + ", Name: " + seniorName);
                        
                        if (seniorUid != null && !seniorUid.isEmpty()) {
                            // Get current rescuer information
                            String rescuerName = getCurrentRescuerName();
                            String rescuerPhone = getCurrentRescuerPhone();
                            String rescuerTeam = getCurrentRescuerTeam();
                            
                            // Create notification data for senior
                            Map<String, Object> rescuerResponseNotification = new HashMap<>();
                            rescuerResponseNotification.put("type", "RESCUER_RESPONSE");
                            rescuerResponseNotification.put("title", "🚑 Help is on the way! (Dashboard)");
                            rescuerResponseNotification.put("message", rescuerName + " from " + (rescuerTeam != null ? rescuerTeam : "Rescue Team") + " is responding to your emergency [via RescuerDashboard]");
                            rescuerResponseNotification.put("rescuerName", rescuerName);
                            rescuerResponseNotification.put("rescuerPhone", rescuerPhone);
                            rescuerResponseNotification.put("rescuerTeam", rescuerTeam);
                            rescuerResponseNotification.put("requestId", helpRequestId);
                            rescuerResponseNotification.put("locationAddress", locationAddress);
                            rescuerResponseNotification.put("timestamp", System.currentTimeMillis());
                            rescuerResponseNotification.put("isRead", false);
                            rescuerResponseNotification.put("isActive", true);
                            
                            // Send notification to senior's notification collection
                            String notificationPath = "Sagip/users/seniors/" + seniorUid + "/notifications";
                            Log.d(TAG, "📤 Sending notification to path: " + notificationPath);
                            Log.d(TAG, "📤 Notification data: " + rescuerResponseNotification.toString());
                            
                            db.collection(notificationPath)
                                    .add(rescuerResponseNotification)
                                    .addOnSuccessListener(documentReference -> {
                                        Log.d(TAG, "✅ Rescuer response notification sent to senior: " + seniorName);
                                        Log.d(TAG, "📱 Notification ID: " + documentReference.getId());
                                        Log.d(TAG, "📱 Notification details - Rescuer: " + rescuerName + ", Phone: " + rescuerPhone + ", Team: " + rescuerTeam);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "❌ Failed to send rescuer response notification to senior", e);
                                        Log.e(TAG, "❌ Error details: " + e.getMessage());
                                    });
                        } else {
                            Log.w(TAG, "⚠️ Senior UID not found in help request: " + helpRequestId);
                        }
                    } else {
                        Log.w(TAG, "⚠️ Help request not found: " + helpRequestId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting help request details for notification", e);
                });
    }
    
    private String getCurrentRescuerName() {
        // Get rescuer name from current user data
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        }
        
        // Fallback to user ID if no display name
        return userId != null ? "Rescuer " + userId.substring(0, Math.min(8, userId.length())) : "Unknown Rescuer";
    }
    
    private String getCurrentRescuerPhone() {
        // Get rescuer phone from current user data
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String phoneNumber = currentUser.getPhoneNumber();
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                return phoneNumber;
            }
        }
        
        // Fallback to a default phone number or get from user profile
        return "Not available";
    }
    
    private String getCurrentRescuerTeam() {
        // Get rescuer team from current user data
        // This would typically come from the user's profile in Firestore
        return "Emergency Response Team";
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
    
    private void startEmergencySOSListener() {
        if (userId == null) {
            Log.w(TAG, "Cannot start emergency SOS listener - userId is null");
            return;
        }
        
        Log.d(TAG, "🚨 Starting emergency SOS listener for rescuer: " + userId);
        
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
              
              if (querySnapshot != null && !querySnapshot.isEmpty()) {
                  for (QueryDocumentSnapshot document : querySnapshot) {
                      handleEmergencySOSNotification(document);
                  }
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
            
            // Only process unread emergency SOS notifications
            if ("EMERGENCY_SOS".equals(type) && (isRead == null || !isRead)) {
                Log.d(TAG, "🚨 Received emergency SOS notification: " + seniorName + " (Request ID: " + requestId + ")");
                
                // Show emergency alert dialog with request ID
                showEmergencySOSAlert(seniorName, seniorPhone, locationAddress, timestamp, requestId);
                
                // Mark notification as read
                document.getReference().update("isRead", true);
                
                // Note: System notification is now handled by EmergencySOSBackgroundService
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling emergency SOS notification: " + e.getMessage(), e);
        }
    }
    
    private void showEmergencySOSAlert(String seniorName, String seniorPhone, String locationAddress, Long timestamp) {
        showEmergencySOSAlert(seniorName, seniorPhone, locationAddress, timestamp, null);
    }
    
    private void showEmergencySOSAlert(String seniorName, String seniorPhone, String locationAddress, Long timestamp, String requestId) {
        // Check if activity is still valid before showing dialog
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show emergency alert dialog - activity is not in valid state");
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_emergency_sos_alert));
        
        String timeStr = "Unknown time";
        if (timestamp != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            timeStr = sdf.format(new java.util.Date(timestamp));
        }
        
        String message = "🚨 URGENT: Senior needs immediate help!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + seniorPhone + "\n" +
                        "📍 Location: " + locationAddress + "\n" +
                        "⏰ Time: " + timeStr + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        
        // Respond to emergency button
        builder.setPositiveButton(getString(R.string.button_respond_now), (dialog, which) -> {
            Log.d(TAG, "🚨 NEW SYSTEM: RESPOND NOW clicked for emergency: " + requestId);
            
            // Clear all emergency notifications and dialogs
            clearAllEmergencyNotifications();
            Log.d(TAG, "Cleared all emergency notifications and dialogs");
            
            // Dismiss dialog immediately
            dialog.dismiss();
            
            // Assign this rescuer to the emergency (this will launch Emergency Assignment Activity)
            if (requestId != null) {
                assignRescuerToEmergencyById(requestId);
            } else {
                assignRescuerToEmergency(seniorName, locationAddress, timestamp);
            }
            
            // Show confirmation to rescuer
            Toast.makeText(this, getString(R.string.toast_assigned_to_emergency), Toast.LENGTH_LONG).show();
        });
        
        // Call senior button
        builder.setNeutralButton(getString(R.string.button_call_senior), (dialog, which) -> {
            // Open phone dialer
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            startActivity(callIntent);
            dialog.dismiss();
        });
        
        // Dismiss button
        builder.setNegativeButton(getString(R.string.button_dismiss), (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Store reference to current emergency dialog
        currentEmergencyDialog = dialog;
        
        // Set white background for better readability
        dialog.getWindow().getDecorView().setBackgroundColor(0xFFFFFFFF); // White background
    }
    
    private void showEmergencySOSSystemNotification(String seniorName, String locationAddress, String notificationId) {
        Log.d(TAG, "🔔 Creating emergency SOS system notification for: " + seniorName);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent for when notification is tapped
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationIntent.putExtra("emergency_sos_clicked", true);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("location_address", locationAddress);
        
        // Create pending intent
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, 
                notificationId.hashCode(), 
                notificationIntent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "emergency_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.text_emergency_sos_title, seniorName))
                .setContentText(getString(R.string.text_emergency_sos_content, locationAddress))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000}) // Longer vibration pattern
                .setLights(0xFFFF0000, 1000, 1000) // Red light blinking
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        notificationManager.notify(notificationId.hashCode(), builder.build());
        Log.d(TAG, "Emergency SOS system notification sent for: " + seniorName);
    }
    
    
    private void openNavigationToSenior(String seniorName, String locationAddress) {
        try {
            // For now, open general navigation to the area
            // In a real implementation, you'd get the exact coordinates
            String destination = "Angeles City, Pampanga"; // Default to Angeles City
            
            Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + 
                Uri.encode(destination) + "&travelmode=driving");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(this, getString(R.string.toast_opening_navigation_to, seniorName), Toast.LENGTH_LONG).show();
            } else {
                // Fallback to web browser
                intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening navigation to senior: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.toast_error_opening_navigation, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void startEmergencySOSBackgroundService() {
        if (userType != null && userType.equals("rescuer")) {
            Log.d(TAG, "Starting EmergencySOSBackgroundService for rescuer");
            
            Intent serviceIntent = new Intent(this, EmergencySOSBackgroundService.class);
            serviceIntent.putExtra("action", "start_monitoring");
            
            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }
    
    
    private Uri getCustomAlarmSound() {
        try {
            // Try to use custom alarm sound
            Uri customSound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.emergency_alarm);
            Log.d(TAG, "Custom alarm sound URI: " + customSound.toString());
            Log.d(TAG, "Package name: " + getPackageName());
            Log.d(TAG, "Resource ID: " + R.raw.emergency_alarm);
            
            // Test if the resource exists
            try {
                android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.emergency_alarm);
                if (afd != null) {
                    afd.close();
                    Log.d(TAG, "✅ Custom alarm sound file exists and is accessible");
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Custom alarm sound file not accessible: " + e.getMessage());
            }
            
            return customSound;
        } catch (Exception e) {
            // Fallback to system alarm sound if custom file doesn't exist
            Log.w(TAG, "Custom alarm sound not found, using system alarm sound. Error: " + e.getMessage());
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
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
            Toast.makeText(this, getString(R.string.text_opening_dedicated_navigation, seniorName), Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, getString(R.string.text_opening_google_maps_to, seniorName), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e("Rescuer_Dashboard", "Error starting RescuerNavigationActivity", e);
                Toast.makeText(this, getString(R.string.text_error_opening_navigation_long, e.getMessage()), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, getString(R.string.text_destination_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.text_current_location_wait_long), Toast.LENGTH_LONG).show();
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
        builder.setTitle(getString(R.string.text_emergency_response_details));
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
                .setSound(getCustomAlarmSound())
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
                    NotificationManager.IMPORTANCE_MAX
            );
            channel.setDescription("Emergency help requests from seniors");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
            
            // Set custom alarm sound with proper audio attributes
            Uri alarmSound = getCustomAlarmSound();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // Ensure sound plays even in silent mode
                    .build();
            channel.setSound(alarmSound, audioAttributes);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // =============== HOSPITAL NAVIGATION ===============

    private void testNavigationToChristInYouHealeParish() {
        Log.d("Rescuer_Dashboard", "Testing Navigation to Christ in You Heale Parish");
        
        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.text_test_navigation));
        builder.setMessage(getString(R.string.text_test_navigation_message));
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
                        Toast.makeText(this, getString(R.string.text_could_not_get_location), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, getString(R.string.text_error_getting_location, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, getString(R.string.text_location_permission_required), Toast.LENGTH_SHORT).show();
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
                      Toast.makeText(this, getString(R.string.text_testing_navigation_to, hospitalName), Toast.LENGTH_LONG).show();
                  } else {
                      Toast.makeText(this, getString(R.string.text_hospital_location_not_found), Toast.LENGTH_SHORT).show();
                  }
              } else {
                  Toast.makeText(this, getString(R.string.text_hospital_not_found, hospitalName), Toast.LENGTH_SHORT).show();
              }
          })
          .addOnFailureListener(e -> {
              Log.e("Rescuer_Dashboard", "Error retrieving hospital location from database", e);
              Toast.makeText(this, getString(R.string.text_error_retrieving_hospital, e.getMessage()), Toast.LENGTH_SHORT).show();
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
        Log.d(TAG, "🔐 Checking authentication state...");

        // Always check Firebase Auth first to ensure user is still authenticated
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Log.d(TAG, "Firebase currentUser: " + (currentUser != null ? currentUser.getUid() : "null"));

        if (currentUser != null) {
            // User is authenticated in Firebase
            userId = currentUser.getUid();
            String phoneNumber = currentUser.getPhoneNumber();
            
            // Debug information
            Log.d(TAG, "✅ User authenticated in Firebase");
            Log.d(TAG, "User ID: " + userId);
            Log.d(TAG, "Phone Number: " + phoneNumber);
            Log.d(TAG, "Email: " + currentUser.getEmail());

            // Always detect user type from database for consistency across devices
            // This ensures the same behavior regardless of SharedPreferences state
            Log.d(TAG, "Detecting user type from database for consistency...");
            detectAndLoadUserType(userId, phoneNumber);
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

        // Add timeout handling
        android.os.Handler timeoutHandler = new android.os.Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            Log.w(TAG, "Database query timeout, showing retry dialog");
            showRetryDialog("Request timed out. Please check your internet connection and try again.");
        };
        
        // Set 10-second timeout
        timeoutHandler.postDelayed(timeoutRunnable, 10000);

        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    // Cancel timeout since we got a response
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        Log.d(TAG, "User status: " + status);
                        Log.d(TAG, "Document data: " + documentSnapshot.getData());
                        
                        if ("new".equals(status)) {
                            Log.d(TAG, "User status is 'new', redirecting to registration");
                            // User status is "new", redirect to registration
                            Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else if (status == null || status.isEmpty()) {
                            Log.w(TAG, "User status is null or empty, treating as registered");
                            // Status is null/empty, treat as registered and proceed
                            loadUserData(userId);
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
                    // Cancel timeout since we got an error
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    Log.e(TAG, "Error checking user status: " + e.getMessage(), e);
                    // Instead of immediately redirecting on error, show a retry dialog
                    showRetryDialog("Unable to verify your registration status. Please check your internet connection and try again.");
                });
    }

    private void showRetryDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Connection Error")
                .setMessage(message)
                .setPositiveButton("Retry", (dialog, which) -> {
                    // Retry the status check
                    checkUserStatusAndRedirect();
                })
                .setNegativeButton("Go to Registration", (dialog, which) -> {
                    // User chooses to go to registration
                    Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
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
        Log.d(TAG, "🔍 Starting user type detection...");
        Log.d(TAG, "UID: " + uid);
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "Firebase User: " + (mAuth.getCurrentUser() != null ? "Available" : "Null"));

        // Always check UID-based first for rescuer users to ensure consistency
        // This prevents device-specific behavior based on phone number availability
        // Use consistent order: rescuer first, then others
        String[] uidUserTypes = {"rescuer", "hospital", "barangay", "seniors"};
        Log.d(TAG, "Checking UID-based collections first for consistency...");
        Log.d(TAG, "Collections to check: " + java.util.Arrays.toString(uidUserTypes));
        checkUIDBasedUserTypes(uid, uidUserTypes, 0);
    }

    private void checkPhoneBasedUserTypes(String uid, String phoneNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.d(TAG, "Phone-based user not found, all search methods exhausted");
            showUserNotFoundError();
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
            Log.e(TAG, "User not found in any collection after checking all types");
            Log.e(TAG, "UID: " + uid + ", Collections checked: " + java.util.Arrays.toString(userTypes));
            
            // Try alternative search methods before giving up
            Log.d(TAG, "Trying alternative search methods...");
            tryAlternativeUserSearch(uid);
            return;
        }

        String currentUserType = userTypes[index];
        Log.d(TAG, "Checking UID-based user type: " + currentUserType + " for UID: " + uid);

        db.collection("Sagip")
                .document("users")
                .collection(currentUserType)
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        Log.d(TAG, "✅ User found in UID-based collection: " + currentUserType);
                        Log.d(TAG, "Document data: " + document.getData());
                        this.userType = currentUserType;
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        String phoneNumber = currentUser != null ? currentUser.getPhoneNumber() : null;
                        saveUserToPreferences(uid, currentUserType, phoneNumber);
                        loadUserDataFromDocument(document);

                        // Emergency listener will be started in onResume()
                    } else {
                        Log.d(TAG, "❌ User not found in collection: " + currentUserType);
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
        // Load cached name immediately for instant display
        loadCachedDisplayName();

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
            // Cache the name for future instant loading
            cacheDisplayName(displayName);
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

    private void tryAlternativeUserSearch(String uid) {
        Log.d(TAG, "Trying alternative user search for UID: " + uid);
        
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No Firebase user available for alternative search");
            showUserNotFoundError();
            return;
        }
        
        String phoneNumber = currentUser.getPhoneNumber();
        if (phoneNumber != null) {
            Log.d(TAG, "Trying phone-based search with number: " + phoneNumber);
            // Try phone-based search as fallback
            String[] phoneUserTypes = {"rescuer", "seniors", "barangay", "hospital"};
            checkPhoneBasedUserTypes(uid, phoneNumber, phoneUserTypes, 0);
        } else {
            Log.e(TAG, "No phone number available for alternative search");
            showUserNotFoundError();
        }
    }
    
    private void showUserNotFoundError() {
        Log.e(TAG, "User not found after all search methods");
        Toast.makeText(this, getString(R.string.user_profile_not_found), Toast.LENGTH_LONG).show();
        clearStoredCredentials();
        mAuth.signOut();
        navigateToLogin();
    }

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials...");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        editor.apply();
        Log.d(TAG, "All stored credentials cleared");
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
                        Toast.makeText(this, getString(R.string.text_could_not_get_location), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, getString(R.string.text_error_getting_location, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, getString(R.string.text_location_permission_required), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, getString(R.string.text_could_not_get_location), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, getString(R.string.text_error_getting_location, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, getString(R.string.text_location_permission_required), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadCachedDisplayName() {
        String cachedName = sharedPreferences.getString(KEY_CACHED_DISPLAY_NAME, null);
        if (cachedName != null && !cachedName.isEmpty()) {
            brgyName.setText(cachedName);
            Log.d(TAG, "Loaded cached display name: " + cachedName);
        } else {
            brgyName.setText("Loading...");
            Log.d(TAG, "No cached display name found, showing loading...");
        }
    }

    private void cacheDisplayName(String displayName) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_DISPLAY_NAME, displayName)
                .apply();
        Log.d(TAG, "Cached display name: " + displayName);
    }
    
    // Multiple Emergency Handling Methods
    private void showMultipleEmergenciesAlert(List<EmergencyQueueManager.EmergencyRequest> emergencies) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 MULTIPLE EMERGENCIES DETECTED");
        
        StringBuilder message = new StringBuilder();
        message.append("🚨 ").append(emergencies.size()).append(" active emergencies detected!\n\n");
        message.append("📋 Emergency Queue (FIFO):\n\n");
        
        for (int i = 0; i < emergencies.size(); i++) {
            EmergencyQueueManager.EmergencyRequest emergency = emergencies.get(i);
            String status = emergency.status.equals("pending") ? "⏳ PENDING" : "👤 ASSIGNED";
            String timeStr = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new java.util.Date(emergency.timestamp));
            
            message.append((i + 1)).append(". ").append(emergency.seniorName)
                   .append(" - ").append(status).append(" (⏰ ").append(timeStr).append(")\n");
        }
        
        message.append("\n⚠️ Please respond to the first emergency in queue!");
        
        builder.setMessage(message.toString());
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        // View all emergencies button
        builder.setPositiveButton("📋 VIEW ALL", (dialog1, which) -> {
            showEmergencyListDialog(emergencies);
        });
        
        // Handle first in queue button
        if (!emergencies.isEmpty()) {
            EmergencyQueueManager.EmergencyRequest firstInQueue = emergencies.get(0);
            builder.setNeutralButton("🚨 HANDLE #1", (dialog1, which) -> {
                showEmergencySOSAlert(
                    firstInQueue.seniorName,
                    firstInQueue.seniorPhone,
                    firstInQueue.locationAddress,
                    firstInQueue.timestamp
                );
            });
        }
        
        // Cancel button
        builder.setNegativeButton("CANCEL", (dialog1, which) -> {
            dialog1.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showEmergencyListDialog(List<EmergencyQueueManager.EmergencyRequest> emergencies) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📋 Emergency Queue (" + emergencies.size() + " active)");
        
        // Create list items
        String[] items = new String[emergencies.size()];
        for (int i = 0; i < emergencies.size(); i++) {
            EmergencyQueueManager.EmergencyRequest emergency = emergencies.get(i);
            String status = emergency.status.equals("pending") ? "PENDING" : "ASSIGNED";
            String timeStr = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new java.util.Date(emergency.timestamp));
            items[i] = (i + 1) + ". " + emergency.seniorName + " - " + status + " (⏰ " + timeStr + ")";
        }
        
        builder.setItems(items, (dialog, which) -> {
            EmergencyQueueManager.EmergencyRequest selected = emergencies.get(which);
            showEmergencySOSAlert(
                selected.seniorName,
                selected.seniorPhone,
                selected.locationAddress,
                selected.timestamp
            );
        });
        
        builder.setNegativeButton("CLOSE", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void assignRescuerToEmergency(String seniorName, String locationAddress, Long timestamp) {
        // Get current rescuer ID
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            return;
        }
        
        String rescuerId = currentUser.getUid();
        Log.d(TAG, "🔍 Looking for emergency to assign rescuer: " + rescuerId);
        Log.d(TAG, "🔍 Searching for: " + seniorName + " at " + locationAddress + " at " + timestamp);
        
        // Find the emergency request by senior name and timestamp
        List<EmergencyQueueManager.EmergencyRequest> activeEmergencies = 
                EmergencyQueueManager.getInstance(this).getActiveEmergencies();
        
        Log.d(TAG, "🔍 Found " + activeEmergencies.size() + " active emergencies");
        
        boolean found = false;
        for (EmergencyQueueManager.EmergencyRequest emergency : activeEmergencies) {
            Log.d(TAG, "🔍 Checking emergency: " + emergency.seniorName + " at " + emergency.locationAddress + " at " + emergency.timestamp);
            Log.d(TAG, "🔍 Name match: " + emergency.seniorName.equals(seniorName));
            Log.d(TAG, "🔍 Location match: " + emergency.locationAddress.equals(locationAddress));
            Log.d(TAG, "🔍 Time diff: " + Math.abs(emergency.timestamp - timestamp) + " (threshold: 60000)");
            
            if (emergency.seniorName.equals(seniorName) && 
                emergency.locationAddress.equals(locationAddress) &&
                Math.abs(emergency.timestamp - timestamp) < 60000) { // Within 1 minute
                
                // Assign this rescuer to the emergency
                EmergencyQueueManager.getInstance(this).assignRescuer(emergency.requestId, rescuerId);
                
                // Show popup confirmation to rescuer
                showRescuerAssignmentPopup(seniorName, locationAddress, rescuerId, emergency.requestId);
                
                Log.d(TAG, "👤 Rescuer " + rescuerId + " assigned to emergency: " + emergency.requestId);
                found = true;
                break;
            }
        }
        
        if (!found) {
            Log.w(TAG, "⚠️ No matching emergency found for assignment");
            Toast.makeText(this, "⚠️ Emergency not found in queue", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void assignRescuerToEmergencyById(String requestId) {
        // Get current rescuer ID
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            return;
        }
        
        String rescuerId = currentUser.getUid();
        Log.d(TAG, "🔍 Assigning rescuer " + rescuerId + " to emergency: " + requestId);
        
        // First try to get the emergency from local EmergencyQueueManager
        EmergencyQueueManager.EmergencyRequest emergency = 
                EmergencyQueueManager.getInstance(this).getEmergencyById(requestId);
        
        if (emergency != null) {
            // Emergency found in local queue, assign rescuer
            EmergencyQueueManager.getInstance(this).assignRescuer(requestId, rescuerId);
            
            // Show popup confirmation to rescuer
            showRescuerAssignmentPopup(emergency.seniorName, emergency.locationAddress, rescuerId, requestId);
            
            Log.d(TAG, "👤 Rescuer " + rescuerId + " assigned to emergency: " + requestId);
        } else {
            // Emergency not found in local queue, try to load from database
            Log.d(TAG, "⚠️ Emergency not found in local queue, loading from database...");
            loadEmergencyFromDatabaseAndAssign(requestId, rescuerId);
        }
    }
    
    private void loadEmergencyFromDatabaseAndAssign(String requestId, String rescuerId) {
        // Load emergency from database using EmergencyQueueManager
        EmergencyQueueManager.getInstance(this).loadEmergencyByIdFromDatabase(requestId, new EmergencyQueueManager.EmergencyLoadCallback() {
            @Override
            public void onEmergencyLoaded(EmergencyQueueManager.EmergencyRequest emergency) {
                if (emergency != null) {
                    // Assign rescuer to emergency
                    EmergencyQueueManager.getInstance(Rescuer_Dashboard.this).assignRescuer(requestId, rescuerId);
                    
                    // Show popup confirmation to rescuer
                    showRescuerAssignmentPopup(emergency.seniorName, emergency.locationAddress, rescuerId, requestId);
                    
                    Log.d(TAG, "👤 Rescuer " + rescuerId + " assigned to emergency from database: " + requestId);
                } else {
                    Log.w(TAG, "⚠️ Emergency not found in database with ID: " + requestId);
                    Toast.makeText(Rescuer_Dashboard.this, "⚠️ Emergency not found in database", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    private void showRescuerAssignmentPopup(String seniorName, String locationAddress, String rescuerId, String requestId) {
        Log.d(TAG, "🎉 showRescuerAssignmentPopup called for: " + seniorName + " at " + locationAddress + " (requestId: " + requestId + ")");
        
        // Check if activity is still valid before showing dialog
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show assignment popup - activity is not in valid state");
            return;
        }
        
        // Launch the new Emergency Assignment Activity
        launchEmergencyAssignmentActivity(seniorName, locationAddress, rescuerId, requestId);
    }
    
    private void launchEmergencyAssignmentActivity(String seniorName, String locationAddress, String rescuerId, String requestId) {
        Intent intent = new Intent(this, EmergencyAssignmentActivity.class);
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("location_address", locationAddress);
        intent.putExtra("rescuer_id", rescuerId);
        intent.putExtra("assignment_time", System.currentTimeMillis());
        intent.putExtra("request_id", requestId);
        
        if (requestId != null) {
            // Get emergency data to extract senior's location and phone
            EmergencyQueueManager.EmergencyRequest emergency = EmergencyQueueManager.getInstance(this).getEmergencyById(requestId);
            
            if (emergency != null) {
                // Debug: Check emergency data
                Log.d(TAG, "🔍 Emergency data debug:");
                Log.d(TAG, "  - requestId: " + emergency.requestId);
                Log.d(TAG, "  - seniorName: " + emergency.seniorName);
                Log.d(TAG, "  - seniorPhone: " + emergency.seniorPhone);
                Log.d(TAG, "  - locationAddress: " + emergency.locationAddress);
                
                // Use phone number from emergency data first (same as senior dashboard)
                String emergencyPhone = emergency.seniorPhone != null ? emergency.seniorPhone : "Not available";
                intent.putExtra("senior_phone", emergencyPhone);
                Log.d(TAG, "📞 Using phone from emergency data: " + emergencyPhone);
                
                // Get senior's coordinates from database
                getSeniorLocationAndLaunch(intent, emergency, rescuerId);
            } else {
                // Fallback if emergency not found in local cache
                intent.putExtra("senior_phone", "Not available");
        intent.putExtra("senior_lat", 0.0);
        intent.putExtra("senior_lng", 0.0);
        
        // Generate emergency ID for tracking
        String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
        intent.putExtra("emergency_id", emergencyId);
                
                // Debug: Check intent before launching
                String phoneInIntent = intent.getStringExtra("senior_phone");
                Log.d(TAG, "🔍 Phone in intent before launch (fallback): " + phoneInIntent);
        
        startActivity(intent);
                Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + seniorName + " (fallback mode)");
            }
        } else {
            // Old system - no requestId available
            intent.putExtra("senior_phone", "Not available");
            intent.putExtra("senior_lat", 0.0);
            intent.putExtra("senior_lng", 0.0);
            
            // Generate emergency ID for tracking
            String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
            intent.putExtra("emergency_id", emergencyId);
            
            // Debug: Check intent before launching
            String phoneInIntent = intent.getStringExtra("senior_phone");
            Log.d(TAG, "🔍 Phone in intent before launch (old system): " + phoneInIntent);
            
            startActivity(intent);
            Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + seniorName + " (old system)");
        }
    }
    
    private void getSeniorLocationAndLaunch(Intent intent, EmergencyQueueManager.EmergencyRequest emergency, String rescuerId) {
        // Get senior's current location and phone number from database
        String seniorUserId = extractUserIdFromRequestId(emergency.requestId);
        if (seniorUserId != null) {
            db.collection("Sagip/users/seniors")
                    .document(seniorUserId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Get location coordinates
                            Double lat = documentSnapshot.getDouble("latitude");
                            Double lng = documentSnapshot.getDouble("longitude");
                            
                            if (lat != null && lng != null) {
                                intent.putExtra("senior_lat", lat);
                                intent.putExtra("senior_lng", lng);
                                Log.d(TAG, "📍 Got senior location from database: " + lat + ", " + lng);
                            } else {
                                // Fallback: Use a default location (you can change this to your city's coordinates)
                                double defaultLat = 14.5995; // Manila coordinates as fallback
                                double defaultLng = 120.9842;
                                intent.putExtra("senior_lat", defaultLat);
                                intent.putExtra("senior_lng", defaultLng);
                                Log.w(TAG, "⚠️ Senior location not available in database, using fallback: " + defaultLat + ", " + defaultLng);
                            }
                            
                            // Phone number already set from emergency data, just log database phone for comparison
                            String databasePhone = documentSnapshot.getString("mobileNumber");
                            Log.d(TAG, "📞 Database phone (for comparison): " + databasePhone);
                            Log.d(TAG, "📞 Using emergency data phone (already set)");
                        } else {
                            // Fallback: Use a default location
                            double defaultLat = 14.5995; // Manila coordinates as fallback
                            double defaultLng = 120.9842;
                            intent.putExtra("senior_lat", defaultLat);
                            intent.putExtra("senior_lng", defaultLng);
                            Log.w(TAG, "⚠️ Senior document not found, using fallback location: " + defaultLat + ", " + defaultLng);
                        }
                        
                        // Generate emergency ID for tracking
                        String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
                        intent.putExtra("emergency_id", emergencyId);
                        
                        // Debug: Check intent before launching
                        String phoneInIntent = intent.getStringExtra("senior_phone");
                        Log.d(TAG, "🔍 Phone in intent before launch: " + phoneInIntent);
                        
                        startActivity(intent);
                        Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + emergency.seniorName + " with location and phone");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to get senior data: " + e.getMessage());
                        // Fallback: Use a default location
                        double defaultLat = 14.5995; // Manila coordinates as fallback
                        double defaultLng = 120.9842;
                        intent.putExtra("senior_lat", defaultLat);
                        intent.putExtra("senior_lng", defaultLng);
                        Log.w(TAG, "⚠️ Database query failed, using fallback location: " + defaultLat + ", " + defaultLng);
                        
                        String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
                        intent.putExtra("emergency_id", emergencyId);
                        
                        // Debug: Check intent before launching
                        String phoneInIntent = intent.getStringExtra("senior_phone");
                        Log.d(TAG, "🔍 Phone in intent before launch (failed): " + phoneInIntent);
                        
                        startActivity(intent);
                        Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + emergency.seniorName + " (data failed)");
                    });
        } else {
            // No senior user ID, use fallback
            double defaultLat = 14.5995; // Manila coordinates as fallback
            double defaultLng = 120.9842;
            intent.putExtra("senior_lat", defaultLat);
            intent.putExtra("senior_lng", defaultLng);
            Log.w(TAG, "⚠️ No senior user ID, using fallback location: " + defaultLat + ", " + defaultLng);
            
            String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
            intent.putExtra("emergency_id", emergencyId);
            
            // Debug: Check intent before launching
            String phoneInIntent = intent.getStringExtra("senior_phone");
            Log.d(TAG, "🔍 Phone in intent before launch (no user ID): " + phoneInIntent);
            
            startActivity(intent);
            Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + emergency.seniorName + " (no user ID)");
        }
    }
    
    private String extractUserIdFromRequestId(String requestId) {
        // Request ID format: "SOS_timestamp_userId"
        String[] parts = requestId.split("_");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
    
    private void showAssignmentPopupForOldSystem(String helpRequestId) {
        // Get emergency details from the help request
        db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .document(helpRequestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String seniorName = documentSnapshot.getString("seniorName");
                        String locationAddress = documentSnapshot.getString("locationAddress");
                        String rescuerId = mAuth.getCurrentUser().getUid();
                        
                        if (seniorName != null && locationAddress != null) {
                            // Show the assignment popup
                            showRescuerAssignmentPopup(seniorName, locationAddress, rescuerId, null);
                        } else {
                            Log.w(TAG, "⚠️ Missing senior name or location for popup");
                        }
                    } else {
                        Log.w(TAG, "⚠️ Help request document not found: " + helpRequestId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting help request details: " + e.getMessage());
                });
    }
    
}