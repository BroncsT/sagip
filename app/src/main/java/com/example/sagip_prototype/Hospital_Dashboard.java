package com.example.sagip_prototype;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.Timestamp;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import android.os.Handler;
import android.os.CountDownTimer;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;

public class Hospital_Dashboard extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_CACHED_HOSPITAL_NAME = "cachedHospitalName";
    
    // Status update requirement (10 minutes)
    private static final long STATUS_UPDATE_INTERVAL_MINUTES = 10;
    private static final long STATUS_UPDATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(STATUS_UPDATE_INTERVAL_MINUTES);

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvCurrentLocation;
    private TextView tvHospitalName;
    private TextView tvAvailableBeds, tvDoctorsAvailable;
    private TextView tvErStatus;
    private Button btnEditStatus;
    
    // Status update requirement UI
    private TextView tvLastUpdated;
    private TextView tvCountdownTimer;
    
    
    private String userType = "hospital";
    private String userId;
    private SharedPreferences sharedPreferences;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private double currentLat = 0.0;
    private double currentLong = 0.0;
    
    // Countdown timer for status update
    private CountDownTimer statusCountdownTimer;
    private boolean isTimerRunning = false;
    private long timerStartTime = 0;
    private long timerDuration = 0;
    private boolean isDialogShowing = false;
    private boolean isTimerBeingStarted = false; // Flag to prevent multiple timer starts
    private boolean isNavigatingBetweenPages = false; // Flag to prevent timer restarts during navigation
    private long lastRealTimeUpdate = 0; // Throttle real-time listener calls
    private boolean isTimerUpdating = false; // Flag to prevent multiple timer updates
    
    // Simple timer persistence using SharedPreferences
    private static final String TIMER_START_TIME_KEY = "timer_start_time";
    private static final String TIMER_DURATION_KEY = "timer_duration";
    
    // Global timer service
    // GlobalTimerService removed - using simple timer with SharedPreferences

    // MediaPlayer for reminder alert sound
    private MediaPlayer reminderMediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_hospital_dashboard);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Start listening for emergency notifications
        startEmergencyNotificationListener();

        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvHospitalName = findViewById(R.id.hospitalStaffName);
        tvAvailableBeds = findViewById(R.id.tvAvailableBeds);
        tvDoctorsAvailable = findViewById(R.id.tvDoctorsAvailable);
        tvErStatus = findViewById(R.id.tvErStatus);
        btnEditStatus = findViewById(R.id.btnEditStatus);
        
        
        // Initialize status update UI
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        tvCountdownTimer = findViewById(R.id.tvCountdownTimer);


        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();

        // Setup bottom navigation
        setupBottomNavigation();

        // Check for location permissions
        checkLocationPermission();

        // Check authentication state
        checkAuthState();
        
        // Stop GlobalTimerService to remove the notification
        stopGlobalTimerService();
        
        // GlobalTimerService removed - using simple timer with SharedPreferences
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("Hospital_Dashboard", "=== onResume() called ===");
        Log.d("Hospital_Dashboard", "Current time: " + new java.util.Date());
        Log.d("Hospital_Dashboard", "Is timer running: " + isTimerRunning);
        
        // Load cached hospital name immediately when returning to dashboard
        loadCachedHospitalName();
        
        // Clear navigation flag
        isNavigatingBetweenPages = false;
        
        // Check if this activity was opened from a status update reminder notification
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("show_status_update_dialog", false)) {
            Log.d("Hospital_Dashboard", "Opened from status update reminder notification");
            // Stop any existing alert sound first (from notification)
            stopReminderAlertSound();
            // Show the status update dialog immediately
            showStatusUpdateRequiredDialog(Long.MAX_VALUE);
            // Clear the extra to prevent showing again
            intent.removeExtra("show_status_update_dialog");
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
        
        // Only restore timer state if not already running to avoid unnecessary refreshes
        if (!isTimerRunning) {
            Log.d("Hospital_Dashboard", "=== ATTEMPTING TIMER RESTORATION ===");
            boolean timerRestored = restoreTimerState();
            
            if (!timerRestored && userId != null) {
                Log.d("Hospital_Dashboard", "No timer to restore, checking database for timer state");
                // Only load if timer is not running to avoid refresh
                loadHospitalStatus();
            } else if (timerRestored) {
                Log.d("Hospital_Dashboard", "✅ Timer restored successfully from SharedPreferences");
            }
        } else {
            Log.d("Hospital_Dashboard", "Timer already running, skipping restoration to avoid refresh");
        }
        
        // Start notification service for status updates
        startStatusNotificationService();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle language change without recreating activity
        Log.d("Hospital_Dashboard", "Configuration changed - language change detected");
        
        // Reload cached hospital name to ensure it's still displayed
        loadCachedHospitalName();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("Hospital_Dashboard", "=== onPause() called ===");
        Log.d("Hospital_Dashboard", "Current time: " + new java.util.Date());
        Log.d("Hospital_Dashboard", "Is timer running: " + isTimerRunning);
        // GlobalTimerService removed
        
        // Set flag to prevent timer restarts during navigation
        isNavigatingBetweenPages = true;
        
        // Save current timer state before leaving
        if (isTimerRunning && timerStartTime > 0 && timerDuration > 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - timerStartTime;
            long remainingTime = timerDuration - elapsedTime;
            
            if (remainingTime > 0) {
                // Save the current state with updated start time
                saveTimerState(currentTime, remainingTime);
                Log.d("Hospital_Dashboard", "⏰ Timer state saved when leaving: " + (remainingTime / 1000) + " seconds remaining");
            } else {
                // Timer has expired, clear saved state
                clearTimerState();
                Log.d("Hospital_Dashboard", "⚠️ Timer expired when leaving, cleared saved state");
            }
        }
        
        stopLocationUpdates();
        
        // Don't cancel timer when navigating to other activities
        // Timer state is saved and will be restored when returning
        Log.d("Hospital_Dashboard", "✅ Timer state saved, will be restored when returning");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        Log.d("Hospital_Dashboard", "=== onStop() called ===");
        Log.d("Hospital_Dashboard", "App is being stopped, but global timer service continues running");
        
        // Don't cancel timer when navigating between activities
        // The global timer service runs independently as a foreground service
        Log.d("Hospital_Dashboard", "Global timer service continues running independently");
    }
    
    
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        Log.d("Hospital_Dashboard", "=== onUserLeaveHint() called ===");
        Log.d("Hospital_Dashboard", "User is leaving the app (home button, recent apps, etc.)");
        
        // Global timer service continues running as foreground service
        // Only pause local timer, global service is independent
        if (statusCountdownTimer != null && isTimerRunning) {
            Log.d("Hospital_Dashboard", "Pausing local timer, global service continues");
            statusCountdownTimer.cancel();
            isTimerRunning = false;
            // Keep timerStartTime and timerDuration for resume
        }
        
        Log.d("Hospital_Dashboard", "Global timer service continues running in background");
    }
    
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d("Hospital_Dashboard", "=== onRestart() called ===");
        Log.d("Hospital_Dashboard", "App is restarting from background");
        
        // This is called when app comes back from background
        // Resume timer if it was paused
        if (timerStartTime > 0 && !isTimerRunning) {
            Log.d("Hospital_Dashboard", "Resuming timer from background");
            resumeTimer();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001) { // Status update request
            Log.d("Hospital_Dashboard", "Returned from status update screen");
            if (resultCode == RESULT_OK) {
                Log.d("Hospital_Dashboard", "Status update was successful, refreshing dashboard");
                // Status was updated successfully, refresh the dashboard data
                refreshTimerAfterStatusUpdate();
                
                // Also explicitly refresh the hospital status display
                refreshHospitalStatus();
            } else {
                Log.d("Hospital_Dashboard", "Status update was cancelled or failed");
            }
        }
    }

    private void checkAuthState() {
        // First check if we have stored user credentials
        userId = sharedPreferences.getString(KEY_USER_ID, null);
        String storedUserType = sharedPreferences.getString(KEY_USER_TYPE, null);

        if (userId != null && storedUserType != null) {
            // We have stored credentials, update userType if needed
            this.userType = storedUserType;
            // Check if user status is "new" and redirect to registration
            checkUserStatusAndRedirect();
        } else {
            // No stored credentials, check Firebase Auth
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                // User is not logged in, redirect to login
                navigateToLogin();
            } else {
                // User is logged in but not stored in SharedPreferences
                userId = currentUser.getUid();

                // Save to SharedPreferences for persistence
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_USER_ID, userId);
                editor.putString(KEY_USER_TYPE, userType);
                editor.apply();
                
                // Check if user status is "new" and redirect to registration
                checkUserStatusAndRedirect();
            }
        }
    }

    private void checkUserStatusAndRedirect() {
        if (userId == null) {
            Log.w("Hospital_Dashboard", "userId is null, cannot check status");
            return;
        }

        Log.d("Hospital_Dashboard", "=== STATUS CHECK START ===");
        Log.d("Hospital_Dashboard", "Checking user status for userId: " + userId);
        Log.d("Hospital_Dashboard", "Collection path: Sagip/users/hospital/" + userId);

        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d("Hospital_Dashboard", "=== DATABASE QUERY SUCCESS ===");
                    Log.d("Hospital_Dashboard", "Document exists: " + documentSnapshot.exists());
                    
                    if (documentSnapshot.exists()) {
                        // Log all fields in the document for debugging
                        Log.d("Hospital_Dashboard", "Document data: " + documentSnapshot.getData());
                        
                        String status = documentSnapshot.getString("status");
                        Log.d("Hospital_Dashboard", "Raw status value: '" + status + "'");
                        Log.d("Hospital_Dashboard", "Status is null: " + (status == null));
                        Log.d("Hospital_Dashboard", "Status equals 'new': " + "new".equals(status));
                        
                        if ("new".equals(status)) {
                            Log.d("Hospital_Dashboard", "=== REDIRECTING TO REGISTRATION ===");
                            Log.d("Hospital_Dashboard", "User status is 'new', redirecting to registration");
                            // User status is "new", redirect to registration
                            Intent intent = new Intent(Hospital_Dashboard.this, Hospital_Registration.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Log.d("Hospital_Dashboard", "=== PROCEEDING TO DASHBOARD ===");
                            Log.d("Hospital_Dashboard", "User status is not 'new', proceeding to dashboard");
                            Log.d("Hospital_Dashboard", "Status value: '" + status + "'");
                            // User is registered, proceed with dashboard initialization
                            initializeDashboard();
                        }
                    } else {
                        Log.w("Hospital_Dashboard", "=== DOCUMENT NOT FOUND ===");
                        Log.w("Hospital_Dashboard", "User document not found, redirecting to registration");
                        // User document doesn't exist, redirect to registration
                        Intent intent = new Intent(Hospital_Dashboard.this, Hospital_Registration.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                    Log.d("Hospital_Dashboard", "=== STATUS CHECK END ===");
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "=== DATABASE QUERY FAILED ===");
                    Log.e("Hospital_Dashboard", "Error checking user status: " + e.getMessage(), e);
                    // On error, redirect to registration to be safe
                    Intent intent = new Intent(Hospital_Dashboard.this, Hospital_Registration.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void initializeDashboard() {
        Log.d("Hospital_Dashboard", "Initializing dashboard for registered user");
        
        // Setup click listeners
        setupClickListeners();
        
        // Load hospital status
        loadHospitalStatus();
        
        // Test database connection for debugging
        testDatabaseConnection();
        
        // Debug: Check current user status immediately
        debugCurrentUserStatus();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(Hospital_Dashboard.this, MainActivity.class);
        // Clear the back stack so user can't press back to return after logging out
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

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
                Toast.makeText(this, getString(R.string.location_permission_denied),
                        Toast.LENGTH_LONG).show();
                tvCurrentLocation.setText(getString(R.string.location_permission_denied));
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback,
                    Looper.getMainLooper());
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @SuppressLint("StringFormatMatches")
    private void updateLocationDisplay(double latitude, double longitude) {
        String locationText = getAddressFromLocation(latitude, longitude);
        if (locationText != null) {
            tvCurrentLocation.setText(getString(R.string.location_format, locationText));
        } else {
            // Fallback to coordinates if address can't be determined
            tvCurrentLocation.setText(String.format(Locale.getDefault(),
                    getString(R.string.location_format), latitude, longitude));
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

        // Create data object with location
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("currentLocation", new GeoPoint(latitude, longitude));
        locationData.put("lastUpdated", com.google.firebase.Timestamp.now());

        // Save to Firestore
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .update(locationData)
                .addOnSuccessListener(aVoid -> {
                    // Location saved successfully
                })
                .addOnFailureListener(e -> {
                    // Handle NOT_FOUND gracefully (document deleted during account deletion)
                    if (e.getMessage() != null && e.getMessage().contains("NOT_FOUND")) {
                        Log.w("Hospital_Dashboard", " Hospital document not found (likely deleted) - stopping location updates");
                        stopLocationUpdates();
                    } else {
                        Toast.makeText(Hospital_Dashboard.this,
                                getString(R.string.error_starting_location_updates),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.hospital_home);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.hospital_home) {
                return true;
            } else if (itemId == R.id.hospital_profile) {
                startActivity(new Intent(getApplicationContext(), Hospital_Profile.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.hospital_list) {
                startActivity(new Intent(getApplicationContext(), Hospital_List.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    // Method to handle logout - clears stored credentials and signs out from Firebase
    public void logoutUser() {
        Log.d("Hospital_Dashboard", "🚪 User logging out - stopping all background services");
        
        // Stop ALL background services to prevent notifications to wrong user
        BackgroundServiceManager.stopAllBackgroundServices(this);
        
        // Clear stored credentials
        clearStoredCredentials();

        // Sign out from Firebase
        mAuth.signOut();

        // Navigate to login screen
        navigateToLogin();
    }

    // Helper method to clear stored credentials
    private void clearStoredCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.apply();
    }

    private void setupClickListeners() {
        if (btnEditStatus != null) {
            btnEditStatus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Hospital_Dashboard.this, Hospital_Status_Edit.class);
                    startActivity(intent);
                }
            });
        }
        
    }

    private void loadHospitalStatus() {
        if (userId == null) {
            Log.w("Hospital_Dashboard", "userId is null, cannot load hospital status");
            return;
        }

        Log.d("Hospital_Dashboard", "Loading hospital status for userId: " + userId);

        // First try a one-time get to see if data exists
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d("Hospital_Dashboard", "One-time get successful");
                    if (documentSnapshot.exists()) {
                        Log.d("Hospital_Dashboard", "Document exists in one-time get");
                        processHospitalData(documentSnapshot);
                        
                        // Check if status update is required
                        checkStatusUpdateRequirement(documentSnapshot);
                    } else {
                        Log.w("Hospital_Dashboard", "Document does not exist in one-time get");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "One-time get failed: " + e.getMessage(), e);
                });

        // Then set up the real-time listener
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e("Hospital_Dashboard", "Error loading hospital status: " + e.getMessage(), e);
                        Toast.makeText(Hospital_Dashboard.this, getString(R.string.error_loading_status, e.getMessage()), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        // Throttle real-time listener calls to prevent excessive processing
                        long currentTime = System.currentTimeMillis();
                        if (lastRealTimeUpdate == 0 || (currentTime - lastRealTimeUpdate) > 1000) { // Throttle to max 1 call per second
                            lastRealTimeUpdate = currentTime;
                            Log.d("Hospital_Dashboard", "Real-time listener: Document exists, processing data...");
                            processHospitalData(documentSnapshot);
                            
                            // Only check timer state if timer is not being started and not navigating between pages
                            if (!isTimerBeingStarted && !isNavigatingBetweenPages) {
                                checkStatusUpdateRequirement(documentSnapshot);
                            } else {
                                Log.d("Hospital_Dashboard", "Skipping timer check - timer is being started or navigating between pages");
                            }
                        } else {
                            Log.d("Hospital_Dashboard", "Real-time listener: Throttled - too frequent updates");
                        }
                    } else {
                        Log.w("Hospital_Dashboard", "Real-time listener: Document does not exist or is null");
                    }
                });
    }

    // Method to process hospital data from document snapshot
    private void processHospitalData(com.google.firebase.firestore.DocumentSnapshot documentSnapshot) {
        Log.d("Hospital_Dashboard", "Processing hospital data...");
        
        // Load cached hospital name immediately for instant display
        loadCachedHospitalName();
        
        // Load hospital name
        String hospitalName = documentSnapshot.getString("hospitalName");
        if (hospitalName != null && tvHospitalName != null && !hospitalName.equals(tvHospitalName.getText().toString())) {
            tvHospitalName.setText(hospitalName);
            // Cache the name for future instant loading
            cacheHospitalName(hospitalName);
            Log.d("Hospital_Dashboard", "Hospital name set: " + hospitalName);
        }

        // Load status information
        Long totalBeds = documentSnapshot.getLong("totalBeds");
        Long availableBeds = documentSnapshot.getLong("availableBeds");
        // Try both field names for backward compatibility
        Long doctorsAvailable = documentSnapshot.getLong("availableDoctors");
        if (doctorsAvailable == null) {
            doctorsAvailable = documentSnapshot.getLong("doctorsAvailable");
        }
        String erStatus = documentSnapshot.getString("erStatus");
        
        // Debug logging
        Log.d("Hospital_Dashboard", "Loaded data - totalBeds: " + totalBeds + 
              ", availableBeds: " + availableBeds + 
              ", doctorsAvailable: " + doctorsAvailable + 
              ", erStatus: " + erStatus);

        if (availableBeds != null && tvAvailableBeds != null) {
            String bedsText = String.valueOf(availableBeds);
            if (!bedsText.equals(tvAvailableBeds.getText().toString())) {
                tvAvailableBeds.setText(bedsText);
                Log.d("Hospital_Dashboard", "Available beds set: " + availableBeds);
            }
        }
        if (doctorsAvailable != null && tvDoctorsAvailable != null) {
            String doctorsText = String.valueOf(doctorsAvailable);
            if (!doctorsText.equals(tvDoctorsAvailable.getText().toString())) {
                tvDoctorsAvailable.setText(doctorsText);
                Log.d("Hospital_Dashboard", "Doctors available set: " + doctorsAvailable);
            }
        }

        // Calculate and set automatic status
        if (totalBeds != null && availableBeds != null && doctorsAvailable != null && tvErStatus != null) {
            String autoStatus = calculateAutoStatus(totalBeds.intValue(), availableBeds.intValue(), doctorsAvailable.intValue());
            Log.d("Hospital_Dashboard", "Calculated status: " + autoStatus);
            if (!autoStatus.equals("unknown")) {
                String statusText = getStatusEmoji(autoStatus) + " " + autoStatus.toUpperCase();
                if (!statusText.equals(tvErStatus.getText().toString())) {
                    tvErStatus.setText(statusText);
                    tvErStatus.setTextColor(getStatusColor(autoStatus));
                    Log.d("Hospital_Dashboard", "Status updated to: " + statusText);
                }
            } else {
                String unknownStatus = getString(R.string.status_not_available_er);
                if (!unknownStatus.equals(tvErStatus.getText().toString())) {
                    tvErStatus.setText(unknownStatus);
                    tvErStatus.setTextColor(getStatusColor("unknown"));
                    Log.w("Hospital_Dashboard", "Status calculation returned unknown");
                }
            }
        } else {
            Log.w("Hospital_Dashboard", "Missing data for status calculation - totalBeds: " + totalBeds + 
                  ", availableBeds: " + availableBeds + 
                  ", doctorsAvailable: " + doctorsAvailable);
        }
    }

    // Method to manually refresh hospital status
    private void refreshHospitalStatus() {
        Log.d("Hospital_Dashboard", "Manually refreshing hospital status...");
        loadHospitalStatus();
    }

    // Method to test database connection and get current status
    private void testDatabaseConnection() {
        if (userId == null) {
            Log.w("Hospital_Dashboard", "userId is null, cannot test database connection");
            return;
        }

        Log.d("Hospital_Dashboard", "Testing database connection for userId: " + userId);
        
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d("Hospital_Dashboard", "Database test successful - Document exists");
                        Log.d("Hospital_Dashboard", "Available beds: " + documentSnapshot.getLong("availableBeds"));
                        Log.d("Hospital_Dashboard", "Available doctors: " + documentSnapshot.getLong("availableDoctors"));
                        Log.d("Hospital_Dashboard", "ER Status: " + documentSnapshot.getString("erStatus"));
                        
                        // Process the data to update UI
                        processHospitalData(documentSnapshot);
                    } else {
                        Log.w("Hospital_Dashboard", "Database test failed - Document does not exist");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "Database test failed: " + e.getMessage(), e);
                });
    }

    // Method to force update status (for testing)
    private void forceUpdateStatus() {
        Log.d("Hospital_Dashboard", "=== forceUpdateStatus called ===");
        Log.d("Hospital_Dashboard", "Force updating status...");
        if (userId == null) {
            Log.w("Hospital_Dashboard", "userId is null, cannot force update");
            return;
        }
        
        // Force a fresh get from database
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d("Hospital_Dashboard", "Force update successful - processing data");
                        processHospitalData(documentSnapshot);
                        
                        // Check if status update is required and update countdown timer
                        checkStatusUpdateRequirement(documentSnapshot);
                    } else {
                        Log.w("Hospital_Dashboard", "Force update failed - document does not exist");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "Force update failed: " + e.getMessage(), e);
                });
    }

    // Debug method to check current user status
    private void debugCurrentUserStatus() {
        if (userId == null) {
            Log.w("Hospital_Dashboard", "DEBUG: userId is null");
            return;
        }

        Log.d("Hospital_Dashboard", "=== DEBUG: CURRENT USER STATUS ===");
        Log.d("Hospital_Dashboard", "DEBUG: userId = " + userId);
        
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d("Hospital_Dashboard", "DEBUG: Document exists = " + documentSnapshot.exists());
                    if (documentSnapshot.exists()) {
                        Log.d("Hospital_Dashboard", "DEBUG: All document fields = " + documentSnapshot.getData());
                        String status = documentSnapshot.getString("status");
                        Log.d("Hospital_Dashboard", "DEBUG: Status field = '" + status + "'");
                        Log.d("Hospital_Dashboard", "DEBUG: Status type = " + (status != null ? status.getClass().getSimpleName() : "null"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "DEBUG: Error = " + e.getMessage(), e);
                });
    }

    private String getStatusEmoji(String status) {
        if (status == null) return "⚪";
        switch (status.toLowerCase()) {
            case "available":
                return "🟢";
            case "busy":
                return "🟡";
            case "crowded":
                return "🔴";
            default:
                return "⚪";
        }
    }

    private int getStatusColor(String status) {
        if (status == null) return 0xFF9E9E9E; // Gray
        switch (status.toLowerCase()) {
            case "available":
                return 0xFF4CAF50; // Green
            case "busy":
                return 0xFFFF9800; // Orange
            case "crowded":
                return 0xFFF44336; // Red
            default:
                return 0xFF9E9E9E; // Gray
        }
    }

    /**
     * Checks if hospital status update is required (every hour)
     */
    private void checkStatusUpdateRequirement(com.google.firebase.firestore.DocumentSnapshot documentSnapshot) {
        Log.d("Hospital_Dashboard", "=== checkStatusUpdateRequirement called ===");
        try {
            com.google.firebase.Timestamp lastUpdated = documentSnapshot.getTimestamp("lastUpdated");
            Log.d("Hospital_Dashboard", "lastUpdated timestamp: " + lastUpdated);
            if (lastUpdated != null) {
                long lastUpdateTime = lastUpdated.toDate().getTime();
                long currentTime = System.currentTimeMillis();
                long timeSinceLastUpdate = currentTime - lastUpdateTime;
                
                Log.d("Hospital_Dashboard", "=== TIMER DEBUG INFO ===");
                Log.d("Hospital_Dashboard", "Last update time: " + lastUpdated.toDate());
                Log.d("Hospital_Dashboard", "Last update timestamp (ms): " + lastUpdateTime);
                Log.d("Hospital_Dashboard", "Current time: " + new java.util.Date());
                Log.d("Hospital_Dashboard", "Current timestamp (ms): " + currentTime);
                Log.d("Hospital_Dashboard", "Time since last update: " + (timeSinceLastUpdate / (1000 * 60)) + " minutes " + ((timeSinceLastUpdate % (1000 * 60)) / 1000) + " seconds");
                Log.d("Hospital_Dashboard", "Time since last update (ms): " + timeSinceLastUpdate);
                Log.d("Hospital_Dashboard", "STATUS_UPDATE_INTERVAL_MS: " + STATUS_UPDATE_INTERVAL_MS + " (10 minutes)");
                Log.d("Hospital_Dashboard", "Is timer currently running: " + isTimerRunning);
                
                if (timeSinceLastUpdate >= STATUS_UPDATE_INTERVAL_MS) {
                    // Status update is required
                    Log.d("Hospital_Dashboard", "Status update is OVERDUE - showing dialog");
                    showStatusUpdateRequiredDialog(timeSinceLastUpdate);
                } else {
                    // Calculate time remaining until next update is required
                    long timeRemaining = STATUS_UPDATE_INTERVAL_MS - timeSinceLastUpdate;
                    long minutesRemaining = timeRemaining / (1000 * 60);
                    long secondsRemaining = (timeRemaining % (1000 * 60)) / 1000;
                    Log.d("Hospital_Dashboard", "Time remaining: " + timeRemaining + " ms");
                    Log.d("Hospital_Dashboard", "Next status update required in: " + minutesRemaining + " minutes " + secondsRemaining + " seconds");
                    Log.d("Hospital_Dashboard", "Timer will continue from: " + minutesRemaining + ":" + String.format("%02d", secondsRemaining));
                }
                
                // Update UI with last updated and next update times
                // Only update if timer is not already running and not navigating between pages
                if (!isTimerRunning && !isNavigatingBetweenPages) {
                    Log.d("Hospital_Dashboard", "Updating UI - timer not running or service not bound");
                    updateStatusUpdateUI(lastUpdated, timeSinceLastUpdate);
                } else {
                    Log.d("Hospital_Dashboard", "Skipping UI update - timer is already running correctly or navigating between pages");
                }
            } else {
                // No lastUpdated timestamp, consider it as requiring immediate update
                Log.w("Hospital_Dashboard", "No lastUpdated timestamp found - requiring immediate update");
                
                // Update UI to show immediate update required
                if (tvCountdownTimer != null) {
                    tvCountdownTimer.setText(getString(R.string.update_required_now));
                    tvCountdownTimer.setTextColor(0xFFFF5722);
                }
                if (tvLastUpdated != null) {
                    tvLastUpdated.setText(getString(R.string.last_updated, "Never"));
                    tvLastUpdated.setTextColor(0xFFFF5722);
                }
                
                showStatusUpdateRequiredDialog(Long.MAX_VALUE);
            }
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "Error checking status update requirement", e);
        }
    }
    
    /**
     * Shows dialog requiring hospital to update status
     */
    private void showStatusUpdateRequiredDialog(long timeSinceLastUpdate) {
        // Prevent duplicate dialogs
        if (isDialogShowing) {
            Log.d("Hospital_Dashboard", "Dialog already showing, skipping duplicate dialog");
            return;
        }
        
        isDialogShowing = true;
        Log.d("Hospital_Dashboard", "Showing status update required dialog");
        
        // Play alert sound for the reminder
        playReminderAlertSound();
        
        String message;
        if (timeSinceLastUpdate == Long.MAX_VALUE) {
            message = getString(R.string.status_update_required_immediate);
        } else {
            long minutesOverdue = timeSinceLastUpdate / (1000 * 60);
            if (minutesOverdue > 0) {
                message = getString(R.string.status_update_overdue, minutesOverdue);
            } else {
                message = getString(R.string.status_update_required);
            }
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.status_update_required_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.update_now), (dialog, which) -> {
                    // Stop the alert sound
                    stopReminderAlertSound();
                    // Navigate to status update screen with result handling
                    isDialogShowing = false;
                    Intent intent = new Intent(Hospital_Dashboard.this, Hospital_Status_Edit.class);
                    startActivityForResult(intent, 1001); // Request code for status update
                })
                .setNegativeButton(getString(R.string.remind_later), (dialog, which) -> {
                    // Stop the alert sound
                    stopReminderAlertSound();
                    // Schedule a reminder for later
                    scheduleStatusUpdateReminder();
                    isDialogShowing = false;
                })
                .setOnDismissListener(dialog -> {
                    // Stop the alert sound when dialog is dismissed
                    stopReminderAlertSound();
                    isDialogShowing = false;
                    Log.d("Hospital_Dashboard", "Status update dialog dismissed");
                })
                .setCancelable(false)
                .show();
    }
    
    /**
     * Schedules a reminder for status update
     */
    private void scheduleStatusUpdateReminder() {
        // You can implement a notification system here
        // For now, just show a toast
        Toast.makeText(this, getString(R.string.status_update_reminder_scheduled), Toast.LENGTH_LONG).show();
    }
    
    /**
     * Plays an alert sound for the 10-minute status update reminder
     */
    private void playReminderAlertSound() {
        try {
            // Stop any existing alert sound first
            stopReminderAlertSound();
            
            // Use alarm sound for more attention-grabbing alert
            Uri alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alertSound == null) {
                // Fallback to notification sound if alarm sound is not available
                alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            
            if (alertSound != null) {
                reminderMediaPlayer = MediaPlayer.create(this, alertSound);
                if (reminderMediaPlayer != null) {
                    reminderMediaPlayer.setLooping(true); // Loop the sound until stopped
                    reminderMediaPlayer.start();
                    Log.d("Hospital_Dashboard", "Playing reminder alert sound (looping)");
                } else {
                    Log.w("Hospital_Dashboard", "Failed to create MediaPlayer for reminder alert sound");
                }
            } else {
                Log.w("Hospital_Dashboard", "No default alert sound available");
            }
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "Error playing reminder alert sound", e);
        }
    }
    
    /**
     * Stops the reminder alert sound
     */
    private void stopReminderAlertSound() {
        try {
            if (reminderMediaPlayer != null) {
                if (reminderMediaPlayer.isPlaying()) {
                    reminderMediaPlayer.stop();
                }
                reminderMediaPlayer.release();
                reminderMediaPlayer = null;
                Log.d("Hospital_Dashboard", "Reminder alert sound stopped");
            }
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "Error stopping reminder alert sound", e);
        }
    }
    
    /**
     * Updates the status update UI with countdown timer
     */
    private void updateStatusUpdateUI(com.google.firebase.Timestamp lastUpdated, long timeSinceLastUpdate) {
        Log.d("Hospital_Dashboard", "=== updateStatusUpdateUI called ===");
        Log.d("Hospital_Dashboard", "tvLastUpdated is null: " + (tvLastUpdated == null));
        Log.d("Hospital_Dashboard", "tvCountdownTimer is null: " + (tvCountdownTimer == null));
        
        if (tvLastUpdated == null || tvCountdownTimer == null) {
            Log.d("Hospital_Dashboard", "UI elements are null, returning");
            return;
        }
        
        try {
            // Format last updated time
            Date lastUpdateDate = lastUpdated.toDate();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            String lastUpdatedText = getString(R.string.last_updated, sdf.format(lastUpdateDate));
            tvLastUpdated.setText(lastUpdatedText);
            
            // Timer cancellation is now handled in startCountdownTimer method
            
            if (timeSinceLastUpdate >= STATUS_UPDATE_INTERVAL_MS) {
                // Overdue - show "Update Required Now!"
                tvCountdownTimer.setText(getString(R.string.update_required_now));
                tvCountdownTimer.setTextColor(0xFFFF5722);
                tvLastUpdated.setTextColor(0xFFFF5722);
            } else {
                // Calculate time remaining until next update is required
                long timeRemaining = STATUS_UPDATE_INTERVAL_MS - timeSinceLastUpdate;
                startCountdownTimer(timeRemaining);
                
                // Change color based on urgency
                if (timeSinceLastUpdate >= (STATUS_UPDATE_INTERVAL_MS * 0.8)) {
                    // Close to due - orange
                    tvLastUpdated.setTextColor(0xFFFF9800);
                } else {
                    // Normal - black
                    tvLastUpdated.setTextColor(0xFF000000);
                }
            }
            
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "Error updating status update UI", e);
        }
    }
    
    /**
     * Starts the countdown timer for status update
     */
    private void startCountdownTimer(long timeRemainingMs) {
        // Prevent multiple timer starts
        if (isTimerBeingStarted) {
            Log.d("Hospital_Dashboard", "Timer is already being started, skipping duplicate start");
            return;
        }
        
        isTimerBeingStarted = true;
        
        long minutes = timeRemainingMs / (1000 * 60);
        long seconds = (timeRemainingMs % (1000 * 60)) / 1000;
        Log.d("Hospital_Dashboard", "=== STARTING TIMER DEBUG ===");
        Log.d("Hospital_Dashboard", "Time remaining: " + timeRemainingMs + " ms");
        Log.d("Hospital_Dashboard", "Minutes: " + minutes + ", Seconds: " + seconds);
        Log.d("Hospital_Dashboard", "Is timer being started: " + isTimerBeingStarted);
        Log.d("Hospital_Dashboard", "Is timer running: " + isTimerRunning);
        // GlobalTimerService removed
        
        // Cancel any existing local timer to prevent conflicts
        if (statusCountdownTimer != null) {
            statusCountdownTimer.cancel();
            statusCountdownTimer = null;
        }
        
        // Save timer state to SharedPreferences for persistence
        saveTimerState(System.currentTimeMillis(), timeRemainingMs);
        
        // Schedule background notification for when app is closed
        scheduleBackgroundNotification();
        
        // Start simple local timer instead of complex global service
        startSimpleTimer(timeRemainingMs);
        
        // Set local timer state for reference
        isTimerRunning = true;
        timerStartTime = System.currentTimeMillis();
        timerDuration = timeRemainingMs;
        
        Log.d("Hospital_Dashboard", "Simple timer started and state saved to SharedPreferences");
        
        // Reset the flag after a short delay
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            isTimerBeingStarted = false;
        }, 1000);
    }
    
    /**
     * Saves timer state to SharedPreferences for persistence
     */
    private void saveTimerState(long startTime, long duration) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(TIMER_START_TIME_KEY, startTime);
        editor.putLong(TIMER_DURATION_KEY, duration);
        editor.apply();
        Log.d("Hospital_Dashboard", "Timer state saved: start=" + startTime + ", duration=" + duration);
    }
    
    /**
     * Restores timer state from SharedPreferences
     */
    private boolean restoreTimerState() {
        long savedStartTime = sharedPreferences.getLong(TIMER_START_TIME_KEY, 0);
        long savedDuration = sharedPreferences.getLong(TIMER_DURATION_KEY, 0);
        
        if (savedStartTime > 0 && savedDuration > 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - savedStartTime;
            long remainingTime = savedDuration - elapsedTime;
            
            Log.d("Hospital_Dashboard", "=== RESTORING TIMER STATE ===");
            Log.d("Hospital_Dashboard", "Saved start time: " + new java.util.Date(savedStartTime));
            Log.d("Hospital_Dashboard", "Current time: " + new java.util.Date(currentTime));
            Log.d("Hospital_Dashboard", "Elapsed time: " + (elapsedTime / 1000) + " seconds");
            Log.d("Hospital_Dashboard", "Remaining time: " + (remainingTime / 1000) + " seconds");
            
            if (remainingTime > 0) {
                // Timer is still valid, restore it
                timerStartTime = savedStartTime;
                timerDuration = savedDuration;
                isTimerRunning = true;
                
                // Start the timer with remaining time
                startSimpleTimer(remainingTime);
                Log.d("Hospital_Dashboard", "✅ Timer restored successfully with " + (remainingTime / 1000) + " seconds remaining");
                return true;
            } else {
                // Timer has expired, clear saved state
                clearTimerState();
                Log.d("Hospital_Dashboard", "⚠️ Timer has expired, cleared saved state");
                return false;
            }
        }
        
        Log.d("Hospital_Dashboard", "No saved timer state found");
        return false;
    }
    
    /**
     * Clears saved timer state
     */
    private void clearTimerState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(TIMER_START_TIME_KEY);
        editor.remove(TIMER_DURATION_KEY);
        editor.apply();
        Log.d("Hospital_Dashboard", "Timer state cleared");
    }
    
    /**
     * Starts a simple local timer
     */
    private void startSimpleTimer(long timeRemainingMs) {
        if (statusCountdownTimer != null) {
            statusCountdownTimer.cancel();
        }
        
        statusCountdownTimer = new CountDownTimer(timeRemainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Prevent multiple timer updates
                if (isTimerUpdating) {
                    Log.d("Hospital_Dashboard", "Timer update already in progress, skipping");
                    return;
                }
                
                isTimerUpdating = true;
                
                // Update display
                long totalSeconds = millisUntilFinished / 1000;
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long seconds = totalSeconds % 60;
                
                String countdownText = getString(R.string.countdown_format, hours, minutes, seconds);
                String displayText = getString(R.string.next_update_in, countdownText);
                
                if (tvCountdownTimer != null) {
                    tvCountdownTimer.setText(displayText);
                    
                    // Change color based on remaining time
                    if (millisUntilFinished <= (STATUS_UPDATE_INTERVAL_MS * 0.1)) {
                        tvCountdownTimer.setTextColor(0xFFFF5722);
                    } else if (millisUntilFinished <= (STATUS_UPDATE_INTERVAL_MS * 0.2)) {
                        tvCountdownTimer.setTextColor(0xFFFF9800);
                    } else {
                        tvCountdownTimer.setTextColor(0xFF2196F3);
                    }
                }
                
                // Reset the flag after a short delay
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    isTimerUpdating = false;
                }, 100);
            }
            
            @Override
            public void onFinish() {
                // Timer finished
                isTimerRunning = false;
                clearTimerState();
                
                if (tvCountdownTimer != null) {
                    tvCountdownTimer.setText(getString(R.string.update_required_now));
                    tvCountdownTimer.setTextColor(0xFFFF5722);
                }
                
                // Play notification sound
                playNotificationSound();
                
                // Schedule background notification for when app is closed
                scheduleBackgroundNotification();
                
                // Show update required dialog
                showStatusUpdateRequiredDialog(STATUS_UPDATE_INTERVAL_MS);
            }
        };
        
        statusCountdownTimer.start();
        Log.d("Hospital_Dashboard", "Simple timer started for " + (timeRemainingMs / 1000) + " seconds");
    }
    
    /**
     * Schedules background notification for when app is closed
     */
    private void scheduleBackgroundNotification() {
        Log.d("Hospital_Dashboard", "Scheduling background notification for status update");
        
        Intent serviceIntent = new Intent(this, HospitalStatusNotificationService.class);
        serviceIntent.putExtra("action", "schedule_notification");
        startService(serviceIntent);
    }
    
    /**
     * Cancels background notifications (called when user logs out)
     */
    private void cancelBackgroundNotifications() {
        Log.d("Hospital_Dashboard", "Canceling background notifications");
        
        Intent serviceIntent = new Intent(this, HospitalStatusNotificationService.class);
        serviceIntent.putExtra("action", "cancel_notification");
        startService(serviceIntent);
    }
    
    /**
     * Resumes the timer from where it left off
     */
    private void resumeTimer() {
        if (timerStartTime == 0 || timerDuration == 0) {
            Log.d("Hospital_Dashboard", "No timer to resume");
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - timerStartTime;
        long remainingTime = timerDuration - elapsedTime;
        
        Log.d("Hospital_Dashboard", "=== RESUMING TIMER ===");
        Log.d("Hospital_Dashboard", "Timer start time: " + new java.util.Date(timerStartTime));
        Log.d("Hospital_Dashboard", "Current time: " + new java.util.Date(currentTime));
        Log.d("Hospital_Dashboard", "Elapsed time: " + (elapsedTime / 1000) + " seconds");
        Log.d("Hospital_Dashboard", "Original duration: " + (timerDuration / 1000) + " seconds");
        Log.d("Hospital_Dashboard", "Remaining time: " + (remainingTime / 1000) + " seconds");
        
        if (remainingTime <= 0) {
            Log.d("Hospital_Dashboard", "Timer has expired, showing update required");
            tvCountdownTimer.setText(getString(R.string.update_required_now));
            tvCountdownTimer.setTextColor(0xFFFF5722);
            tvLastUpdated.setTextColor(0xFFFF5722);
            
            // Play notification sound
            playNotificationSound();
            
            showStatusUpdateRequiredDialog(STATUS_UPDATE_INTERVAL_MS);
        } else {
            Log.d("Hospital_Dashboard", "Resuming timer with " + (remainingTime / 1000) + " seconds remaining");
            // Don't call startCountdownTimer to avoid conflicts, just start simple timer
            startSimpleTimer(remainingTime);
        }
    }

    private String calculateAutoStatus(int totalBeds, int availableBeds, int doctors) {
        Log.d("Hospital_Dashboard", "=== CALCULATION DEBUG ===");
        Log.d("Hospital_Dashboard", "Input - totalBeds: " + totalBeds + ", availableBeds: " + availableBeds + ", doctors: " + doctors);
        
        // Validate input
        if (totalBeds <= 0 || availableBeds < 0 || doctors <= 0) {
            Log.w("Hospital_Dashboard", "Validation failed - returning unknown");
            return "unknown";
        }
        
        if (availableBeds > totalBeds) {
            Log.w("Hospital_Dashboard", "Available beds > total beds - returning unknown");
            return "unknown";
        }
        
        // Calculate capacity percentage (occupied beds / total beds)
        double capacityPercentage = ((double) (totalBeds - availableBeds) / totalBeds) * 100;
        
        // Calculate occupied beds per doctor ratio (this indicates workload)
        int occupiedBeds = totalBeds - availableBeds;
        double occupiedBedsPerDoctor = (double) occupiedBeds / doctors;
        
        // Calculate total beds per doctor ratio (this indicates overall capacity)
        double totalBedsPerDoctor = (double) totalBeds / doctors;
        
        Log.d("Hospital_Dashboard", "Calculated - capacityPercentage: " + capacityPercentage + 
              "%, occupiedBedsPerDoctor: " + occupiedBedsPerDoctor + ", totalBedsPerDoctor: " + totalBedsPerDoctor);
        
        // Automatic status logic based on capacity and workload
        String result;
        if (availableBeds == 0) {
            result = "crowded"; // No available beds
        } else if (capacityPercentage >= 90 || occupiedBedsPerDoctor >= 8 || doctors < 2) {
            result = "crowded"; // At or near capacity, or insufficient staff
        } else if (capacityPercentage >= 70 || occupiedBedsPerDoctor >= 6 || doctors < 3) {
            result = "busy"; // High capacity or high workload
        } else if (capacityPercentage >= 50 || occupiedBedsPerDoctor >= 4) {
            result = "busy"; // Moderate capacity or moderate workload
        } else {
            result = "available"; // Good capacity and low workload
        }
        
        Log.d("Hospital_Dashboard", "Final result: " + result);
        return result;
    }
    
    /**
     * Starts the status notification service
     */
    private void startStatusNotificationService() {
        if (userId == null || !"hospital".equals(userType)) {
            Log.d("Hospital_Dashboard", "Not a hospital user, skipping notification service");
            return;
        }
        
        Intent serviceIntent = new Intent(this, HospitalStatusNotificationService.class);
        serviceIntent.putExtra("action", "schedule_notification");
        startService(serviceIntent);
        
        Log.d("Hospital_Dashboard", "Started status notification service");
    }
    
    /**
     * Stops the status notification service (called on logout)
     */
    private void stopStatusNotificationService() {
        Intent serviceIntent = new Intent(this, HospitalStatusNotificationService.class);
        serviceIntent.putExtra("action", "cancel_notification");
        startService(serviceIntent);
        
        Log.d("Hospital_Dashboard", "Stopped status notification service");
    }
    
    /**
     * Refreshes the timer after a successful status update
     */
    private void refreshTimerAfterStatusUpdate() {
        Log.d("Hospital_Dashboard", "=== REFRESHING TIMER AFTER STATUS UPDATE ===");
        Log.d("Hospital_Dashboard", "Current time before refresh: " + new java.util.Date());
        
        // Reset timer state
        if (statusCountdownTimer != null) {
            statusCountdownTimer.cancel();
            isTimerRunning = false;
        }
        timerStartTime = 0;
        timerDuration = 0;
        
        // Clear saved timer state since we're starting fresh
        clearTimerState();
        
        // Add a small delay to ensure Firebase has updated the timestamp
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Log.d("Hospital_Dashboard", "Delayed refresh - Current time: " + new java.util.Date());
            // Force update the status to get fresh data and start new timer
            forceUpdateStatus();
        }, 1000); // 1 second delay
    }

    private void updateTimerDisplayOnly() {
        if (timerStartTime > 0 && timerDuration > 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - timerStartTime;
            long remainingTime = timerDuration - elapsedTime;
            
            if (remainingTime > 0) {
                // Update the display with current remaining time
                long totalSeconds = remainingTime / 1000;
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long seconds = totalSeconds % 60;
                
                String countdownText = getString(R.string.countdown_format, hours, minutes, seconds);
                String displayText = getString(R.string.next_update_in, countdownText);
                
                if (tvCountdownTimer != null) {
                    tvCountdownTimer.setText(displayText);
                    
                    // Update color based on remaining time
                    if (remainingTime <= (STATUS_UPDATE_INTERVAL_MS * 0.1)) {
                        tvCountdownTimer.setTextColor(0xFFFF5722);
                    } else if (remainingTime <= (STATUS_UPDATE_INTERVAL_MS * 0.2)) {
                        tvCountdownTimer.setTextColor(0xFFFF9800);
                    } else {
                        tvCountdownTimer.setTextColor(0xFF2196F3);
                    }
                }
                
                Log.d("Hospital_Dashboard", "Updated timer display: " + minutes + ":" + String.format("%02d", seconds));
            } else {
                // Timer has expired
                showExpiredTimerState();
            }
        }
    }

    
    /**
     * Shows expired timer state
     */
    private void showExpiredTimerState() {
        if (tvCountdownTimer != null) {
            tvCountdownTimer.setText(getString(R.string.update_required_now));
            tvCountdownTimer.setTextColor(0xFFFF5722);
        }
        
        // Play notification sound
        playNotificationSound();
        
        Log.d("Hospital_Dashboard", "Timer has expired, showing update required");
    }
    
    /**
     * Test method to manually trigger timer with specific remaining time
     */
    public void testTimerWithRemainingTime(long minutesRemaining) {
        Log.d("Hospital_Dashboard", "=== TEST TIMER ===");
        Log.d("Hospital_Dashboard", "Testing timer with " + minutesRemaining + " minutes remaining");
        
        long timeRemainingMs = minutesRemaining * 60 * 1000; // Convert to milliseconds
        startCountdownTimer(timeRemainingMs);
    }

    private void stopGlobalTimerService() {
        Intent serviceIntent = new Intent(this, GlobalTimerService.class);
        stopService(serviceIntent);
        Log.d("Hospital_Dashboard", "GlobalTimerService stopped to remove notification");
    }

    /**
     * Plays a notification sound when timer expires
     */
    private void playNotificationSound() {
        try {
            // Get the default notification sound
            Uri notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            
            if (notificationSound != null) {
                MediaPlayer mediaPlayer = MediaPlayer.create(this, notificationSound);
                if (mediaPlayer != null) {
                    mediaPlayer.setOnCompletionListener(mp -> {
                        mp.release();
                        Log.d("Hospital_Dashboard", "Notification sound played successfully");
                    });
                    mediaPlayer.start();
                } else {
                    Log.w("Hospital_Dashboard", "Failed to create MediaPlayer for notification sound");
                }
            } else {
                Log.w("Hospital_Dashboard", "No default notification sound available");
            }
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "Error playing notification sound", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("Hospital_Dashboard", "=== onDestroy() called ===");
        
        // Cancel countdown timer to prevent memory leaks
        if (statusCountdownTimer != null) {
            statusCountdownTimer.cancel();
            isTimerRunning = false;
            // Reset timer info on destroy
            timerStartTime = 0;
            timerDuration = 0;
        }
    }

    private void loadCachedHospitalName() {
        String cachedName = sharedPreferences.getString(KEY_CACHED_HOSPITAL_NAME, null);
        if (cachedName != null && !cachedName.isEmpty()) {
            tvHospitalName.setText(cachedName);
            Log.d("Hospital_Dashboard", "Loaded cached hospital name: " + cachedName);
        } else {
            tvHospitalName.setText("Loading...");
            Log.d("Hospital_Dashboard", "No cached hospital name found, showing loading...");
        }
    }

    private void cacheHospitalName(String hospitalName) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_HOSPITAL_NAME, hospitalName)
                .apply();
        Log.d("Hospital_Dashboard", "Cached hospital name: " + hospitalName);
    }
    
    /**
     * Start listening for emergency notifications from rescuers
     */
    private void startEmergencyNotificationListener() {
        Log.d("Hospital_Dashboard", "🚨 Starting emergency notification listener...");
        
        // Get current user
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w("Hospital_Dashboard", "No authenticated user, cannot start emergency listener");
            return;
        }
        
        String userId = currentUser.getUid();
        Log.d("Hospital_Dashboard", "👤 Listening for emergency notifications for user: " + userId);
        
        // Listen for emergency notifications in the hospital's notifications collection
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("type", "EMERGENCY_INCOMING")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e("Hospital_Dashboard", "❌ Error listening for emergency notifications", error);
                        return;
                    }
                    
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        Log.d("Hospital_Dashboard", "🚨 Received " + querySnapshot.size() + " emergency notification(s)");
                        
                        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                            try {
                                handleEmergencyNotification(document);
                            } catch (Exception e) {
                                Log.e("Hospital_Dashboard", "❌ Error handling emergency notification", e);
                            }
                        }
                    } else {
                        Log.d("Hospital_Dashboard", "📭 No emergency notifications found");
                    }
                });
    }
    
    /**
     * Handle incoming emergency notification
     */
    private void handleEmergencyNotification(DocumentSnapshot document) {
        try {
            String notificationId = document.getId();
            String type = document.getString("type");
            String title = document.getString("title");
            String message = document.getString("message");
            String emergencyId = document.getString("emergencyId");
            String seniorName = document.getString("seniorName");
            String seniorPhone = document.getString("seniorPhone");
            String rescuerName = document.getString("rescuerName");
            String rescuerPhone = document.getString("rescuerPhone");
            String hospitalName = document.getString("hospitalName");
            Double estimatedArrivalMinutes = document.getDouble("estimatedArrivalMinutes");
            Long timestamp = document.getLong("timestamp");
            
            Log.d("Hospital_Dashboard", "🚨 Emergency Alert Details:");
            Log.d("Hospital_Dashboard", "   📋 ID: " + notificationId);
            Log.d("Hospital_Dashboard", "   🏥 Hospital: " + hospitalName);
            Log.d("Hospital_Dashboard", "   👴 Senior: " + seniorName + " (" + seniorPhone + ")");
            Log.d("Hospital_Dashboard", "   👨‍⚕️ Rescuer: " + rescuerName + " (" + rescuerPhone + ")");
            Log.d("Hospital_Dashboard", "   ⏱️ ETA: " + (estimatedArrivalMinutes != null ? estimatedArrivalMinutes + " minutes" : "Unknown"));
            
            // Show emergency alert dialog
            showEmergencyAlertDialog(title, message, seniorName, seniorPhone, rescuerName, rescuerPhone, 
                                   estimatedArrivalMinutes, emergencyId, notificationId);
            
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "❌ Error parsing emergency notification", e);
        }
    }
    
    /**
     * Show emergency alert dialog to hospital staff
     */
    private void showEmergencyAlertDialog(String title, String message, String seniorName, String seniorPhone,
                                        String rescuerName, String rescuerPhone, Double estimatedArrivalMinutes,
                                        String emergencyId, String notificationId) {
        
        // Create a detailed alert message
        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append("🚨 EMERGENCY PATIENT INCOMING\n\n");
        alertMessage.append("👴 Senior: ").append(seniorName != null ? seniorName : "Unknown").append("\n");
        alertMessage.append("📞 Senior Phone: ").append(seniorPhone != null ? seniorPhone : "Not available").append("\n");
        alertMessage.append("👨‍⚕️ Rescuer: ").append(rescuerName != null ? rescuerName : "Unknown").append("\n");
        alertMessage.append("📞 Rescuer Phone: ").append(rescuerPhone != null ? rescuerPhone : "Not available").append("\n");
        if (estimatedArrivalMinutes != null) {
            alertMessage.append("⏱️ Estimated Arrival: ").append(String.format("%.1f", estimatedArrivalMinutes)).append(" minutes\n");
        }
        alertMessage.append("\n").append(message != null ? message : "Emergency patient being transported to your facility");
        
        // Create and show alert dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title != null ? title : "🚨 Emergency Alert")
                .setMessage(alertMessage.toString())
                .setPositiveButton("✅ Acknowledged", (dialog, which) -> {
                    // Mark notification as read
                    markNotificationAsRead(notificationId);
                    Log.d("Hospital_Dashboard", "✅ Emergency alert acknowledged by hospital staff");
                })
                .setNegativeButton("📞 Call Rescuer", (dialog, which) -> {
                    // Mark notification as read
                    markNotificationAsRead(notificationId);
                    // Call rescuer
                    if (rescuerPhone != null && !rescuerPhone.isEmpty()) {
                        callRescuer(rescuerPhone);
                    }
                    Log.d("Hospital_Dashboard", "📞 Hospital staff calling rescuer: " + rescuerPhone);
                })
                .setCancelable(false)
                .show();
                
        // Play emergency sound
        playEmergencyAlertSound();
    }
    
    /**
     * Mark notification as read and update hospital list with senior information
     */
    private void markNotificationAsRead(String notificationId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        String userId = currentUser.getUid();
        
        // First, get the notification data before marking as read
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Extract senior information from notification
                        String seniorName = documentSnapshot.getString("seniorName");
                        String seniorPhone = documentSnapshot.getString("seniorPhone");
                        String seniorAddress = documentSnapshot.getString("seniorAddress");
                        String rescuerName = documentSnapshot.getString("rescuerName");
                        String rescuerPhone = documentSnapshot.getString("rescuerPhone");
                        String emergencyId = documentSnapshot.getString("emergencyId");
                        Double estimatedArrivalMinutes = documentSnapshot.getDouble("estimatedArrivalMinutes");
                        Long timestamp = documentSnapshot.getLong("timestamp");
                        
                        Log.d("Hospital_Dashboard", "📋 Extracted senior info: " + seniorName + " (" + seniorPhone + ")");
                        
                        // Update hospital list with senior information
                        updateHospitalListWithSeniorInfo(seniorName, seniorPhone, seniorAddress, 
                                                       rescuerName, rescuerPhone, emergencyId, 
                                                       estimatedArrivalMinutes, timestamp);
                        
                        // Mark notification as read
                        markNotificationAsReadInternal(notificationId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "❌ Failed to get notification data", e);
                    // Still try to mark as read even if getting data failed
                    markNotificationAsReadInternal(notificationId);
                });
    }
    
    /**
     * Internal method to mark notification as read
     */
    private void markNotificationAsReadInternal(String notificationId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        String userId = currentUser.getUid();
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .update("isRead", true, "isActive", false)
                .addOnSuccessListener(aVoid -> {
                    Log.d("Hospital_Dashboard", "✅ Notification marked as read: " + notificationId);
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "❌ Failed to mark notification as read", e);
                });
    }
    
    /**
     * Update hospital list with senior information
     */
    private void updateHospitalListWithSeniorInfo(String seniorName, String seniorPhone, String seniorAddress,
                                                String rescuerName, String rescuerPhone, String emergencyId,
                                                Double estimatedArrivalMinutes, Long timestamp) {
        Log.d("Hospital_Dashboard", "🔄 Updating hospital list with senior information...");
        
        // Get current hospital data
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        String userId = currentUser.getUid();
        
        // Update the hospital document with senior information
        Map<String, Object> seniorInfo = new HashMap<>();
        seniorInfo.put("seniorName", seniorName);
        seniorInfo.put("seniorPhone", seniorPhone);
        seniorInfo.put("seniorAddress", seniorAddress);
        seniorInfo.put("rescuerName", rescuerName);
        seniorInfo.put("rescuerPhone", rescuerPhone);
        seniorInfo.put("emergencyId", emergencyId);
        seniorInfo.put("estimatedArrivalMinutes", estimatedArrivalMinutes);
        seniorInfo.put("emergencyTimestamp", timestamp);
        seniorInfo.put("hasIncomingEmergency", true);
        seniorInfo.put("lastEmergencyUpdate", System.currentTimeMillis());
        
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .update(seniorInfo)
                .addOnSuccessListener(aVoid -> {
                    Log.d("Hospital_Dashboard", "✅ Hospital list updated with senior information");
                    Log.d("Hospital_Dashboard", "👴 Senior: " + seniorName + " (" + seniorPhone + ")");
                    Log.d("Hospital_Dashboard", "👨‍⚕️ Rescuer: " + rescuerName + " (" + rescuerPhone + ")");
                    Log.d("Hospital_Dashboard", "⏱️ ETA: " + (estimatedArrivalMinutes != null ? estimatedArrivalMinutes + " minutes" : "Unknown"));
                    
                    
                    // Show success message
                    Toast.makeText(this, getString(R.string.senior_info_added_to_hospital, seniorName), Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("Hospital_Dashboard", "❌ Failed to update hospital list with senior info", e);
                    Toast.makeText(this, getString(R.string.failed_to_update_hospital_list), Toast.LENGTH_SHORT).show();
                });
    }
    
    /**
     * Call rescuer
     */
    private void callRescuer(String phoneNumber) {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(android.net.Uri.parse("tel:" + phoneNumber));
            startActivity(callIntent);
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "❌ Error calling rescuer", e);
            Toast.makeText(this, getString(R.string.unable_to_make_call), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Play emergency alert sound
     */
    private void playEmergencyAlertSound() {
        try {
            // Play emergency sound
            android.media.RingtoneManager ringtoneManager = new android.media.RingtoneManager(this);
            android.net.Uri emergencySound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
            
            if (emergencySound != null) {
                android.media.Ringtone ringtone = ringtoneManager.getRingtone(this, emergencySound);
                if (ringtone != null) {
                    ringtone.play();
                }
            }
        } catch (Exception e) {
            Log.e("Hospital_Dashboard", "❌ Error playing emergency sound", e);
        }
    }
}