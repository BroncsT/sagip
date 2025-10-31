package com.example.sagip_prototype;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Senior_Dashboard extends AppCompatActivity {

    private static final String TAG = "SeniorDashboard";
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_PHONE = "userPhone";
    private static final String KEY_CACHED_FULL_NAME = "cachedFullName";
    private static final String KEY_NOTIFICATION_TOAST_SHOWN = "notificationToastShown";
    
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    TextView tvFullName, tvCurrentLocation;
    Button btnFindHospital, btnSOS;
    
    
    // Emergency tracking
    private String currentEmergencyId = null;
    private String currentRescuerId = null;
    private String currentRescuerPhone = null;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesActive = false;
    private double currentLat = 0.0;
    private double currentLong = 0.0;
    private String currentLocationAddress = "";
    private String currentBarangay = "";

    // Broadcast receiver for immediate popup
    private BroadcastReceiver rescuerAcceptedReceiver;

    private ActivityResultLauncher<String[]> locationPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        Log.d(TAG, "🌐 Saved language preference: " + savedLanguage);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        Log.d(TAG, "🌐 Language set to: " + savedLanguage);
        
        // Apply saved font size preference
        FontSizeHelper.applyFontSize(this);
        
        setContentView(R.layout.activity_senior_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Check authentication state with persistence
        checkAuthStateWithPersistence();

        initializeViews();
        
        // Load cached name immediately after views are initialized
        loadCachedName();
        
        // Initialize location services and data loading
        initializeLocationServices();
        registerLocationPermissionLauncher();
        loadUserData();
        setupBottomNavigation();
        requestLocationPermissions();
        
        // Test location immediately
        testCurrentLocation();
        
        // Start listening for rescuer response notifications immediately
        SeniorNotificationService.getInstance(this).startListening();
        
        
        // Test functionality removed - notification system is working
        
        // Check and request notification permission
        checkAndRequestNotificationPermission();
        
        // Register for FCM notifications
        registerForFCMNotifications();
        
        // Register broadcast receiver for immediate popup
        registerRescuerAcceptedReceiver();
        
        
        // Handle rescuer response notification if app was opened from notification
        // Add a delay to ensure UI is fully loaded
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            handleRescuerResponseNotification(getIntent());
        }, 1000); // 1 second delay to ensure UI is fully loaded
    }

    private void initializeViews() {
        tvFullName = findViewById(R.id.seniorName);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        btnSOS = findViewById(R.id.sosButton);
        
        // Add test button for debugging senior notifications
        btnSOS.setOnClickListener(v -> showSOSConfirmationDialog());
        
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.senior_home);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.senior_home) {
                return true;
            } else if (itemId == R.id.senior_profile) {
                startActivity(new Intent(getApplicationContext(), Senior_Profile.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.senior_location) {
                startActivity(new Intent(getApplicationContext(), Senior_Emergency_Contact.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }


    // New method to open Senior_GoogleMap with current location
    private void openMyGoogleMapWithLocation() {
        try {
            Intent mapIntent = new Intent(Senior_Dashboard.this, Senior_GoogleMap.class);

            // Pass current location data to Senior_GoogleMap
            mapIntent.putExtra("latitude", currentLat);
            mapIntent.putExtra("longitude", currentLong);
            mapIntent.putExtra("locationAddress", currentLocationAddress);
            mapIntent.putExtra("seniorName", tvFullName.getText().toString());

            startActivity(mapIntent);
            Log.d(TAG, "Opened Senior_GoogleMap with current location");

        } catch (Exception e) {
            Log.e(TAG, "Error opening Senior_GoogleMap", e);
            Toast.makeText(this, getString(R.string.toast_error_opening_map), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSOSConfirmationDialog() {
        // Get senior information
        String seniorName = tvFullName.getText().toString();
        String currentLocation = tvCurrentLocation.getText().toString();
        
        // Get phone number from Firebase Auth
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String phoneNumber = getString(R.string.text_not_available);
        
        if (currentUser != null) {
            phoneNumber = currentUser.getPhoneNumber();
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                phoneNumber = getString(R.string.text_not_provided);
            }
        }
        
        // Show confirmation dialog with senior information
        showSOSDialogWithInfo(seniorName, currentLocation, phoneNumber);
    }
    
    private void showSOSDialogWithInfo(String seniorName, String currentLocation, String phoneNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_emergency_help_request));
        
        // Create detailed message with senior information
        String message = getString(R.string.dialog_emergency_help_message, seniorName, currentLocation, phoneNumber);
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        // Send Help button
        builder.setPositiveButton(getString(R.string.button_send_help), (dialog, which) -> {
            dialog.dismiss();
            sendSOSRequest(seniorName, phoneNumber);
        });
        
        // Cancel button
        builder.setNegativeButton(getString(R.string.button_cancel), (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(Senior_Dashboard.this, getString(R.string.toast_help_request_cancelled), Toast.LENGTH_SHORT).show();
        });
        
        // Make dialog non-cancelable by back button for safety
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        
        // Style the buttons
        dialog.setOnShowListener(dialogInterface -> {
            try {
                // Make the positive button red to indicate emergency
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
            } catch (Exception e) {
                Log.e(TAG, "Error styling dialog buttons", e);
            }
        });
        
        dialog.show();
    }
    
    private void sendSOSRequest(String seniorName, String phoneNumber) {
        Log.d(TAG, "SOS request sent for: " + seniorName);
        
        // Get current location before creating emergency
        getCurrentLocationAndSendSOS(seniorName, phoneNumber);
    }
    
    private void getCurrentLocationAndSendSOS(String seniorName, String phoneNumber) {
        Log.d(TAG, "🔍 Getting current location for SOS - current values: " + currentLat + ", " + currentLong);
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "⚠️ No location permission, using last known location");
            createEmergencyWithLocation(seniorName, phoneNumber, currentLat, currentLong);
            return;
        }
        
        // If we already have a valid location, use it
        if (currentLat != 0.0 && currentLong != 0.0) {
            Log.d(TAG, "📍 Using current location values: " + currentLat + ", " + currentLong);
            createEmergencyWithLocation(seniorName, phoneNumber, currentLat, currentLong);
            return;
        }
        
        // Try to get last known location first
        Log.d(TAG, "📍 Requesting last known location...");
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, "📍 Got last known location: " + location.getLatitude() + ", " + location.getLongitude());
                        // Update current location values
                        currentLat = location.getLatitude();
                        currentLong = location.getLongitude();
                        createEmergencyWithLocation(seniorName, phoneNumber, location.getLatitude(), location.getLongitude());
                    } else {
                        Log.w(TAG, "⚠️ No last known location, requesting fresh location update...");
                        requestFreshLocationAndSendSOS(seniorName, phoneNumber);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting last known location: " + e.getMessage());
                    Log.w(TAG, "⚠️ Requesting fresh location update...");
                    requestFreshLocationAndSendSOS(seniorName, phoneNumber);
                });
    }
    
    private void requestFreshLocationAndSendSOS(String seniorName, String phoneNumber) {
        Log.d(TAG, "📍 Requesting fresh location update for SOS...");
        
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1)
                .setMaxWaitTime(5000); // Wait max 5 seconds
        
        LocationCallback tempCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    Log.d(TAG, "📍 Got fresh location: " + location.getLatitude() + ", " + location.getLongitude());
                    currentLat = location.getLatitude();
                    currentLong = location.getLongitude();
                    createEmergencyWithLocation(seniorName, phoneNumber, location.getLatitude(), location.getLongitude());
                    
                    // Remove this temporary callback
                    fusedLocationClient.removeLocationUpdates(this);
                    return;
                }
            }
        };
        
        fusedLocationClient.requestLocationUpdates(locationRequest, tempCallback, null);
        
        // Fallback timeout - if no location received in 5 seconds, use current values
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Log.w(TAG, "⚠️ Location request timeout, using current values: " + currentLat + ", " + currentLong);
            fusedLocationClient.removeLocationUpdates(tempCallback);
            createEmergencyWithLocation(seniorName, phoneNumber, currentLat, currentLong);
        }, 5000);
    }
    
    private void createEmergencyWithLocation(String seniorName, String phoneNumber, double latitude, double longitude) {
        // Create emergency request with unique ID
        String requestId = "SOS_" + System.currentTimeMillis() + "_" + mAuth.getCurrentUser().getUid();
        String seniorUid = mAuth.getCurrentUser().getUid();
        
        // Validate and fix barangay information
        String barangayForEmergency = validateAndFixBarangay();
        if (barangayForEmergency == null || barangayForEmergency.isEmpty()) {
            Log.e(TAG, "❌ Cannot proceed with emergency - no valid barangay information");
            Toast.makeText(this, "Cannot send emergency alert: Barangay information is missing. Please update your profile.", Toast.LENGTH_LONG).show();
            return;
        }
        
        Log.d(TAG, "🚨 Creating emergency request:");
        Log.d(TAG, "🚨 Senior: " + seniorName);
        Log.d(TAG, "🚨 Phone: " + phoneNumber);
        Log.d(TAG, "🚨 Location: " + currentLocationAddress);
        Log.d(TAG, "🚨 Barangay: " + barangayForEmergency);
        Log.d(TAG, "🚨 Coordinates: " + latitude + ", " + longitude);
        
        // Create GeoPoint for location coordinates
        com.google.firebase.firestore.GeoPoint location = null;
        if (latitude != 0.0 && longitude != 0.0) {
            location = new com.google.firebase.firestore.GeoPoint(latitude, longitude);
            Log.d(TAG, "📍 Creating emergency with location: " + latitude + ", " + longitude);
        } else {
            Log.w(TAG, "⚠️ No valid location coordinates available for emergency");
        }
        
        // Add to emergency queue
        EmergencyQueueManager.EmergencyRequest emergencyRequest = new EmergencyQueueManager.EmergencyRequest(
                requestId,
                seniorUid,
                seniorName,
                phoneNumber,
                currentLocationAddress,
                barangayForEmergency,
                System.currentTimeMillis(),
                getString(R.string.text_medical),
                location
        );
        EmergencyQueueManager.getInstance(this).addEmergencyRequest(emergencyRequest);
        
        // Show success toast (no popup needed - first dialog already confirmed)
        Toast.makeText(this, "Emergency request sent! Help is on the way.", Toast.LENGTH_LONG).show();
    }
    
    /**
     * Validates and fixes barangay information for emergency requests
     * @return Valid barangay name or null if cannot be determined
     */
    private String validateAndFixBarangay() {
        Log.d(TAG, "🔍 Validating barangay information for emergency...");
        Log.d(TAG, "🔍 Current barangay from profile: '" + currentBarangay + "'");
        
        // Check if currentBarangay is valid
        if (currentBarangay != null && !currentBarangay.trim().isEmpty()) {
            Log.d(TAG, "✅ Barangay from profile is valid: " + currentBarangay);
            return currentBarangay.trim();
        }
        
        // Try to extract barangay from location address
        if (currentLocationAddress != null && !currentLocationAddress.trim().isEmpty()) {
            String extractedBarangay = extractBarangayFromAddress(currentLocationAddress);
            if (extractedBarangay != null && !extractedBarangay.isEmpty()) {
                Log.d(TAG, "✅ Extracted barangay from address: " + extractedBarangay);
                // Update the currentBarangay for future use
                currentBarangay = extractedBarangay;
                return extractedBarangay;
            }
        }
        
        // Try to determine barangay from coordinates (if available)
        if (currentLat != 0.0 && currentLong != 0.0) {
            String coordinateBarangay = determineBarangayFromCoordinates(currentLat, currentLong);
            if (coordinateBarangay != null && !coordinateBarangay.isEmpty()) {
                Log.d(TAG, "✅ Determined barangay from coordinates: " + coordinateBarangay);
                // Update the currentBarangay for future use
                currentBarangay = coordinateBarangay;
                return coordinateBarangay;
            }
        }
        
        // Last resort: use a default barangay or prompt user
        Log.e(TAG, "❌ Cannot determine barangay from profile, address, or coordinates");
        Log.e(TAG, "❌ Profile barangay: '" + currentBarangay + "'");
        Log.e(TAG, "❌ Location address: '" + currentLocationAddress + "'");
        Log.e(TAG, "❌ Coordinates: " + currentLat + ", " + currentLong);
        
        return null;
    }
    
    /**
     * Extracts barangay name from location address
     */
    private String extractBarangayFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        
        String lowerAddress = address.toLowerCase().trim();
        Log.d(TAG, "🔍 Extracting barangay from address: " + address);
        
        // Common barangay patterns in the Philippines
        String[] barangayPatterns = {
            "barangay", "brgy", "brgy.", "barrio"
        };
        
        for (String pattern : barangayPatterns) {
            int index = lowerAddress.indexOf(pattern);
            if (index != -1) {
                // Extract text after the pattern
                String afterPattern = address.substring(index + pattern.length()).trim();
                // Remove common suffixes and clean up
                String barangay = afterPattern.split("[,\\s]+")[0].trim();
                if (!barangay.isEmpty() && barangay.length() > 2) {
                    Log.d(TAG, "🔍 Found barangay pattern '" + pattern + "' -> '" + barangay + "'");
                    return barangay;
                }
            }
        }
        
        Log.d(TAG, "🔍 No barangay pattern found in address");
        return null;
    }
    
    /**
     * Determines barangay from coordinates (simplified version)
     * In a real implementation, this would use reverse geocoding
     */
    private String determineBarangayFromCoordinates(double lat, double lng) {
        Log.d(TAG, "🔍 Determining barangay from coordinates: " + lat + ", " + lng);
        
        // This is a simplified implementation
        // In a real app, you would use Google Maps Geocoding API or similar
        // For now, we'll return null to indicate we can't determine it from coordinates
        Log.d(TAG, "🔍 Coordinate-based barangay determination not implemented");
        return null;
    }

    private void testCurrentLocation() {
        Log.d(TAG, "🔍 Checking current location status...");
        Log.d(TAG, "📍 Current location values: " + currentLat + ", " + currentLong);
        Log.d(TAG, "📍 Location updates active: " + locationUpdatesActive);
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            Log.d(TAG, "📍 Location permission granted, requesting last known location...");
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Log.d(TAG, "📍 Last known location: " + location.getLatitude() + ", " + location.getLongitude());
                            currentLat = location.getLatitude();
                            currentLong = location.getLongitude();
                            Log.d(TAG, "📍 Updated current location: " + currentLat + ", " + currentLong);
                        } else {
                            Log.w(TAG, "📍 No last known location available");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Error getting last known location: " + e.getMessage());
                    });
        } else {
            Log.w(TAG, "⚠️ No location permission granted");
        }
    }
    
    private void sendEmergencyAlertToRescuers(String seniorName, String phoneNumber) {
        Log.d(TAG, "🚨 Sending emergency alert to all rescuers...");
        
        // Create emergency data
        Map<String, Object> emergencyData = new HashMap<>();
        emergencyData.put("seniorName", seniorName);
        emergencyData.put("seniorPhone", phoneNumber);
        emergencyData.put("location", new GeoPoint(currentLat, currentLong));
        emergencyData.put("locationAddress", currentLocationAddress);
        emergencyData.put("emergencyType", getString(R.string.text_sos));
        emergencyData.put("severity", getString(R.string.text_high));
        emergencyData.put("timestamp", System.currentTimeMillis());
        emergencyData.put("status", getString(R.string.text_active));
        emergencyData.put("seniorUid", mAuth.getCurrentUser().getUid());
        
        // Get all rescuers and send them the emergency alert
        db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .get()
          .addOnSuccessListener(querySnapshot -> {
              Log.d(TAG, "Found " + querySnapshot.size() + " rescuers to notify");
              
              if (querySnapshot.isEmpty()) {
                  Log.w(TAG, "No rescuers found in database!");
                  return;
              }
              
              // Send notification to each rescuer
              for (QueryDocumentSnapshot document : querySnapshot) {
                  String rescuerId = document.getId();
                  String rescuerName = document.getString("rescuegroup");
                  if (rescuerName == null) {
                      rescuerName = document.getString("name");
                  }
                  
                  Log.d(TAG, "Sending emergency alert to rescuer: " + rescuerName + " (ID: " + rescuerId + ")");
                  
                  // Save emergency notification to rescuer's database
                  saveEmergencyNotificationToRescuer(rescuerId, emergencyData);
              }
              
              // Also save the emergency request to a general emergency collection
              saveEmergencyRequestToDatabase(emergencyData);
              
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to get rescuers: " + e.getMessage(), e);
              Toast.makeText(this, getString(R.string.toast_failed_send_emergency), Toast.LENGTH_SHORT).show();
          });
    }
    
    private void saveEmergencyNotificationToRescuer(String rescuerId, Map<String, Object> emergencyData) {
        // Create notification data for the rescuer
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", getString(R.string.text_emergency_sos_type));
        notificationData.put("title", getString(R.string.text_emergency_sos_alert));
        notificationData.put("message", getString(R.string.text_senior_needs_help, emergencyData.get("seniorName")));
        notificationData.put("seniorName", emergencyData.get("seniorName"));
        notificationData.put("seniorPhone", emergencyData.get("seniorPhone"));
        notificationData.put("location", emergencyData.get("location"));
        notificationData.put("locationAddress", emergencyData.get("locationAddress"));
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("isRead", false);
        notificationData.put("priority", getString(R.string.text_high_priority));
        
        // Save to rescuer's notifications collection
        db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .document(rescuerId)
          .collection("emergencyNotifications")
          .add(notificationData)
          .addOnSuccessListener(documentReference -> {
              Log.d(TAG, "Emergency notification saved for rescuer: " + rescuerId);
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to save emergency notification for rescuer " + rescuerId + ": " + e.getMessage());
          });
    }
    
    private void saveEmergencyRequestToDatabase(Map<String, Object> emergencyData) {
        // Save to general emergency requests collection
        db.collection("Sagip")
          .document("emergencyRequests")
          .collection("activeRequests")
          .add(emergencyData)
          .addOnSuccessListener(documentReference -> {
              Log.d(TAG, "Emergency request saved to database with ID: " + documentReference.getId());
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Failed to save emergency request: " + e.getMessage());
          });
    }


    private void registerLocationPermissionLauncher() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (fineLocationGranted != null && fineLocationGranted) {
                        startLocationUpdates();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        startLocationUpdates();
                    } else {
                        Toast.makeText(this, getString(R.string.toast_location_permission_needed), Toast.LENGTH_SHORT).show();
                        tvCurrentLocation.setText(getString(R.string.text_location_permission_denied));
                    }
                }
        );
    }

    private void initializeLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    Log.d(TAG, "📍 Location callback received: " + location.getLatitude() + ", " + location.getLongitude());
                    currentLat = location.getLatitude();
                    currentLong = location.getLongitude();
                    Log.d(TAG, "📍 Updated currentLat: " + currentLat + ", currentLong: " + currentLong);
                    updateLocationUI(location);
                    saveLocationToDatabase(location);
                }
            }
        };
    }

    private void requestLocationPermissions() {
        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void startLocationUpdates() {
        Log.d(TAG, "🔄 Starting location updates...");
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "⚠️ No location permission for starting location updates");
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(10000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(5000)
                .build();

        Log.d(TAG, "📍 Requesting location updates with high accuracy");
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        locationUpdatesActive = true;
        tvCurrentLocation.setText(getString(R.string.text_fetching_location));
        Log.d(TAG, "✅ Location updates started successfully");
    }

    private void stopLocationUpdates() {
        if (locationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
    }

    private void updateLocationUI(Location location) {
        if (location != null) {
            getAddressFromLocation(location);
        }
    }

    private void getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder addressText = new StringBuilder();

                if (address.getThoroughfare() != null) {
                    addressText.append(address.getThoroughfare());
                    if (address.getSubThoroughfare() != null) {
                        addressText.append(" ").append(address.getSubThoroughfare());
                    }
                    addressText.append(", ");
                }

                if (address.getLocality() != null) {
                    addressText.append(address.getLocality()).append(", ");
                }

                if (address.getAdminArea() != null) {
                    addressText.append(address.getAdminArea());
                }

                currentLocationAddress = addressText.toString();
                tvCurrentLocation.setText(currentLocationAddress);
                Log.d(TAG, "Current location: " + currentLocationAddress);
            } else {
                currentLocationAddress = getString(R.string.text_not_available);
                tvCurrentLocation.setText(currentLocationAddress);
            }
        } catch (IOException e) {
            currentLocationAddress = getString(R.string.text_not_available);
            tvCurrentLocation.setText(currentLocationAddress);
            Log.e(TAG, "Error getting address from location", e);
        }
    }

    private void saveLocationToDatabase(Location location) {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors"; // Use consistent collection name

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("accuracy", location.getAccuracy());
        locationData.put("timestamp", System.currentTimeMillis());

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                locationData.put("currentLocation", addresses.get(0).getAddressLine(0));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address for database", e);
        }

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update(locationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location saved to database"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving location to database", e));
    }

    private void checkUserStatus() {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors"; // Use consistent collection name
        
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        if (status != null && !status.equals("approved")) {
                            // Account not approved - sign out and redirect to login
                            Log.d(TAG, "Senior account not approved during status check, status: " + status);
                            mAuth.signOut();
                            clearStoredCredentials();
                            Toast.makeText(Senior_Dashboard.this, 
                                getString(R.string.account_not_approved_message), 
                                Toast.LENGTH_LONG).show();
                            navigateToLogin();
                            return;
                        }
                        // Status is approved, continue with normal flow
                        Log.d(TAG, "Senior account status verified as approved");
                    } else {
                        Log.e(TAG, "User document not found during status check, trying alternative search");
                        // Try to find user by phone number as fallback
                        tryAlternativeUserSearch();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user status", e);
                    // Try alternative search before giving up
                    tryAlternativeUserSearch();
                });
    }

    private void checkAuthStateWithPersistence() {
        // Check if user was previously logged in
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String storedUserType = sharedPreferences.getString(KEY_USER_TYPE, null);

        if (isLoggedIn && userId != null && storedUserType != null) {
            // User was previously logged in, verify Firebase Auth state
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // Firebase user is still authenticated, check status
                checkUserStatus();
            } else {
                // Firebase session expired, redirect to login
                clearStoredCredentials();
                navigateToLogin();
            }
        } else {
            // No stored login, check Firebase Auth
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                // User is not logged in, redirect to login
                navigateToLogin();
            } else {
                // User is logged in but not stored in SharedPreferences
                saveUserCredentials(currentUser.getUid(), "seniors", currentUser.getPhoneNumber());
                checkUserStatus();
            }
        }
    }

    private void saveUserCredentials(String userId, String userType, String phoneNumber) {
        Log.d(TAG, "Saving user credentials: " + userId + ", " + userType);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_TYPE, userType);
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        editor.apply();
    }

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials");
        
        // Reset notification service to prevent cross-user notifications
        SeniorNotificationService.resetInstance();
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        editor.remove(KEY_NOTIFICATION_TOAST_SHOWN); // Reset notification toast flag
        editor.apply();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(Senior_Dashboard.this, MainActivity.class);
        intent.putExtra("LOGOUT_ACTION", true);
        startActivity(intent);
        finish();
    }

    private void tryAlternativeUserSearch() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            mAuth.signOut();
            clearStoredCredentials();
            navigateToLogin();
            return;
        }

        String phoneNumber = currentUser.getPhoneNumber();
        if (phoneNumber != null) {
            // Try searching by phone number
            String searchNumber = phoneNumber.startsWith("+63") ? phoneNumber.substring(3) : phoneNumber;
            
            db.collection("Sagip")
                    .document("users")
                    .collection("seniors")
                    .whereEqualTo("mobileNumber", searchNumber)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!querySnapshot.isEmpty()) {
                            Log.d(TAG, "Found user by phone number alternative search");
                            // User found, continue with normal flow
                        } else {
                            Log.e(TAG, "User not found even with alternative search");
                            mAuth.signOut();
                            clearStoredCredentials();
                            Toast.makeText(Senior_Dashboard.this, 
                                getString(R.string.user_profile_not_found_login_again), 
                                Toast.LENGTH_LONG).show();
                            navigateToLogin();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Alternative search failed", e);
                        mAuth.signOut();
                        clearStoredCredentials();
                            Toast.makeText(Senior_Dashboard.this, 
                                getString(R.string.error_finding_user_profile), 
                                Toast.LENGTH_LONG).show();
                        navigateToLogin();
                    });
        } else {
            Log.e(TAG, "No phone number available for alternative search");
            mAuth.signOut();
            clearStoredCredentials();
            navigateToLogin();
        }
    }

    private void loadUserData() {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors"; // Use consistent collection name

        // Load cached name immediately for instant display
        loadCachedName();

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String middleName = documentSnapshot.getString("middleName");
                        String lastName = documentSnapshot.getString("lastName");
                        // Get currentLocation with proper error handling
                        String currentLocation = null;
                        try {
                            com.google.firebase.firestore.GeoPoint currentLocationGeoPoint = documentSnapshot.getGeoPoint("currentLocation");
                            if (currentLocationGeoPoint != null) {
                                currentLocation = currentLocationGeoPoint.getLatitude() + ", " + currentLocationGeoPoint.getLongitude();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "currentLocation field is not a GeoPoint, trying as String: " + e.getMessage());
                            // Fallback: try to get as String
                            try {
                                currentLocation = documentSnapshot.getString("currentLocation");
                            } catch (Exception e2) {
                                Log.w(TAG, "currentLocation field is neither GeoPoint nor String: " + e2.getMessage());
                                currentLocation = null;
                            }
                        }
                        String barangay = documentSnapshot.getString("barangay");

                        if (documentSnapshot.getDouble("latitude") != null && documentSnapshot.getDouble("longitude") != null) {
                            currentLat = documentSnapshot.getDouble("latitude");
                            currentLong = documentSnapshot.getDouble("longitude");
                        }

                        if (firstName != null && middleName != null && lastName != null) {
                            String fullName = firstName + " " + middleName + " " + lastName;
                            tvFullName.setText(fullName);
                            // Cache the name for future instant loading
                            cacheFullName(fullName);
                        } else {
                            tvFullName.setText(getString(R.string.text_full_name_not_available));
                        }
                        
                        // Store barangay information
                        if (barangay != null && !barangay.isEmpty()) {
                            currentBarangay = barangay;
                            Log.d(TAG, "Current barangay: " + currentBarangay);
                        } else {
                            Log.w(TAG, "No barangay information found for senior");
                        }

                        if (currentLocation != null && !currentLocation.isEmpty()) {
                            currentLocationAddress = currentLocation;
                            tvCurrentLocation.setText(currentLocation);
                        } else {
                            tvCurrentLocation.setText(getString(R.string.text_waiting_location_update));
                        }
                    } else {
                        tvFullName.setText(getString(R.string.text_user_data_not_found));
                        Log.d(TAG, "Document doesn't exist");
                    }
                })
                .addOnFailureListener(e -> {
                    tvFullName.setText(getString(R.string.text_failed_load_data));
                    Log.e(TAG, "Error fetching user data", e);
                });
    }

    private void loadCachedName() {
        // Ensure SharedPreferences is initialized
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        }
        
        String cachedName = sharedPreferences.getString(KEY_CACHED_FULL_NAME, null);
        if (cachedName != null && !cachedName.isEmpty()) {
            tvFullName.setText(cachedName);
            Log.d(TAG, "Loaded cached name: " + cachedName);
        } else {
            // Try to load from alternative cache or show loading
            String alternativeCache = getAlternativeCachedName();
            if (alternativeCache != null && !alternativeCache.isEmpty()) {
                tvFullName.setText(alternativeCache);
                // Restore to main cache
                cacheFullName(alternativeCache);
                Log.d(TAG, "Loaded from alternative cache: " + alternativeCache);
            } else {
                // Try one more time with a fresh SharedPreferences instance
                SharedPreferences freshPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                String freshCache = freshPrefs.getString(KEY_CACHED_FULL_NAME, null);
                if (freshCache != null && !freshCache.isEmpty()) {
                    tvFullName.setText(freshCache);
                    Log.d(TAG, "Loaded from fresh SharedPreferences: " + freshCache);
                } else {
                    tvFullName.setText(getString(R.string.text_loading));
                    Log.d(TAG, "No cached name found, showing loading...");
                }
            }
        }
    }

    private String getAlternativeCachedName() {
        // Try to get from a more persistent storage
        SharedPreferences altPrefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        return altPrefs.getString(KEY_CACHED_FULL_NAME, null);
    }

    private void cacheFullName(String fullName) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_FULL_NAME, fullName)
                .apply();
        Log.d(TAG, "Cached name: " + fullName);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // Load cached name immediately when returning to dashboard via new intent
        loadCachedName();
        
        
        // Handle rescuer response notification
        handleRescuerResponseNotification(intent);
        
        Log.d(TAG, "onNewIntent called - reloading cached name and checking for rescuer data");
    }
    
    private void handleRescuerResponseNotification(Intent intent) {
        Log.d(TAG, "🔍 handleRescuerResponseNotification called with intent: " + (intent != null ? "not null" : "null"));
        if (intent != null) {
            String notificationType = intent.getStringExtra("notification_type");
            Log.d(TAG, "🔍 Checking notification type: " + notificationType);
            Log.d(TAG, "🔍 All intent extras: " + intent.getExtras());
            
            if ("rescuer_response".equals(notificationType) || "RESCUER_RESPONSE".equals(notificationType)) {
                String rescuerName = intent.getStringExtra("rescuer_name");
                String rescuerPhone = intent.getStringExtra("rescuer_phone");
                String requestId = intent.getStringExtra("request_id");
                String emergencyStatus = intent.getStringExtra("emergency_status");
                String assignedRescuerId = intent.getStringExtra("assigned_rescuer_id");
                String rescuerTeam = intent.getStringExtra("rescuer_team");
                String hospitalId = intent.getStringExtra("hospital_id");
                String hospitalName = intent.getStringExtra("hospital_name");
                String hospitalAddress = intent.getStringExtra("hospital_address");
                String hospitalPhone = intent.getStringExtra("hospital_phone");
                
                Log.d(TAG, "🚨 Received rescuer response notification - Rescuer: " + rescuerName + " (Request ID: " + requestId + ")");
                Log.d(TAG, "📱 Emergency Status: " + emergencyStatus + ", Assigned Rescuer ID: " + assignedRescuerId);
                Log.d(TAG, "🏢 Rescue Team: " + rescuerTeam);
                Log.d(TAG, "🏥 Hospital: " + hospitalName + " at " + hospitalAddress);
                
                // Stop the emergency alert sound when notification is clicked
                EmergencySOSBackgroundService.stopEmergencySound();
                Log.d(TAG, "🔇 Emergency alert sound stopped when senior clicked notification");
                
                // Show popup notification before navigating to rescuer details
                showRescuerAcceptedPopup(rescuerName, rescuerPhone, rescuerTeam, requestId, assignedRescuerId, emergencyStatus, hospitalId, hospitalName, hospitalAddress, hospitalPhone);
                
                // Update emergency tracking variables
                currentEmergencyId = requestId;
                currentRescuerId = assignedRescuerId;
                currentRescuerPhone = rescuerPhone;
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("notification_type");
                intent.removeExtra("rescuer_name");
                intent.removeExtra("rescuer_phone");
                intent.removeExtra("request_id");
                intent.removeExtra("emergency_status");
                intent.removeExtra("assigned_rescuer_id");
                intent.removeExtra("rescuer_team");
                intent.removeExtra("hospital_id");
                intent.removeExtra("hospital_name");
                intent.removeExtra("hospital_address");
                intent.removeExtra("hospital_phone");
            } else if ("hospital_details_update".equals(notificationType)) {
                Log.d(TAG, "🏥 Received hospital details update notification");
                
                // Stop the emergency alert sound when notification is clicked
                EmergencySOSBackgroundService.stopEmergencySound();
                Log.d(TAG, "🔇 Emergency alert sound stopped when senior clicked hospital update notification");
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("notification_type");
            } else {
                Log.d(TAG, "🔍 No rescuer response notification found, notification type: " + notificationType);
            }
        } else {
            Log.d(TAG, "🔍 Intent is null in handleRescuerResponseNotification");
        }
    }
    
    private void showRescuerResponseDialog(String rescuerName, String rescuerPhone, String requestId, 
                                         String emergencyStatus, String assignedRescuerId) {
        // Check if activity is still valid before showing dialog
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show rescuer response dialog - activity is not in valid state");
            return;
        }
        
        // Try to extract rescue group from notification message
        String rescueGroup = extractRescueGroupFromNotification();
        
        if (rescueGroup != null && !rescueGroup.isEmpty()) {
            // Use rescue group from notification
            showRescuerDialogWithDetails(rescuerName, rescuerPhone, requestId, emergencyStatus, rescueGroup);
        } else if (assignedRescuerId != null && !assignedRescuerId.isEmpty()) {
            // Fallback to database lookup
            fetchRescuerDetailsAndShowDialog(rescuerName, rescuerPhone, requestId, emergencyStatus, assignedRescuerId);
        } else {
            // Final fallback
            showRescuerDialogWithDetails(rescuerName, rescuerPhone, requestId, emergencyStatus, "Emergency Response Team");
        }
    }
    
    private String extractRescueGroupFromNotification() {
        // This method would need access to the notification message
        // For now, we'll return null and rely on database lookup
        return null;
    }
    
    private void fetchRescuerDetailsAndShowDialog(String rescuerName, String rescuerPhone, String requestId, 
                                                String emergencyStatus, String assignedRescuerId) {
        Log.d(TAG, "🔍 Fetching rescuer details for ID: " + assignedRescuerId);
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(assignedRescuerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d(TAG, "🔍 Document exists: " + documentSnapshot.exists());
                    
                    if (documentSnapshot.exists()) {
                        // Log all available fields for debugging
                        Log.d(TAG, "🔍 Available fields: " + documentSnapshot.getData().keySet());
                        
                        String rescueGroup = documentSnapshot.getString("rescuegroup");
                        Log.d(TAG, "🔍 Rescue group from 'rescuegroup' field: " + rescueGroup);
                        
                        // Try alternative field names
                        if (rescueGroup == null || rescueGroup.isEmpty()) {
                            rescueGroup = documentSnapshot.getString("rescueGroup");
                            Log.d(TAG, "🔍 Rescue group from 'rescueGroup' field: " + rescueGroup);
                        }
                        
                        if (rescueGroup == null || rescueGroup.isEmpty()) {
                            rescueGroup = documentSnapshot.getString("group");
                            Log.d(TAG, "🔍 Rescue group from 'group' field: " + rescueGroup);
                        }
                        
                        if (rescueGroup == null || rescueGroup.isEmpty()) {
                            rescueGroup = documentSnapshot.getString("team");
                            Log.d(TAG, "🔍 Rescue group from 'team' field: " + rescueGroup);
                        }
                        
                        if (rescueGroup == null || rescueGroup.isEmpty()) {
                            rescueGroup = "Emergency Response Team";
                            Log.d(TAG, "🔍 Using fallback rescue group name");
                        }
                        
                        Log.d(TAG, "🔍 Final rescue group: " + rescueGroup);
                        showRescuerDialogWithDetails(rescuerName, rescuerPhone, requestId, emergencyStatus, rescueGroup);
                    } else {
                        Log.w(TAG, "🔍 Rescuer document not found for ID: " + assignedRescuerId);
                        showRescuerDialogWithDetails(rescuerName, rescuerPhone, requestId, emergencyStatus, "Emergency Response Team");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error fetching rescuer details for ID: " + assignedRescuerId, e);
                    showRescuerDialogWithDetails(rescuerName, rescuerPhone, requestId, emergencyStatus, "Emergency Response Team");
                });
    }
    
    private void showRescuerDialogWithDetails(String rescuerName, String rescuerPhone, String requestId, 
                                            String emergencyStatus, String rescueGroup) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.help_on_way_title));
        
        String statusText = "assigned".equals(emergencyStatus) ? getString(R.string.assigned_status) : getString(R.string.pending_status);
        String message = getString(R.string.rescuer_assigned_message) + "\n\n" +
                        getString(R.string.rescue_group_label) + " " + rescueGroup + "\n" +
                        getString(R.string.phone_label) + " " + rescuerPhone + "\n" +
                        getString(R.string.status_label_dialog) + " " + statusText + "\n\n" +
                        getString(R.string.rescuer_coming_message);
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        
        // View Details button
        builder.setPositiveButton(getString(R.string.view_details_button), (dialog, which) -> {
            Log.d(TAG, "User clicked View Details for request: " + requestId);
            
            // Stop the emergency alert sound when senior clicks "View Details"
            EmergencySOSBackgroundService.stopEmergencySound();
            Log.d(TAG, "🔇 Emergency alert sound stopped when senior clicked 'View Details'");
            
            // Navigate to rescuer details page
            Intent intent = new Intent(this, RescuerDetailsActivity.class);
            intent.putExtra("emergencyId", requestId);
            startActivity(intent);
        });
        
        // Call rescuer button
        builder.setNeutralButton(getString(R.string.call_rescuer_button), (dialog, which) -> {
            if (rescuerPhone != null && !rescuerPhone.isEmpty()) {
                // Stop the emergency alert sound when senior clicks "Call Rescuer"
                EmergencySOSBackgroundService.stopEmergencySound();
                Log.d(TAG, "🔇 Emergency alert sound stopped when senior clicked 'Call Rescuer'");
                
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(android.net.Uri.parse("tel:" + rescuerPhone));
                startActivity(callIntent);
            } else {
                Toast.makeText(this, getString(R.string.text_rescuer_phone_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        
        // OK button
        builder.setNegativeButton("OK", (dialog, which) -> {
            // Stop the emergency alert sound when senior clicks "OK"
            EmergencySOSBackgroundService.stopEmergencySound();
            Log.d(TAG, "🔇 Emergency alert sound stopped when senior clicked 'OK'");
            
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        
        // Style the buttons
        dialog.setOnShowListener(dialogInterface -> {
            try {
                // Make the positive button green to indicate positive action
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
            } catch (Exception e) {
                Log.e(TAG, "Error styling dialog buttons", e);
            }
        });
        
        dialog.show();
        Log.d(TAG, "📱 Rescuer response dialog shown for: " + rescuerName);
    }

    private void showRescuerAcceptedPopup(String rescuerName, String rescuerPhone, String rescuerTeam, 
                                        String requestId, String assignedRescuerId, String emergencyStatus,
                                        String hospitalId, String hospitalName, String hospitalAddress, String hospitalPhone) {
        // Check if activity is still valid before showing dialog
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show rescuer accepted popup - activity is not in valid state");
            return;
        }
        
        Log.d(TAG, "🎉 Showing rescuer accepted popup for: " + rescuerName);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_rescuer_accepted_title));
        builder.setMessage(getString(R.string.dialog_rescuer_accepted_message, rescuerName, rescuerTeam));
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setCancelable(false);
        
        // View Details button - navigates to rescuer details page
        builder.setPositiveButton(getString(R.string.button_view_details), (dialog, which) -> {
            Log.d(TAG, "🚑 User chose to view rescuer details");
            
            // Stop the emergency alert sound when senior clicks "View Details"
            EmergencySOSBackgroundService.stopEmergencySound();
            Log.d(TAG, "🔇 Emergency alert sound stopped when senior clicked 'View Details'");
            
            navigateToRescuerDetails(rescuerName, rescuerPhone, rescuerTeam, requestId, assignedRescuerId, 
                                   emergencyStatus, hospitalId, hospitalName, hospitalAddress, hospitalPhone);
            dialog.dismiss();
        });
        
        // Call Rescuer button
        if (rescuerPhone != null && !rescuerPhone.isEmpty()) {
            builder.setNeutralButton(getString(R.string.button_call_rescuer), (dialog, which) -> {
                Log.d(TAG, "📞 User chose to call rescuer: " + rescuerPhone);
                
                // Stop the emergency alert sound when senior clicks "Call Rescuer"
                EmergencySOSBackgroundService.stopEmergencySound();
                Log.d(TAG, "🔇 Emergency alert sound stopped when senior clicked 'Call Rescuer'");
                
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(android.net.Uri.parse("tel:" + rescuerPhone));
                startActivity(callIntent);
                dialog.dismiss();
            });
        }
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            try {
                // Style the buttons
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                    if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
                    }
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                    }
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
                if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(16);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error styling rescuer accepted popup buttons", e);
            }
        });
        
        dialog.show();
        Log.d(TAG, "🎉 Rescuer accepted popup shown for: " + rescuerName);
    }
    
    private void navigateToRescuerDetails(String rescuerName, String rescuerPhone, String rescuerTeam, 
                                        String requestId, String assignedRescuerId, String emergencyStatus,
                                        String hospitalId, String hospitalName, String hospitalAddress, String hospitalPhone) {
        Log.d(TAG, "🚑 Navigating to rescuer details page");
        Log.d(TAG, "📍 Passing senior location: " + currentLat + ", " + currentLong);
        
        Intent rescuerDetailsIntent = new Intent(this, RescuerDetailsActivity.class);
        rescuerDetailsIntent.putExtra("emergencyId", requestId);
        rescuerDetailsIntent.putExtra("rescuerName", rescuerName);
        rescuerDetailsIntent.putExtra("rescuerPhone", rescuerPhone);
        rescuerDetailsIntent.putExtra("rescuerTeam", rescuerTeam);
        rescuerDetailsIntent.putExtra("assignedRescuerId", assignedRescuerId);
        rescuerDetailsIntent.putExtra("emergencyStatus", emergencyStatus);
        rescuerDetailsIntent.putExtra("hospitalId", hospitalId);
        rescuerDetailsIntent.putExtra("hospitalName", hospitalName);
        rescuerDetailsIntent.putExtra("hospitalAddress", hospitalAddress);
        rescuerDetailsIntent.putExtra("hospitalPhone", hospitalPhone);
        // Add senior location to intent
        rescuerDetailsIntent.putExtra("seniorLat", currentLat);
        rescuerDetailsIntent.putExtra("seniorLong", currentLong);
        startActivity(rescuerDetailsIntent);
    }
    
    private void registerRescuerAcceptedReceiver() {
        rescuerAcceptedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.sagip_prototype.SHOW_RESCUER_ACCEPTED_POPUP".equals(intent.getAction())) {
                    Log.d(TAG, "📡 Received broadcast to show rescuer accepted popup");
                    
                    String rescuerName = intent.getStringExtra("rescuer_name");
                    String rescuerPhone = intent.getStringExtra("rescuer_phone");
                    String rescuerTeam = intent.getStringExtra("rescuer_team");
                    String requestId = intent.getStringExtra("request_id");
                    String assignedRescuerId = intent.getStringExtra("assigned_rescuer_id");
                    String emergencyStatus = intent.getStringExtra("emergency_status");
                    String hospitalId = intent.getStringExtra("hospital_id");
                    String hospitalName = intent.getStringExtra("hospital_name");
                    String hospitalAddress = intent.getStringExtra("hospital_address");
                    String hospitalPhone = intent.getStringExtra("hospital_phone");
                    
                    // Show the popup immediately
                    showRescuerAcceptedPopup(rescuerName, rescuerPhone, rescuerTeam, requestId, 
                                          assignedRescuerId, emergencyStatus, hospitalId, 
                                          hospitalName, hospitalAddress, hospitalPhone);
                }
            }
        };
        
        IntentFilter filter = new IntentFilter("com.example.sagip_prototype.SHOW_RESCUER_ACCEPTED_POPUP");
        
        // For Android 13+ (API 33+), specify RECEIVER_NOT_EXPORTED for security
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(rescuerAcceptedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(rescuerAcceptedReceiver, filter);
        }
        
        Log.d(TAG, "📡 Registered rescuer accepted broadcast receiver");
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Load cached name immediately when returning to dashboard
        loadCachedName();
        
        // Start listening for rescuer response notifications (if not already started)
        SeniorNotificationService.getInstance(this).startListening();
        
        // Load active emergencies from database to ensure local cache is up to date
        EmergencyQueueManager.getInstance(this).loadActiveEmergenciesFromDatabase();
        
        
        // Check notification permission status
        Log.d(TAG, "🔔 App resumed, checking notification status");
        if (areNotificationsEnabled()) {
            Log.d(TAG, "🔔 Notifications are now enabled!");
            // Only show toast once per session
            if (!sharedPreferences.getBoolean(KEY_NOTIFICATION_TOAST_SHOWN, false)) {
                Toast.makeText(this, getString(R.string.toast_notifications_enabled_senior), Toast.LENGTH_SHORT).show();
                // Mark that we've shown the toast
                sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_TOAST_SHOWN, true).apply();
            }
        }
        
        if (!locationUpdatesActive) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Don't stop listening for rescuer response notifications immediately
        // Let it continue running in background for better notification delivery
        // SeniorNotificationService.getInstance(this).stopListening();
        
        // Stop location updates
        stopLocationUpdates();
    }
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle language change without recreating activity
        Log.d(TAG, "Configuration changed - language change detected");
        
        // Reload cached name to ensure it's still displayed
        loadCachedName();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup resources
        if (locationUpdatesActive) {
            stopLocationUpdates();
        }
        
        // Stop listening for rescuer response notifications when activity is destroyed
        SeniorNotificationService.getInstance(this).stopListening();
        
        // Unregister broadcast receiver
        if (rescuerAcceptedReceiver != null) {
            unregisterReceiver(rescuerAcceptedReceiver);
            Log.d(TAG, "📡 Unregistered rescuer accepted broadcast receiver");
        }
        
        // Remove emergency status listener
    }
    
    
    /**
     * Get emergency status from database with fallback
     */
    public void getEmergencyStatus(String requestId, EmergencyQueueManager.EmergencyStatusCallback callback) {
        // First try to get from local EmergencyQueueManager
        EmergencyQueueManager.EmergencyRequest emergency = EmergencyQueueManager.getInstance(this).getEmergencyById(requestId);
        
        if (emergency != null) {
            // Emergency found in local queue
            callback.onStatusLoaded(emergency.status, emergency.assignedRescuerId, System.currentTimeMillis());
        } else {
            // Emergency not found in local queue, load from database
            Log.d(TAG, "⚠️ Emergency not found in local queue, loading from database...");
            EmergencyQueueManager.getInstance(this).getEmergencyStatusFromDatabase(requestId, callback);
        }
    }
    
    // Notification permission request methods
    private void checkAndRequestNotificationPermission() {
        Log.d(TAG, "🔔 Checking notification permissions");
        
        // For Android 13+ (API 33+), request POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "🔔 POST_NOTIFICATIONS permission not granted, requesting...");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
                return;
            }
        }
        
        // Check if notifications are enabled
        if (!areNotificationsEnabled()) {
            Log.d(TAG, "🔔 Notifications are disabled, requesting permission");
            showNotificationPermissionDialog();
        } else {
            Log.d(TAG, "🔔 Notifications are enabled");
        }
    }
    
    private boolean areNotificationsEnabled() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return notificationManager.areNotificationsEnabled();
        } else {
            // For older versions, assume notifications are enabled if the service exists
            return true;
        }
    }
    
    private void showNotificationPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_enable_notifications))
                .setMessage(getString(R.string.dialog_enable_notifications_senior))
                .setPositiveButton(getString(R.string.button_enable_notifications), (dialog, which) -> {
                    openNotificationSettings();
                })
                .setNegativeButton(getString(R.string.button_later), (dialog, which) -> {
                    Log.d(TAG, "🔔 User chose to enable notifications later");
                    // Show a reminder toast
                    Toast.makeText(this, getString(R.string.toast_notifications_later), Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }
    
    private void openNotificationSettings() {
        Log.d(TAG, "🔔 Opening notification settings");
        
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android 8.0 and above, open app-specific notification settings
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            // For older versions, open general notification settings
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
        }
        
        try {
            startActivity(intent);
            Toast.makeText(this, getString(R.string.toast_enable_notifications_return), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error opening notification settings", e);
            Toast.makeText(this, getString(R.string.toast_could_not_open_settings), Toast.LENGTH_LONG).show();
        }
    }
    
    // FCM Token Registration
    private void registerForFCMNotifications() {
        Log.d(TAG, "🔔 Registering for FCM notifications");
        
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "❌ Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d(TAG, "🔔 FCM Token: " + token);

                    // Save token to Firestore
                    saveFCMTokenToDatabase(token);
                });
    }
    
    private void saveFCMTokenToDatabase(String fcmToken) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "❌ No authenticated user for FCM token");
            return;
        }
        
        String userId = currentUser.getUid();
        Log.d(TAG, "💾 Saving FCM token for user: " + userId);
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("fcmToken", fcmToken);
        tokenData.put("lastUpdated", System.currentTimeMillis());
        
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(userId)
                .set(tokenData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ FCM token saved successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to save FCM token", e);
                });
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001) { // POST_NOTIFICATIONS permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✅ POST_NOTIFICATIONS permission granted");
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "❌ POST_NOTIFICATIONS permission denied");
                Toast.makeText(this, "Notification permission denied. You may not receive emergency notifications.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    
}