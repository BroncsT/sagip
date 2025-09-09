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

import java.io.IOException;
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
    
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    TextView tvFullName, tvCurrentLocation;
    Button btnFindHospital, btnHelp;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesActive = false;
    private double currentLat = 0.0;
    private double currentLong = 0.0;
    private String currentLocationAddress = "";

    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private boolean helpRequestInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_senior_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Check authentication state with persistence
        checkAuthStateWithPersistence();

        initializeViews();
        initializeLocationServices();
        registerLocationPermissionLauncher();
        loadUserData();
        setupBottomNavigation();
        requestLocationPermissions();
    }

    private void initializeViews() {
        tvFullName = findViewById(R.id.seniorName);
        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        btnFindHospital = findViewById(R.id.findhospital);
        btnHelp = findViewById(R.id.sosButton);

        btnFindHospital.setOnClickListener(v -> navigateToNearestHospital());
        btnHelp.setOnClickListener(v -> showHelpConfirmationDialog());
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

    private void sendHelpRequest() {
        Log.d(TAG, "Help button pressed - Creating help request");

        // Prevent multiple help requests
        if (helpRequestInProgress) {
            Toast.makeText(this, "Help request already in progress. Please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, "Current location not available. Please wait or check location permissions.", Toast.LENGTH_LONG).show();
            return;
        }

        helpRequestInProgress = true;
        Toast.makeText(this, "Creating help request...", Toast.LENGTH_SHORT).show();

        // Get current user info first
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");
                        String seniorName = (firstName != null && lastName != null) ?
                                firstName + " " + lastName : "Senior User";
                        String phoneNumber = documentSnapshot.getString("phoneNumber");

                        // Create help request (this will also open the map)
                        createHelpRequest(seniorName, phoneNumber, uid);
                    } else {
                        createHelpRequest("Senior User", "", uid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user info", e);
                    createHelpRequest("Senior User", "", uid);
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
            Toast.makeText(this, "Error opening map", Toast.LENGTH_SHORT).show();
        }
    }

    // New method to open Senior_GoogleMap in tracking mode
    private void openMyGoogleMapWithTracking(String helpRequestId) {
        try {
            Intent mapIntent = new Intent(Senior_Dashboard.this, Senior_GoogleMap.class);

            // Pass current location data to Senior_GoogleMap
            mapIntent.putExtra("latitude", currentLat);
            mapIntent.putExtra("longitude", currentLong);
            mapIntent.putExtra("locationAddress", currentLocationAddress);
            mapIntent.putExtra("helpRequestIdForTracking", helpRequestId);
            mapIntent.putExtra("seniorName", tvFullName.getText().toString());

            startActivity(mapIntent);
            Log.d(TAG, "Opened Senior_GoogleMap in tracking mode with help request ID: " + helpRequestId);

        } catch (Exception e) {
            Log.e(TAG, "Error opening Senior_GoogleMap in tracking mode", e);
            Toast.makeText(this, "Error opening map", Toast.LENGTH_SHORT).show();
        }
    }

    private void createHelpRequest(String seniorName, String phoneNumber, String seniorUid) {
        Map<String, Object> helpRequest = new HashMap<>();
        helpRequest.put("seniorUid", seniorUid);
        helpRequest.put("seniorName", seniorName);
        helpRequest.put("seniorPhone", phoneNumber != null ? phoneNumber : "");
        helpRequest.put("latitude", currentLat);
        helpRequest.put("longitude", currentLong);
        helpRequest.put("locationAddress", currentLocationAddress);
        helpRequest.put("timestamp", System.currentTimeMillis());
        helpRequest.put("status", "active");
        helpRequest.put("type", "emergency_help");
        helpRequest.put("description", "Senior needs immediate assistance");

        // Add to help requests collection
        db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .add(helpRequest)
                .addOnSuccessListener(documentReference -> {
                    String requestId = documentReference.getId();
                    Log.d(TAG, "Help request created: " + requestId);

                    // Notify all rescuers
                    notifyAllRescuers(helpRequest, requestId);

                                         // Open map in tracking mode with the help request ID
                     openMyGoogleMapWithTracking(requestId);

                    Toast.makeText(this, "Help request sent to rescuers!", Toast.LENGTH_LONG).show();
                    helpRequestInProgress = false;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating help request", e);
                    Toast.makeText(this, "Failed to create help request. Please try again.", Toast.LENGTH_LONG).show();
                    helpRequestInProgress = false;
                });
    }

    // New method to notify all rescuers
    private void notifyAllRescuers(Map<String, Object> helpRequest, String requestId) {
        // Create a simple notification document that rescuers will listen to
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "emergency_help");
        notification.put("title", "🚨 Emergency Help Request");
        notification.put("message", helpRequest.get("seniorName") + " needs help!");
        notification.put("helpRequestId", requestId);
        notification.put("seniorUid", helpRequest.get("seniorUid"));
        notification.put("seniorName", helpRequest.get("seniorName"));
        notification.put("seniorPhone", helpRequest.get("seniorPhone"));
        notification.put("latitude", helpRequest.get("latitude"));
        notification.put("longitude", helpRequest.get("longitude"));
        notification.put("locationAddress", helpRequest.get("locationAddress"));
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("isActive", true);

        // Add to global emergency notifications that rescuers will listen to
        db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .document(requestId) // Use same ID as help request
                .set(notification)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Emergency notification sent to all rescuers");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send emergency notification", e);
                });
    }

    private void showHelpConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 Emergency Help Request");
        builder.setMessage("Are you sure you need help?\n\nThis will:\n• Alert all nearby rescuers immediately\n• Send your location to them\n• Open your location on the map\n\nOnly use this if you really need help!");

        builder.setIcon(android.R.drawable.ic_dialog_alert);

        // Positive button - Confirm help request
        builder.setPositiveButton("YES, I NEED HELP", (dialog, which) -> {
            dialog.dismiss();
            sendHelpRequest();
        });

        // Negative button - Cancel
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(Senior_Dashboard.this, "Help request cancelled", Toast.LENGTH_SHORT).show();
        });

        // Make dialog non-cancelable by back button or outside touch for safety
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();

        // Style the buttons for better visibility
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
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("🚨 YES, I NEED HELP");
            } catch (Exception e) {
                Log.e(TAG, "Error styling dialog buttons", e);
            }
        });

        dialog.show();
    }

    private void navigateToNearestHospital() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, "Current location not available. Please wait or check permissions.", Toast.LENGTH_SHORT).show();
            return;
        }

        String source = currentLat + "," + currentLong;
        String destination = "hospital";

        Uri uri = Uri.parse("https://www.google.com/maps/dir/" + source + "/" + destination);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
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
                        Toast.makeText(this, "Location permission needed for location services", Toast.LENGTH_SHORT).show();
                        tvCurrentLocation.setText("Location permission denied");
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
                    currentLat = location.getLatitude();
                    currentLong = location.getLongitude();
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(10000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(5000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        locationUpdatesActive = true;
        tvCurrentLocation.setText("Fetching current location...");
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
                currentLocationAddress = "Location found but address unknown";
                tvCurrentLocation.setText(currentLocationAddress);
            }
        } catch (IOException e) {
            currentLocationAddress = "Unable to get address from location";
            tvCurrentLocation.setText(currentLocationAddress);
            Log.e(TAG, "Error getting address from location", e);
        }
    }

    private void saveLocationToDatabase(Location location) {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

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
        String userType = "seniors";
        
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
                                "Your account is not yet approved. Please wait for administrator approval.", 
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
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
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
                                "User profile not found. Please login again.", 
                                Toast.LENGTH_LONG).show();
                            navigateToLogin();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Alternative search failed", e);
                        mAuth.signOut();
                        clearStoredCredentials();
                        Toast.makeText(Senior_Dashboard.this, 
                            "Error finding user profile. Please login again.", 
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
        String userType = "seniors";

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
                        String currentLocation = documentSnapshot.getString("currentLocation");

                        if (documentSnapshot.getDouble("latitude") != null && documentSnapshot.getDouble("longitude") != null) {
                            currentLat = documentSnapshot.getDouble("latitude");
                            currentLong = documentSnapshot.getDouble("longitude");
                        }

                        if (firstName != null && middleName != null && lastName != null) {
                            String fullName = firstName + " " + middleName + " " + lastName;
                            tvFullName.setText(fullName);
                        } else {
                            tvFullName.setText("Full Name Not Available");
                        }

                        if (currentLocation != null && !currentLocation.isEmpty()) {
                            currentLocationAddress = currentLocation;
                            tvCurrentLocation.setText(currentLocation);
                        } else {
                            tvCurrentLocation.setText("Waiting for location update...");
                        }
                    } else {
                        tvFullName.setText("User data not found.");
                        Log.d(TAG, "Document doesn't exist");
                    }
                })
                .addOnFailureListener(e -> {
                    tvFullName.setText("Failed to load data.");
                    Log.e(TAG, "Error fetching user data", e);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup resources
        if (locationUpdatesActive) {
            stopLocationUpdates();
        }
    }
}