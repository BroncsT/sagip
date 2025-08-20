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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

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
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Rescuer_Dashboard extends AppCompatActivity {

    private static final String TAG = "RescuerDashboard";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
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
    private Vibrator vibrator;
    private long lastLoginTime; // Track when rescuer logged in

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
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

        // Start emergency listener when activity resumes
        if (emergencyListener == null) {
            startEmergencyListener();
        }

        // Clear any old notifications when app comes to foreground
        clearOldEmergencyNotifications();
        
        // Clear any emergency notifications when returning to dashboard
        clearAllEmergencyNotifications();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        
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
            Toast.makeText(this, "Emergency notification cleared", Toast.LENGTH_SHORT).show();
            
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

        // Clear stored credentials
        clearStoredCredentials();

        // Navigate to login
        navigateToLogin();
    }

    // =============== EMERGENCY NOTIFICATION SYSTEM ===============

    private void initializeEmergencyNotificationComponents() {
        // Initialize vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        Log.d(TAG, "Emergency notification components initialized");
    }

    private void startEmergencyListener() {
        Log.d(TAG, "Starting emergency listener...");

        // Clean up old emergencies first (older than 1 hour)
        cleanupOldEmergencies();

        // Listen for new emergency notifications
        emergencyListener = db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Emergency listener failed.", e);
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // New emergency detected!
                                DocumentSnapshot emergency = dc.getDocument();
                                handleNewEmergency(emergency);
                            } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                                // Emergency was modified (likely responded to by another rescuer)
                                DocumentSnapshot emergency = dc.getDocument();
                                Boolean isActive = emergency.getBoolean("isActive");
                                if (isActive != null && !isActive) {
                                    // Emergency was deactivated, clear the notification
                                    String helpRequestId = emergency.getString("helpRequestId");
                                    if (helpRequestId != null) {
                                        clearEmergencyNotification(helpRequestId);
                                        Log.d(TAG, "Emergency was responded to by another rescuer, clearing notification");
                                    }
                                }
                            }
                        }
                    }
                });

        Log.d(TAG, "Emergency listener started successfully");
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

        // Vibrate the device
        vibrateDevice();

        // Play notification sound
        playNotificationSound();

        // Show emergency alert dialog
        showEmergencyAlert(title, message, seniorName, seniorPhone, locationAddress,
                latitude, longitude, helpRequestId, emergency.getId());

        // Show system notification
        showSystemNotification(title, message + " - " + locationAddress, helpRequestId);
    }

    private void vibrateDevice() {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Vibration pattern: wait 0ms, vibrate 1000ms, wait 500ms, vibrate 1000ms
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 500, 1000}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 1000, 500, 1000}, -1);
            }
        }
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

    private void showEmergencyAlert(String title, String message, String seniorName,
                                    String seniorPhone, String locationAddress, Double latitude,
                                    Double longitude, String helpRequestId, String emergencyId) {

        String fullMessage = message + "\n\n" +
                "Senior: " + seniorName + "\n" +
                "Phone: " + (seniorPhone != null && !seniorPhone.isEmpty() ? seniorPhone : "Not provided") + "\n" +
                "Location: " + locationAddress;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(fullMessage);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        // RESPOND button - most important action
        builder.setPositiveButton("🚑 RESPOND NOW", (dialog, which) -> {
            clearEmergencyNotification(helpRequestId);
            respondToEmergency(helpRequestId, emergencyId);
            openExternalGoogleMapsNavigation(latitude, longitude, locationAddress, seniorName, seniorPhone, helpRequestId);
            dialog.dismiss();
        });

        // GET ROUTE button - opens external Google Maps with navigation
        builder.setNeutralButton("🗺️ GET ROUTE", (dialog, which) -> {
            clearEmergencyNotification(helpRequestId);
            openExternalGoogleMapsNavigation(latitude, longitude, locationAddress, seniorName, seniorPhone, helpRequestId);
            dialog.dismiss();
        });

        // Call button - if phone number available
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            builder.setNegativeButton("📞 CALL", (dialog, which) -> {
                clearEmergencyNotification(helpRequestId);
                callSenior(seniorPhone);
                dialog.dismiss();
            });
        }

        // Make dialog not cancelable so rescuer must choose an action
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Make RESPOND button red and larger
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
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
                    Toast.makeText(this, "✅ Response recorded - Help is on the way!", Toast.LENGTH_LONG).show();

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
                    Toast.makeText(this, "Error recording response", Toast.LENGTH_SHORT).show();
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
    }

    private void openLocationInInternalMap(Double latitude, Double longitude, String address,
                                           String seniorName, String seniorPhone, String helpRequestId) {
        if (latitude != null && longitude != null) {
            Intent mapIntent = new Intent(this, MyGoogleMAp.class);

            // Use consistent extra names that match MyGoogleMAp expectations
            mapIntent.putExtra("latitude", latitude);
            mapIntent.putExtra("longitude", longitude);
            mapIntent.putExtra("locationAddress", address);
            mapIntent.putExtra("isRescuerMode", true);
            mapIntent.putExtra("seniorName", seniorName);
            mapIntent.putExtra("seniorPhone", seniorPhone != null ? seniorPhone : "");
            mapIntent.putExtra("helpRequestId", helpRequestId);
            mapIntent.putExtra("emergencyDescription", "Senior needs immediate assistance");

            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Emergency location not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void callSenior(String phoneNumber) {
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        startActivity(callIntent);
    }

    private void openGoogleMapsNavigation(Double latitude, Double longitude, String destinationAddress) {
        if (latitude == null || longitude == null) {
            Toast.makeText(this, "Destination location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, "Your current location is not available yet. Please wait for location update.", Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "Opening Google Maps navigation to " + destinationAddress, Toast.LENGTH_SHORT).show();
            } else {
                // Fallback to web browser if Google Maps app is not installed
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUrl));
                startActivity(webIntent);
                Toast.makeText(this, "Opening navigation in browser to " + destinationAddress, Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Maps navigation", e);
            Toast.makeText(this, "Error opening navigation", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "🚗 Opening Google Maps navigation to " + seniorName, Toast.LENGTH_LONG).show();
                
                // Also show a dialog with emergency details
                showEmergencyDetailsDialog(seniorName, seniorPhone, destinationAddress, helpRequestId);
            } else {
                // Fallback to web-based Google Maps
                String webMapsUri = String.format("https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving", 
                    latitude, longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webMapsUri));
                startActivity(webIntent);
                Toast.makeText(this, "🌐 Opening web-based navigation to " + seniorName, Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Maps navigation", e);
            Toast.makeText(this, "Error opening navigation", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
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

    private void navigateToNearestHospital() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, "Current location not available. Please wait or check permissions.",
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
                loadUserData(userId);
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

                                // Start emergency listener after user data is loaded
                                startEmergencyListener();
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
        String[] phoneUserTypes = {"rescuer", "seniors", "user", "admin"};

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

        db.collection("Sagip")
                .document("users")
                .collection(currentUserType)
                .whereEqualTo("mobileNumber", phoneNumber)
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
            Toast.makeText(this, "User profile not found. Please login again.", Toast.LENGTH_LONG).show();
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

                        // Start emergency listener after user data is loaded
                        startEmergencyListener();
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
            brgyName.setText("User Name Not Available");
        }

        // Check if there's stored location data
        GeoPoint geoPoint = document.getGeoPoint("currentLocation");
        if (geoPoint != null) {
            currentLat = geoPoint.getLatitude();
            currentLong = geoPoint.getLongitude();
            updateLocationDisplay(currentLat, currentLong);
        }
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
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                startLocationUpdates();
            } else {
                // Permission denied, show a message
                Toast.makeText(this, "Location permission denied. Unable to update location.",
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
                Toast.makeText(this, "Error starting location updates", Toast.LENGTH_SHORT).show();
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
}