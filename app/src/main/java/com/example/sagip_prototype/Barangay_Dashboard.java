package com.example.sagip_prototype;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Barangay_Dashboard extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_LOGIN_TIMESTAMP = "loginTimestamp";
    private static final String KEY_CACHED_BARANGAY_NAME = "cachedBarangayName";
    private static final String KEY_NOTIFICATION_TOAST_SHOWN = "notificationToastShown";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView brgyName;
    private TextView currentLocationText;
    private TextView totalSeniorsCount;
    private TextView hospitalsCount;
    private RecyclerView hospitalRecyclerView;
    private HospitalLIstAdapter hospitalAdapter;
    private List<HospitalLIst> hospitalList;
    private String userType = "barangay";
    private String userId;
    private SharedPreferences sharedPreferences;
    private AlertDialog currentEmergencyDialog;
    private boolean hospitalsLoaded = false;
    private boolean dataLoadingInProgress = false;
    private java.util.Set<String> shownNotificationIds = new java.util.HashSet<>();

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private double currentLat = 0.0;
    private double currentLong = 0.0;
    
    // Broadcast receiver for language changes
    private android.content.BroadcastReceiver languageChangeReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if ("com.example.sagip_prototype.LANGUAGE_CHANGED".equals(intent.getAction())) {
                String languageCode = intent.getStringExtra("language");
                Log.d("Barangay_Dashboard", "Received language change broadcast: " + languageCode);
                
                // Apply the new language
                LanguageSelectionActivity.setAppLanguage(Barangay_Dashboard.this, languageCode);
                
                // Update UI elements
                updateUILanguage();
                
                // Reload cached data
                loadCachedBarangayName();
                
                // Show confirmation toast
                Toast.makeText(Barangay_Dashboard.this, getString(R.string.toast_language_change_detected), Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_barangay_dashboard);

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        brgyName = findViewById(R.id.barangayStaffName);
        currentLocationText = findViewById(R.id.currentLocationValue);
        totalSeniorsCount = findViewById(R.id.totalSeniorsCount);
        hospitalsCount = findViewById(R.id.hospitalsCount);
        hospitalRecyclerView = findViewById(R.id.   hospitalRecyclerView);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createLocationRequest();
        createLocationCallback();

        // Setup bottom navigation
        setupBottomNavigation();

        // Check for location permissions
        checkLocationPermission();

        // Setup RecyclerView for hospitals
        setupHospitalRecyclerView();

        // Load data immediately for instant display
        loadDataImmediately();

        // Check authentication state with improved persistence
        checkAuthStateWithPersistence();
        
        // Handle emergency notification intents
        handleEmergencyNotificationIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Load cached barangay name immediately when returning to dashboard
        loadCachedBarangayName();
        
        // Load data immediately for instant display (only if not already loaded)
        if (!hospitalsLoaded && !dataLoadingInProgress) {
            loadDataImmediately();
        } else {
            Log.d("Barangay_Dashboard", "Data already loaded or loading in progress, skipping onResume data loading");
        }
        
        // Update UI language when returning
        updateUILanguage();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
        // Verify login state when app resumes
        verifyLoginState();
        
        // Check notification permission status
        Log.d("Barangay_Dashboard", "🔔 App resumed, checking notification status");
        if (areNotificationsEnabled()) {
            Log.d("Barangay_Dashboard", "🔔 Notifications are now enabled!");
            // Only show toast once per session
            if (!sharedPreferences.getBoolean(KEY_NOTIFICATION_TOAST_SHOWN, false)) {
                Toast.makeText(this, getString(R.string.toast_notifications_enabled_barangay), Toast.LENGTH_SHORT).show();
                // Mark that we've shown the toast
                sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_TOAST_SHOWN, true).apply();
            }
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle language change without recreating activity
        Log.d("Barangay_Dashboard", "Configuration changed - language change detected");
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        // Update UI elements with new language
        updateUILanguage();
        
        // Reload cached barangay name to ensure it's still displayed
        loadCachedBarangayName();
        
        // Show toast to confirm language change
        Toast.makeText(this, getString(R.string.toast_language_change_detected), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }


    private void checkAuthStateWithPersistence() {
        // Check if user was previously logged in
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        userId = sharedPreferences.getString(KEY_USER_ID, null);
        String storedUserType = sharedPreferences.getString(KEY_USER_TYPE, null);
        String storedEmail = sharedPreferences.getString(KEY_USER_EMAIL, null);

        if (isLoggedIn && userId != null && storedUserType != null) {
            // User was previously logged in, restore session
            this.userType = storedUserType;

            // Verify Firebase Auth state
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // Firebase user is still authenticated, check status
                checkUserStatusAndRedirect();
            } else if (storedEmail != null) {
                // Firebase session expired but we have stored credentials
                // Check status before loading data
                checkUserStatusAndRedirect();
            } else {
                // No valid authentication, redirect to login
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
                userId = currentUser.getUid();
                saveLoginState(userId, userType, currentUser.getEmail());
                // Check status before loading data
                checkUserStatusAndRedirect();
            }
        }
    }

    private void verifyLoginState() {
        // This method can be called periodically to ensure login state is maintained
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        if (!isLoggedIn) {
            // User is not marked as logged in, redirect to login
            navigateToLogin();
        }
    }

    private void saveLoginState(String uid, String userType, String email) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_ID, uid);
        editor.putString(KEY_USER_TYPE, userType);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis());

        if (email != null) {
            editor.putString(KEY_USER_EMAIL, email);
        }

        // Use commit() instead of apply() for immediate persistence
        editor.commit();
    }

    private void checkUserStatusAndRedirect() {
        if (userId == null) {
            Log.w("Barangay_Dashboard", "userId is null, cannot check status");
            return;
        }

        Log.d("Barangay_Dashboard", "Checking user status for userId: " + userId);

        db.collection("Sagip")
                .document("users")
                .collection("barangay")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        Log.d("Barangay_Dashboard", "User status: " + status);
                        
                        if ("new".equals(status)) {
                            Log.d("Barangay_Dashboard", "User status is 'new', redirecting to registration");
                            // User status is "new", redirect to registration
                            Intent intent = new Intent(Barangay_Dashboard.this, Barangay_Registration.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Log.d("Barangay_Dashboard", "User status is not 'new', proceeding to dashboard");
                            // User is registered, proceed with dashboard initialization
                            // Data loading is handled by loadDataImmediately() in onCreate()
                            
        // Clear old notifications first to prevent showing old alerts
        BarangayNotificationService.getInstance(Barangay_Dashboard.this).clearOldNotifications();
        
        // Start listening for emergency notifications (only new ones from this session)
        BarangayNotificationService.getInstance(Barangay_Dashboard.this).startListening();
        
        // Start real-time popup listener for emergency notifications
        startEmergencyPopupListener();
        
        // Check and request notification permission
        checkAndRequestNotificationPermission();
        
        // Register for FCM notifications
        registerForFCMNotifications();
        
        // Register language change receiver
        registerLanguageChangeReceiver();
                        }
                    } else {
                        Log.w("Barangay_Dashboard", "User document does not exist, redirecting to registration");
                        // User document doesn't exist, redirect to registration
                        Intent intent = new Intent(Barangay_Dashboard.this, Barangay_Registration.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Barangay_Dashboard", "Error checking user status: " + e.getMessage(), e);
                    // On error, redirect to registration to be safe
                    Intent intent = new Intent(Barangay_Dashboard.this, Barangay_Registration.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(Barangay_Dashboard.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadUserData(String uid) {
        // Prevent duplicate loading if data is already loaded or loading in progress
        if (hospitalsLoaded || dataLoadingInProgress) {
            Log.d("Barangay_Dashboard", "Data already loaded or loading in progress, skipping loadUserData");
            return;
        }

        // Set loading flag
        dataLoadingInProgress = true;
        Log.d("Barangay_Dashboard", "Starting data loading for user: " + uid);

        // Load cached name immediately for instant display
        loadCachedBarangayName();

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
                                // Document exists, update login timestamp to keep session fresh
                                updateLoginTimestamp();

                                String rescueGroup = document.getString("barangayName");
                                if (rescueGroup != null) {
                                    brgyName.setText(rescueGroup);
                                    // Cache the name for future instant loading
                                    cacheBarangayName(rescueGroup);
                                } else {
                                    String firstName = document.getString("barangayName");
                                    if (firstName != null) {
                                        brgyName.setText(firstName);
                                        // Cache the name for future instant loading
                                        cacheBarangayName(firstName);
                                    } else {
                                        brgyName.setText(getString(R.string.rescue_group_not_available));
                                    }
                                }

                            // Check if there's stored location data
                            GeoPoint geoPoint = document.getGeoPoint("currentLocation");
                            if (geoPoint != null) {
                                currentLat = geoPoint.getLatitude();
                                currentLong = geoPoint.getLongitude();
                                updateLocationDisplay(currentLat, currentLong);
                            }

                            // Load senior count and nearby hospitals
                            loadSeniorCount();
                            loadNearbyHospitals();
                            } else {
                                Toast.makeText(Barangay_Dashboard.this,
                                        getString(R.string.user_document_not_exist),
                                        Toast.LENGTH_SHORT).show();

                                // Clear stored credentials and redirect to login
                                clearStoredCredentials();
                                navigateToLogin();
                            }
                        } else {
                            // Handle network errors gracefully - don't log out on temporary failures
                            Toast.makeText(Barangay_Dashboard.this,
                                    getString(R.string.unable_load_user_data),
                                    Toast.LENGTH_SHORT).show();

                            // Only log out if it's an authentication error
                            Exception exception = task.getException();
                            if (exception != null && exception.getMessage() != null &&
                                    exception.getMessage().toLowerCase().contains("permission")) {
                                clearStoredCredentials();
                                navigateToLogin();
                            }
                        }
                    }
                });
    }

    private void updateLoginTimestamp() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis());
        editor.commit();
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
                    // Handle failure silently for location updates
                    // Don't show toast for every location update failure
                });
    }


    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.barangay_dashboard);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.barangay_dashboard) {
                return true;
            } else if (itemId == R.id.barangay_profile) {
                startActivity(new Intent(getApplicationContext(), Barangay_Profile.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.barangay_seniorList) {
                startActivity(new Intent(getApplicationContext(), Barangay_List.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void setupHospitalRecyclerView() {
        hospitalList = new ArrayList<>();
        hospitalAdapter = new HospitalLIstAdapter(hospitalList, new HospitalLIstAdapter.OnHospitalLIstClickListener() {
            @Override
            public void onHospitalClick(HospitalLIst hospital) {
                Log.d("Barangay_Dashboard", "Hospital card clicked: " + hospital.getHospitalName());
                showHospitalInformation(hospital);
            }
        });
        
        hospitalRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        hospitalRecyclerView.setAdapter(hospitalAdapter);
    }

    // Method to handle logout - clears stored credentials and signs out from Firebase
    public void logoutUser() {
        // Clear stored credentials
        clearStoredCredentials();

        // Reset notification service to prevent cross-user notifications
        BarangayNotificationService.resetInstance();

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
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_EMAIL);
        editor.remove(KEY_LOGIN_TIMESTAMP);
        editor.remove(KEY_NOTIFICATION_TOAST_SHOWN); // Reset notification toast flag
        editor.commit(); // Use commit() for immediate persistence
    }
    private boolean isLoginExpired() {
        long loginTimestamp = sharedPreferences.getLong(KEY_LOGIN_TIMESTAMP, 0);
        long currentTime = System.currentTimeMillis();
        long EXPIRATION_TIME = 30L * 24 * 60 * 60 * 1000; // 30 days
        return (currentTime - loginTimestamp) > EXPIRATION_TIME;
    }

    private void loadCachedBarangayName() {
        String cachedName = sharedPreferences.getString(KEY_CACHED_BARANGAY_NAME, null);
        if (cachedName != null && !cachedName.isEmpty()) {
            brgyName.setText(cachedName);
            Log.d("Barangay_Dashboard", "Loaded cached barangay name: " + cachedName);
        } else {
            brgyName.setText("Loading...");
            Log.d("Barangay_Dashboard", "No cached barangay name found, showing loading...");
        }
    }

    private void loadDataImmediately() {
        // Get user ID from SharedPreferences or Firebase Auth
        String currentUserId = userId;
        if (currentUserId == null) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                currentUserId = currentUser.getUid();
            }
        }
        
        if (currentUserId != null) {
            Log.d("Barangay_Dashboard", "Loading data immediately for user: " + currentUserId);
            
            // Only load if data hasn't been loaded and no loading is in progress
            if (!hospitalsLoaded && !dataLoadingInProgress) {
                Log.d("Barangay_Dashboard", "Data not loaded yet, loading fresh data");
                // Load user data immediately (this will also load senior count and hospitals)
                loadUserData(currentUserId);
            } else {
                Log.d("Barangay_Dashboard", "Data already loaded or loading in progress, skipping data loading");
            }
        } else {
            Log.w("Barangay_Dashboard", "No user ID available for immediate data loading");
        }
    }

    private void cacheBarangayName(String barangayName) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_BARANGAY_NAME, barangayName)
                .apply();
        Log.d("Barangay_Dashboard", "Cached barangay name: " + barangayName);
    }

    private void resetDataLoadingFlags() {
        hospitalsLoaded = false;
        dataLoadingInProgress = false;
        Log.d("Barangay_Dashboard", "Reset data loading flags for fresh data load");
    }

    public void refreshData() {
        Log.d("Barangay_Dashboard", "Manual data refresh requested");
        resetDataLoadingFlags();
        loadDataImmediately();
    }

    private void loadSeniorCount() {
        // Get the current barangay name from cache or text
        String currentBarangay = brgyName.getText().toString();
        
        // If text shows "Loading..." or is empty, try to get from cache
        if (currentBarangay == null || currentBarangay.isEmpty() || "Loading...".equals(currentBarangay)) {
            currentBarangay = sharedPreferences.getString(KEY_CACHED_BARANGAY_NAME, null);
        }

        if (currentBarangay == null || currentBarangay.isEmpty()) {
            totalSeniorsCount.setText("0");
            Log.d("Barangay_Dashboard", "No barangay name available for senior count");
            return;
        }

        // Make the variable final for lambda usage
        final String finalBarangayName = currentBarangay;

        // Query seniors from the current barangay
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .whereEqualTo("barangay", finalBarangayName)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    totalSeniorsCount.setText(String.valueOf(count));
                    Log.d("Barangay_Dashboard", "Found " + count + " seniors in " + finalBarangayName);
                })
                .addOnFailureListener(e -> {
                    totalSeniorsCount.setText("0");
                    Log.e("Barangay_Dashboard", "Error loading senior count: " + e.getMessage());
                });
    }

    private void loadNearbyHospitals() {
        // Prevent duplicate loading
        if (hospitalsLoaded) {
            Log.d("Barangay_Dashboard", "Hospitals already loaded, skipping duplicate load");
            return;
        }

        Log.d("Barangay_Dashboard", "Loading hospitals from Firestore");

        // Fetch hospitals from Firestore - using the correct path Sagip/users/hospital/{UID}
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d("Barangay_Dashboard", "Successfully loaded hospitals from Sagip/users/hospital, count: " + queryDocumentSnapshots.size());
                    
                    hospitalList.clear();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Log.d("Barangay_Dashboard", "Processing hospital document: " + document.getId());
                        
                        // Debug: Log all fields in the document
                        Log.d("Barangay_Dashboard", "Document fields: " + document.getData().keySet());
                        Log.d("Barangay_Dashboard", "Document data: " + document.getData());
                        
                        HospitalLIst hospital = createHospitalLIstFromDocument(document);
                        if (hospital.getHospitalName() != null && !hospital.getHospitalName().isEmpty()) {
                            hospitalList.add(hospital);
                        } else {
                            Log.d("Barangay_Dashboard", "Skipping hospital with null/empty name");
                        }
                    }
                    
                    if (hospitalList.isEmpty()) {
                        Log.d("Barangay_Dashboard", "No hospitals found in Sagip/users/hospital collection");
                        hospitalsCount.setText("No hospitals found");
                    } else {
                        Log.d("Barangay_Dashboard", "Found " + hospitalList.size() + " hospitals in the app");
                        hospitalsCount.setText(hospitalList.size() + " found");
                    }
                    
                    // Update RecyclerView
                    Log.d("Barangay_Dashboard", "Notifying adapter of data change. Hospital list size: " + hospitalList.size());
                    hospitalAdapter.notifyDataSetChanged();
                    
                    // Mark hospitals as loaded to prevent duplicate loading
                    hospitalsLoaded = true;
                    dataLoadingInProgress = false;
                    Log.d("Barangay_Dashboard", "Data loading completed successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("Barangay_Dashboard", "Error loading hospitals from Sagip/users/hospital: " + e.getMessage(), e);
                    hospitalsCount.setText("Error loading");
                    dataLoadingInProgress = false;
                    Log.d("Barangay_Dashboard", "Data loading failed, resetting flags");
                });
    }

    private Hospital createHospitalFromDocument(QueryDocumentSnapshot document) {
        Hospital hospital = new Hospital();
        hospital.setDocumentId(document.getId());
        hospital.setHospitalName((String) document.get("hospitalName"));
        hospital.setContactNumber((String) document.get("contactNumber"));
        hospital.setEmail((String) document.get("email"));
        hospital.setAddress((String) document.get("address"));
        hospital.setStatus((String) document.get("status"));
        hospital.setUserType((String) document.get("userType"));
        hospital.setProfileImageUrl((String) document.get("profileImageUrl"));
        hospital.setEmergencyContact((String) document.get("emergencyContact"));
        hospital.setSpecialization((String) document.get("specialization"));
        
        // Handle bed capacity
        Object bedCapacityObj = document.get("bedCapacity");
        if (bedCapacityObj instanceof Number) {
            hospital.setBedCapacity(((Number) bedCapacityObj).intValue());
        }
        
        Object availableBedsObj = document.get("availableBeds");
        if (availableBedsObj instanceof Number) {
            hospital.setAvailableBeds(((Number) availableBedsObj).intValue());
        }
        
        // Handle emergency ready status
        Object emergencyReadyObj = document.get("isEmergencyReady");
        if (emergencyReadyObj instanceof Boolean) {
            hospital.setEmergencyReady((Boolean) emergencyReadyObj);
        }
        
        return hospital;
    }

    private HospitalLIst createHospitalLIstFromDocument(QueryDocumentSnapshot document) {
        HospitalLIst hospital = new HospitalLIst();
        
        // Set basic info
        hospital.setHospitalName(document.getString("hospitalName"));
        hospital.setHospitalAddress(document.getString("hospitalAddress"));
        
        // Set numeric fields
        if (document.getLong("totalBeds") != null) {
            hospital.setTotalBeds(document.getLong("totalBeds").intValue());
        }
        if (document.getLong("availableBeds") != null) {
            hospital.setAvailableBeds(document.getLong("availableBeds").intValue());
        }
        if (document.getLong("doctorsAvailable") != null) {
            hospital.setDoctorsAvailable(document.getLong("doctorsAvailable").intValue());
        }
        
        // Set status fields
        hospital.setErStatus(document.getString("erStatus"));
        if (document.getDouble("capacityPercentage") != null) {
            hospital.setCapacityPercentage(document.getDouble("capacityPercentage"));
        }
        
        // Set timestamp
        if (document.getTimestamp("lastUpdated") != null) {
            hospital.setLastUpdated(document.getTimestamp("lastUpdated").toString());
        }
        
        
        // Debug logging
        Log.d("Barangay_Dashboard", "Created HospitalLIst: " + hospital.getHospitalName() + 
            ", Address: " + hospital.getHospitalAddress() + 
            ", Beds: " + hospital.getAvailableBeds() + "/" + hospital.getTotalBeds() + 
            ", Doctors: " + hospital.getDoctorsAvailable() + 
            ", Status: " + hospital.getErStatus());
        
        return hospital;
    }





    private void showHospitalInformation(HospitalLIst hospital) {
        Log.d("Barangay_Dashboard", "Showing hospital information for: " + hospital.getHospitalName());
        
        // Create a dialog to show hospital information
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(hospital.getHospitalName());
        
        // Build hospital information message
        StringBuilder hospitalInfo = new StringBuilder();
        hospitalInfo.append("🏥 ").append(hospital.getHospitalName()).append("\n\n");
        
        // Add available beds
        hospitalInfo.append("🛏️ ").append(getString(R.string.hospital_info_available_beds, hospital.getAvailableBeds())).append("\n\n");
        
        // Add doctors available
        if (hospital.getDoctorsAvailable() != null) {
            hospitalInfo.append("⚕️ ").append(getString(R.string.hospital_info_doctors_available, hospital.getDoctorsAvailable())).append("\n\n");
        }
        
        // Add emergency status
        String erStatus = hospital.getErStatus();
        if (erStatus != null && !erStatus.isEmpty()) {
            hospitalInfo.append("🚨 ").append(getString(R.string.hospital_info_emergency_status, erStatus));
        } else {
            hospitalInfo.append("🚨 ").append(getString(R.string.hospital_info_emergency_status, hospital.getCalculatedStatus()));
        }
        
        builder.setMessage(hospitalInfo.toString());
        
        // Add buttons
        builder.setPositiveButton(getString(R.string.hospital_info_close), (dialog, which) -> {
            dialog.dismiss();
        });
        
        // Show the dialog
        builder.create().show();
    }

    
    
    // Notification permission request methods
    private void checkAndRequestNotificationPermission() {
        Log.d("Barangay_Dashboard", "🔔 Checking notification permissions");
        
        // Check if notifications are enabled
        if (!areNotificationsEnabled()) {
            Log.d("Barangay_Dashboard", "🔔 Notifications are disabled, requesting permission");
            showNotificationPermissionDialog();
        } else {
            Log.d("Barangay_Dashboard", "🔔 Notifications are enabled");
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
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_enable_notifications))
                .setMessage(getString(R.string.dialog_enable_notifications_barangay))
                .setPositiveButton(getString(R.string.button_enable_notifications), (dialog, which) -> {
                    openNotificationSettings();
                })
                .setNegativeButton(getString(R.string.button_later), (dialog, which) -> {
                    Log.d("Barangay_Dashboard", "🔔 User chose to enable notifications later");
                    // Show a reminder toast
                    Toast.makeText(this, getString(R.string.toast_notifications_later), Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }
    
    private void openNotificationSettings() {
        Log.d("Barangay_Dashboard", "🔔 Opening notification settings");
        
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
            Log.e("Barangay_Dashboard", "Error opening notification settings", e);
            Toast.makeText(this, getString(R.string.toast_could_not_open_settings), Toast.LENGTH_LONG).show();
        }
    }
    
    // FCM Token Registration
    private void registerForFCMNotifications() {
        Log.d("Barangay_Dashboard", "🔔 Registering for FCM notifications");
        
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("Barangay_Dashboard", "❌ Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d("Barangay_Dashboard", "🔔 FCM Token: " + token);

                    // Save token to Firestore
                    saveFCMTokenToDatabase(token);
                });
    }
    
    private void saveFCMTokenToDatabase(String fcmToken) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w("Barangay_Dashboard", "❌ No authenticated user for FCM token");
            return;
        }
        
        String userId = currentUser.getUid();
        Log.d("Barangay_Dashboard", "💾 Saving FCM token for user: " + userId);
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("fcmToken", fcmToken);
        tokenData.put("lastUpdated", System.currentTimeMillis());
        
        db.collection("Sagip")
                .document("users")
                .collection("barangay")
                .document(userId)
                .set(tokenData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d("Barangay_Dashboard", "✅ FCM token saved successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("Barangay_Dashboard", "❌ Failed to save FCM token", e);
                });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Dismiss any active emergency dialog
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            currentEmergencyDialog = null;
            Log.d("Barangay_Dashboard", "Dismissed emergency dialog on destroy");
        }
        
        // Clear shown notification IDs to prevent memory leaks
        shownNotificationIds.clear();
        
        // Stop location updates
        stopLocationUpdates();
        
        // Stop listening for emergency notifications when activity is destroyed
        BarangayNotificationService.getInstance(this).stopListening();
        
        // Reset notification service to prevent cross-user notifications
        BarangayNotificationService.resetInstance();
        
        // Unregister language change receiver
        unregisterLanguageChangeReceiver();
    }
    
    private void updateUILanguage() {
        // Update UI elements with new language
        // The cached barangay name will be reloaded by loadCachedBarangayName()
    }
    
    private void registerLanguageChangeReceiver() {
        android.content.IntentFilter filter = new android.content.IntentFilter("com.example.sagip_prototype.LANGUAGE_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(languageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(languageChangeReceiver, filter);
        }
    }
    
    private void unregisterLanguageChangeReceiver() {
        try {
            unregisterReceiver(languageChangeReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
            Log.d("Barangay_Dashboard", "Language change receiver was not registered");
        }
    }
    
    /**
     * Handle emergency notification intents from notifications
     */
    private void handleEmergencyNotificationIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            String notificationId = intent.getStringExtra("notification_id");
            String seniorName = intent.getStringExtra("senior_name");
            String seniorPhone = intent.getStringExtra("senior_phone");
            String locationAddress = intent.getStringExtra("location_address");
            String barangay = intent.getStringExtra("barangay");
            String requestId = intent.getStringExtra("request_id");
            String emergencyType = intent.getStringExtra("emergency_type");
            
            // Get senior coordinates for navigation
            Double seniorLatitude = null;
            Double seniorLongitude = null;
            if (intent.hasExtra("senior_latitude")) {
                seniorLatitude = intent.getDoubleExtra("senior_latitude", 0.0);
            }
            if (intent.hasExtra("senior_longitude")) {
                seniorLongitude = intent.getDoubleExtra("senior_longitude", 0.0);
            }
            
            // Get currentLocation field
            String currentLocation = intent.getStringExtra("current_location");
            
            if (notificationId != null && seniorName != null) {
                Log.d("Barangay_Dashboard", "🚨 Received emergency notification - Senior: " + seniorName);
                Log.d("Barangay_Dashboard", "🚨 Senior coordinates - Lat: " + seniorLatitude + ", Long: " + seniorLongitude);
                Log.d("Barangay_Dashboard", "🚨 Current location: " + currentLocation);
                
                // Show emergency alert dialog
                showEmergencyAlert(seniorName, seniorPhone, locationAddress, barangay, requestId, emergencyType, seniorLatitude, seniorLongitude, currentLocation);
                
                // Mark notification as read to prevent repeated showing
                markNotificationAsRead(notificationId);
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("notification_id");
                intent.removeExtra("senior_name");
                intent.removeExtra("senior_phone");
                intent.removeExtra("location_address");
                intent.removeExtra("barangay");
                intent.removeExtra("request_id");
                intent.removeExtra("emergency_type");
                intent.removeExtra("senior_latitude");
                intent.removeExtra("senior_longitude");
            }
        }
    }
    
    /**
     * Show emergency alert dialog similar to rescuer dashboard
     */
    private void showEmergencyAlert(String seniorName, String seniorPhone, String locationAddress, 
                                  String barangay, String requestId, String emergencyType, 
                                  Double seniorLatitude, Double seniorLongitude, String currentLocation) {
        // Enhanced activity state check
        if (isFinishing() || isDestroyed()) {
            Log.w("Barangay_Dashboard", "Cannot show emergency alert dialog - activity is not in valid state (finishing: " + isFinishing() + ", destroyed: " + isDestroyed() + ")");
            return;
        }
        
        // Dismiss any existing emergency dialog
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            currentEmergencyDialog = null;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 EMERGENCY ALERT - " + barangay);
        
        String message = "🚨 URGENT: Senior needs immediate assistance!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not provided") + "\n" +
                        "📍 Location: " + (locationAddress != null ? locationAddress : "Not provided") + "\n" +
                        (currentLocation != null && !currentLocation.isEmpty() ? 
                            "🏠 Full Address: " + currentLocation + "\n" : "") +
                        "🏘️ Barangay: " + barangay + "\n" +
                        "🚨 Type: " + (emergencyType != null ? emergencyType : "Emergency") + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        
        // Call Senior button
        builder.setPositiveButton("📞 CALL SENIOR", (dialog, which) -> {
            if (seniorPhone != null && !seniorPhone.isEmpty()) {
                try {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL);
                    callIntent.setData(Uri.parse("tel:" + seniorPhone));
                    startActivity(callIntent);
                    Log.d("Barangay_Dashboard", "📞 Opening dialer for: " + seniorPhone);
                } catch (Exception e) {
                    Log.e("Barangay_Dashboard", "❌ Error opening dialer: " + e.getMessage());
                    Toast.makeText(this, getString(R.string.unable_to_open_dialer, seniorPhone), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.senior_phone_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        
        // Navigate button
        builder.setNeutralButton("🗺️ NAVIGATE", (dialog, which) -> {
            if (seniorLatitude != null && seniorLongitude != null) {
                // Use coordinates for more accurate navigation
                String addressForFallback = (currentLocation != null && !currentLocation.isEmpty()) ? currentLocation : locationAddress;
                openGoogleMapsNavigationWithCoordinates(seniorLatitude, seniorLongitude, addressForFallback);
            } else if (currentLocation != null && !currentLocation.isEmpty()) {
                // Use currentLocation if available
                openGoogleMapsNavigation(currentLocation);
            } else if (locationAddress != null && !locationAddress.isEmpty()) {
                // Fallback to locationAddress
                openGoogleMapsNavigation(locationAddress);
            } else {
                Toast.makeText(this, getString(R.string.location_info_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        
        // View Details button
        builder.setNegativeButton("👁️ VIEW DETAILS", (dialog, which) -> {
            // Navigate to senior list or emergency details
            Intent detailsIntent = new Intent(this, Barangay_List.class);
            startActivity(detailsIntent);
        });
        
        AlertDialog dialog = builder.create();
        
        // Style the buttons
        dialog.setOnShowListener(dialogInterface -> {
            try {
                // Make the positive button red to indicate emergency
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
            } catch (Exception e) {
                Log.e("Barangay_Dashboard", "Error styling dialog buttons", e);
            }
        });
        
        // Add dismiss listener to clean up when dialog is dismissed
        dialog.setOnDismissListener(dialogInterface -> {
            Log.d("Barangay_Dashboard", "🚨 Emergency alert dialog dismissed");
            currentEmergencyDialog = null;
        });
        
        dialog.show();
        
        // Store reference to current emergency dialog
        currentEmergencyDialog = dialog;
        
        Log.d("Barangay_Dashboard", "🚨 Emergency alert dialog shown for: " + seniorName);
    }
    
    /**
     * Open Google Maps navigation to emergency location using coordinates
     */
    private void openGoogleMapsNavigationWithCoordinates(Double latitude, Double longitude, String locationAddress) {
        try {
            Log.d("Barangay_Dashboard", "🗺️ Opening navigation with coordinates: " + latitude + ", " + longitude);
            
            // First try to open Google Maps app with coordinates
            String navigationUri = String.format("google.navigation:q=%.6f,%.6f&mode=d", latitude, longitude);
            Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
            navIntent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps app is available
            if (navIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navIntent);
                Toast.makeText(this, "🗺️ Opening Google Maps navigation to coordinates", Toast.LENGTH_SHORT).show();
            } else {
                // Fallback to web-based Google Maps navigation with coordinates
                String webUrl = String.format("https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving", latitude, longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                startActivity(webIntent);
                Toast.makeText(this, "🗺️ Opening web navigation to coordinates", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("Barangay_Dashboard", "Error opening navigation with coordinates: " + e.getMessage());
            // Fallback to address-based navigation
            if (locationAddress != null && !locationAddress.isEmpty()) {
                openGoogleMapsNavigation(locationAddress);
            } else {
                Toast.makeText(this, "Unable to open navigation. Please contact emergency services.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Open Google Maps navigation to emergency location
     */
    private void openGoogleMapsNavigation(String locationAddress) {
        try {
            // First try to open Google Maps app with navigation
            String navigationUri = "google.navigation:q=" + Uri.encode(locationAddress) + "&mode=d";
            Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
            navIntent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps app is available
            if (navIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navIntent);
                Toast.makeText(this, "🗺️ Opening Google Maps navigation", Toast.LENGTH_SHORT).show();
            } else {
                // Fallback to web-based Google Maps navigation
                String webUrl = "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(locationAddress) + "&travelmode=driving";
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                startActivity(webIntent);
                Toast.makeText(this, "🗺️ Opening web navigation", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("Barangay_Dashboard", "Error opening navigation: " + e.getMessage());
            Toast.makeText(this, "Unable to open navigation. Please contact emergency services.", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Mark notification as read in the database
     */
    private void markNotificationAsRead(String notificationId) {
        if (mAuth.getCurrentUser() == null) {
            Log.w("Barangay_Dashboard", "No authenticated user, cannot mark notification as read");
            return;
        }
        
        if (notificationId == null || notificationId.isEmpty()) {
            Log.w("Barangay_Dashboard", "Invalid notification ID, cannot mark as read");
            return;
        }
        
        String userId = mAuth.getCurrentUser().getUid();
        String notificationPath = "Sagip/users/barangay/" + userId + "/notifications/" + notificationId;
        
        // First check if the document exists before trying to update it
        db.document(notificationPath)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Document exists, proceed with update
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("isRead", true);
                        updates.put("readTimestamp", System.currentTimeMillis());
                        
                        db.document(notificationPath)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d("Barangay_Dashboard", "✅ Notification marked as read: " + notificationId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Barangay_Dashboard", "❌ Failed to mark notification as read: " + notificationId, e);
                                });
                    } else {
                        Log.w("Barangay_Dashboard", "⚠️ Notification document does not exist: " + notificationId + " (path: " + notificationPath + ")");
                        // Don't treat this as an error since the notification might have been processed already
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Barangay_Dashboard", "❌ Failed to check notification existence: " + notificationId, e);
                });
    }
    
    /**
     * Start real-time popup listener for emergency notifications
     */
    private void startEmergencyPopupListener() {
        if (userId == null) {
            Log.w("Barangay_Dashboard", "Cannot start emergency popup listener - userId is null");
            return;
        }
        
        Log.d("Barangay_Dashboard", "🚨 Starting emergency popup listener for barangay: " + userId);
        
        // Listen for emergency notifications in real-time (only from current session)
        long sessionStartTime = System.currentTimeMillis();
        Log.d("Barangay_Dashboard", "🚨 Starting emergency popup listener with session start time: " + sessionStartTime);
        
        db.collection("Sagip")
          .document("users")
          .collection("barangay")
          .document(userId)
          .collection("notifications")
          .whereGreaterThan("timestamp", sessionStartTime)
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(1)
          .addSnapshotListener((querySnapshot, error) -> {
              if (error != null) {
                  Log.e("Barangay_Dashboard", "Error listening to emergency notifications: " + error.getMessage(), error);
                  return;
              }
              
              if (querySnapshot != null && !querySnapshot.isEmpty()) {
                  for (QueryDocumentSnapshot document : querySnapshot) {
                      handleEmergencyPopupNotification(document);
                  }
              }
          });
    }
    
    /**
     * Handle emergency popup notification
     */
    private void handleEmergencyPopupNotification(QueryDocumentSnapshot document) {
        try {
            String type = document.getString("type");
            String title = document.getString("title");
            String message = document.getString("message");
            String seniorName = document.getString("seniorName");
            String seniorPhone = document.getString("seniorPhone");
            String locationAddress = document.getString("locationAddress");
            String barangay = document.getString("barangay");
            String requestId = document.getString("requestId");
            String emergencyType = document.getString("emergencyType");
            Long timestamp = document.getLong("timestamp");
            Boolean isRead = document.getBoolean("isRead");
            
            // Get senior coordinates for navigation
            Double seniorLatitude = null;
            Double seniorLongitude = null;
            if (document.getDouble("seniorLatitude") != null) {
                seniorLatitude = document.getDouble("seniorLatitude");
            }
            if (document.getDouble("seniorLongitude") != null) {
                seniorLongitude = document.getDouble("seniorLongitude");
            }
            
            // Get currentLocation field with proper error handling
            String currentLocation = null;
            try {
                com.google.firebase.firestore.GeoPoint currentLocationGeoPoint = document.getGeoPoint("currentLocation");
                if (currentLocationGeoPoint != null) {
                    currentLocation = currentLocationGeoPoint.getLatitude() + ", " + currentLocationGeoPoint.getLongitude();
                }
            } catch (Exception e) {
                Log.w("Barangay_Dashboard", "currentLocation field is not a GeoPoint, trying as String: " + e.getMessage());
                // Fallback: try to get as String
                try {
                    currentLocation = document.getString("currentLocation");
                } catch (Exception e2) {
                    Log.w("Barangay_Dashboard", "currentLocation field is neither GeoPoint nor String: " + e2.getMessage());
                    currentLocation = null;
                }
            }
            
            // Only process unread emergency notifications that haven't been shown yet
            String notificationId = document.getId();
            if ("EMERGENCY_ALERT".equals(type) && (isRead == null || !isRead) && !shownNotificationIds.contains(notificationId)) {
                Log.d("Barangay_Dashboard", "🚨 Received emergency popup notification: " + seniorName + " (Request ID: " + requestId + ")");
                Log.d("Barangay_Dashboard", "🚨 Senior coordinates - Lat: " + seniorLatitude + ", Long: " + seniorLongitude);
                Log.d("Barangay_Dashboard", "🚨 Current location: " + currentLocation);
                
                // Mark this notification as shown to prevent duplicates
                shownNotificationIds.add(notificationId);
                
                // Show emergency alert dialog
                showEmergencyPopupAlert(seniorName, seniorPhone, locationAddress, barangay, requestId, emergencyType, timestamp, seniorLatitude, seniorLongitude, currentLocation);
                
                // Mark notification as read
                document.getReference().update("isRead", true);
            }
            
        } catch (Exception e) {
            Log.e("Barangay_Dashboard", "Error handling emergency popup notification: " + e.getMessage());
        }
    }
    
    /**
     * Show emergency popup alert dialog
     */
    private void showEmergencyPopupAlert(String seniorName, String seniorPhone, String locationAddress,
                                       String barangay, String requestId, String emergencyType, Long timestamp,
                                       Double seniorLatitude, Double seniorLongitude, String currentLocation) {
        // Enhanced activity state check
        if (isFinishing() || isDestroyed()) {
            Log.w("Barangay_Dashboard", "Cannot show emergency popup dialog - activity is not in valid state (finishing: " + isFinishing() + ", destroyed: " + isDestroyed() + ")");
            return;
        }
        
        // Dismiss any existing emergency dialog
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            currentEmergencyDialog = null;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 EMERGENCY ALERT - " + barangay);
        
        String timeStr = "Unknown time";
        if (timestamp != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            timeStr = sdf.format(new java.util.Date(timestamp));
        }
        
        String message = "🚨 URGENT: Senior needs immediate assistance!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not provided") + "\n" +
                        "📍 Location: " + (locationAddress != null ? locationAddress : "Not provided") + "\n" +
                        (currentLocation != null && !currentLocation.isEmpty() ? 
                            "🏠 Full Address: " + currentLocation + "\n" : "") +
                        "🏘️ Barangay: " + barangay + "\n" +
                        "⏰ Time: " + timeStr + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        
        // Call Senior button
        builder.setPositiveButton("📞 CALL SENIOR", (dialog, which) -> {
            if (seniorPhone != null && !seniorPhone.isEmpty()) {
                try {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL);
                    callIntent.setData(Uri.parse("tel:" + seniorPhone));
                    startActivity(callIntent);
                    Log.d("Barangay_Dashboard", "📞 Opening dialer for: " + seniorPhone);
                } catch (Exception e) {
                    Log.e("Barangay_Dashboard", "❌ Error opening dialer: " + e.getMessage());
                    Toast.makeText(this, getString(R.string.unable_to_open_dialer, seniorPhone), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.senior_phone_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        
        // Navigate button
        builder.setNeutralButton("🗺️ NAVIGATE", (dialog, which) -> {
            if (seniorLatitude != null && seniorLongitude != null) {
                // Use coordinates for more accurate navigation
                String addressForFallback = (currentLocation != null && !currentLocation.isEmpty()) ? currentLocation : locationAddress;
                openGoogleMapsNavigationWithCoordinates(seniorLatitude, seniorLongitude, addressForFallback);
            } else if (currentLocation != null && !currentLocation.isEmpty()) {
                // Use currentLocation if available
                openGoogleMapsNavigation(currentLocation);
            } else if (locationAddress != null && !locationAddress.isEmpty()) {
                // Fallback to locationAddress
                openGoogleMapsNavigation(locationAddress);
            } else {
                Toast.makeText(this, getString(R.string.location_info_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        
        // View Details button
        builder.setNegativeButton("👁️ VIEW DETAILS", (dialog, which) -> {
            // Navigate to senior list or emergency details
            Intent detailsIntent = new Intent(this, Barangay_List.class);
            startActivity(detailsIntent);
        });
        
        AlertDialog dialog = builder.create();
        
        // Style the buttons
        dialog.setOnShowListener(dialogInterface -> {
            try {
                // Make the positive button red to indicate emergency
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(16);
            } catch (Exception e) {
                 
            }
        });
        
        // Add dismiss listener to clean up when dialog is dismissed
        dialog.setOnDismissListener(dialogInterface -> {
            Log.d("Barangay_Dashboard", "🚨 Emergency popup dialog dismissed");
            currentEmergencyDialog = null;
        });
        
        dialog.show();
        
        // Store reference to current emergency dialog
        currentEmergencyDialog = dialog;
        
        Log.d("Barangay_Dashboard", "🚨 Emergency popup dialog shown for: " + seniorName);
    }

}