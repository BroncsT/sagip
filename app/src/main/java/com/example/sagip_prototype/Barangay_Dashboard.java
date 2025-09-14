package com.example.sagip_prototype;

import android.Manifest;
import android.app.NotificationManager;
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
    private Button navigateToHospitalButton;
    private TextView totalSeniorsCount;
    private TextView hospitalsCount;
    private LinearLayout hospitalListContainer;
    private String userType = "barangay";
    private String userId;
    private SharedPreferences sharedPreferences;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private double currentLat = 0.0;
    private double currentLong = 0.0;

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
        hospitalListContainer = findViewById(R.id.hospitalListContainer);

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
        createLocationRequest();
        createLocationCallback();

        // Setup bottom navigation
        setupBottomNavigation();

        // Check for location permissions
        checkLocationPermission();

        // Check authentication state with improved persistence
        checkAuthStateWithPersistence();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Load cached barangay name immediately when returning to dashboard
        loadCachedBarangayName();
        
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
        
        // Reload cached barangay name to ensure it's still displayed
        loadCachedBarangayName();
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
                            loadUserData(userId);
                            
        // Clear old notifications first to prevent showing old alerts
        BarangayNotificationService.getInstance(Barangay_Dashboard.this).clearOldNotifications();
        
        // Start listening for emergency notifications
        BarangayNotificationService.getInstance(Barangay_Dashboard.this).startListening();
        
        // Check and request notification permission
        checkAndRequestNotificationPermission();
        
        // Register for FCM notifications
        registerForFCMNotifications();
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

    private void navigateToNearestHospital() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.current_location_not_available),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Format coordinates for Google Maps
        String source = currentLat + "," + currentLong;
        // Use "hospital" as destination to find nearest hospitals
        String destination = "hospital";

        // Create Google Maps intent
        Uri uri = Uri.parse("https://www.google.com/maps/dir/" + source + "/" + destination);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Check if Google Maps is installed
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Google Maps app is not installed, open in browser instead
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
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

    // Method to handle logout - clears stored credentials and signs out from Firebase
    public void logoutUser() {
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

    private void cacheBarangayName(String barangayName) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_BARANGAY_NAME, barangayName)
                .apply();
        Log.d("Barangay_Dashboard", "Cached barangay name: " + barangayName);
    }

    private void loadSeniorCount() {
        // Get the current barangay name
        String currentBarangay = brgyName.getText().toString();
        if (currentBarangay == null || currentBarangay.isEmpty() || "Loading...".equals(currentBarangay)) {
            totalSeniorsCount.setText("0");
            return;
        }

        // Query seniors from the current barangay
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .whereEqualTo("barangay", currentBarangay)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    totalSeniorsCount.setText(String.valueOf(count));
                    Log.d("Barangay_Dashboard", "Found " + count + " seniors in " + currentBarangay);
                })
                .addOnFailureListener(e -> {
                    totalSeniorsCount.setText("0");
                    Log.e("Barangay_Dashboard", "Error loading senior count: " + e.getMessage());
                });
    }

    private void loadNearbyHospitals() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            hospitalsCount.setText("Location needed");
            return;
        }

        // Clear previous hospital list
        hospitalListContainer.removeAllViews();

        Log.d("Barangay_Dashboard", "Loading hospitals from Firestore");

        // Fetch hospitals from Firestore - using the correct path Sagip/users/hospital/{UID}
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d("Barangay_Dashboard", "Successfully loaded hospitals from Sagip/users/hospital, count: " + queryDocumentSnapshots.size());
                    
                    List<Hospital> hospitals = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Log.d("Barangay_Dashboard", "Processing hospital document: " + document.getId());
                        Hospital hospital = createHospitalFromDocument(document);
                        hospitals.add(hospital);
                    }
                    
                    if (hospitals.isEmpty()) {
                        Log.d("Barangay_Dashboard", "No hospitals found in Sagip/users/hospital collection");
                        hospitalsCount.setText("No hospitals found");
                        addNoHospitalsMessage();
                    } else {
                        Log.d("Barangay_Dashboard", "Found " + hospitals.size() + " hospitals in the app");
                        hospitalsCount.setText(hospitals.size() + " found");
                        
                        // Add each hospital to the list
                        for (Hospital hospital : hospitals) {
                            addHospitalToList(hospital);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Barangay_Dashboard", "Error loading hospitals from Sagip/users/hospital: " + e.getMessage(), e);
                    hospitalsCount.setText("Error loading");
                    addNoHospitalsMessage();
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

    private void addNoHospitalsMessage() {
        // Create a no hospitals message
        LinearLayout noHospitalsItem = new LinearLayout(this);
        noHospitalsItem.setOrientation(LinearLayout.VERTICAL);
        noHospitalsItem.setPadding(16, 24, 16, 24);
        noHospitalsItem.setGravity(android.view.Gravity.CENTER);

        TextView noHospitalsText = new TextView(this);
        noHospitalsText.setText("No hospitals available");
        noHospitalsText.setTextSize(16);
        noHospitalsText.setTextColor(getResources().getColor(R.color.gray));
        noHospitalsText.setGravity(android.view.Gravity.CENTER);

        noHospitalsItem.addView(noHospitalsText);
        hospitalListContainer.addView(noHospitalsItem);
    }

    private void addHospitalToList(Hospital hospital) {
        // Create a hospital item card
        androidx.cardview.widget.CardView hospitalCard = new androidx.cardview.widget.CardView(this);
        hospitalCard.setCardElevation(4);
        hospitalCard.setRadius(12);
        hospitalCard.setCardBackgroundColor(getResources().getColor(R.color.white));
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        hospitalCard.setLayoutParams(cardParams);

        // Create a hospital item view
        LinearLayout hospitalItem = new LinearLayout(this);
        hospitalItem.setOrientation(LinearLayout.HORIZONTAL);
        hospitalItem.setPadding(16, 16, 16, 16);
        hospitalItem.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Hospital icon
        ImageView hospitalIcon = new ImageView(this);
        hospitalIcon.setImageResource(R.drawable.ic_hospital);
        hospitalIcon.setLayoutParams(new LinearLayout.LayoutParams(40, 40));
        hospitalIcon.setPadding(8, 8, 8, 8);
        hospitalIcon.setBackgroundResource(R.drawable.circle_background);
        hospitalIcon.setColorFilter(getResources().getColor(R.color.primaryRed));

        // Hospital name and details
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        textContainer.setPadding(16, 0, 0, 0);

        TextView hospitalText = new TextView(this);
        hospitalText.setText(hospital.getHospitalName() != null ? hospital.getHospitalName() : "Unknown Hospital");
        hospitalText.setTextSize(16);
        hospitalText.setTextColor(getResources().getColor(R.color.black));
        hospitalText.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView hospitalSubtext = new TextView(this);
        String status = hospital.getStatusDisplay();
        String bedInfo = hospital.getBedStatus();
        hospitalSubtext.setText(status + " • " + bedInfo);
        hospitalSubtext.setTextSize(12);
        
        // Color code the status
        if (status.equals("Open")) {
            hospitalSubtext.setTextColor(getResources().getColor(R.color.success_green));
        } else if (status.equals("Busy")) {
            hospitalSubtext.setTextColor(getResources().getColor(R.color.emergency_red));
        } else {
            hospitalSubtext.setTextColor(getResources().getColor(R.color.gray));
        }
        hospitalSubtext.setPadding(0, 4, 0, 0);

        textContainer.addView(hospitalText);
        textContainer.addView(hospitalSubtext);

        // Navigation arrow
        ImageView arrowIcon = new ImageView(this);
        arrowIcon.setImageResource(R.drawable.ic_arrow_forward);
        arrowIcon.setLayoutParams(new LinearLayout.LayoutParams(24, 24));
        arrowIcon.setColorFilter(getResources().getColor(R.color.gray));

        // Add views to hospital item
        hospitalItem.addView(hospitalIcon);
        hospitalItem.addView(textContainer);
        hospitalItem.addView(arrowIcon);

        // Add click listener to navigate to hospital
        hospitalItem.setOnClickListener(v -> {
            navigateToSpecificHospital(hospital.getHospitalName());
        });

        // Add hospital item to card
        hospitalCard.addView(hospitalItem);

        // Add hospital card to container
        hospitalListContainer.addView(hospitalCard);
    }

    private void navigateToSpecificHospital(String hospitalName) {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.toast_current_location_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        // Format coordinates for Google Maps
        String source = currentLat + "," + currentLong;
        String destination = hospitalName + ", Angeles City, Philippines";

        // Create Google Maps intent
        Uri uri = Uri.parse("https://www.google.com/maps/dir/" + source + "/" + destination);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Check if Google Maps is installed
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Google Maps app is not installed, open in browser instead
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
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
        // Stop listening for emergency notifications when activity is destroyed
        BarangayNotificationService.getInstance(this).stopListening();
    }
}