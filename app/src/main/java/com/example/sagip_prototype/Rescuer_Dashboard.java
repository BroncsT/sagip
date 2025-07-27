package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_dashboard);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

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
        createLocationRequest();
        createLocationCallback();

        // Setup bottom navigation
        setupBottomNavigation();

        // Check for location permissions
        checkLocationPermission();

        // Check authentication state
        checkAuthState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

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
                    Log.d(TAG, "Location updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update location: " + e.getMessage());
                });
    }

    private void navigateToNearestHospital() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, "Current location not available. Please wait or check permissions.",
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
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar);
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

    // Method to handle logout - clears stored credentials and signs out from Firebase
    public void logoutUser() {
        Log.d(TAG, "Logging out user...");
        // Clear stored credentials
        clearStoredCredentials();

        // Sign out from Firebase
        mAuth.signOut();

        // Navigate to login screen
        navigateToLogin();
    }

    // Helper method to clear stored credentials
    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials...");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        editor.apply();
    }
}