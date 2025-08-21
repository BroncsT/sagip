package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Hospital_Dashboard extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvCurrentLocation;
    private TextView tvHospitalName;
    private TextView tvTotalBeds, tvAvailableBeds, tvDoctorsAvailable;
    private TextView tvErStatus;
    private Button btnEditStatus;
    private String userType = "hospital";
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
        setContentView(R.layout.activity_hospital_dashboard);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvHospitalName = findViewById(R.id.hospitalStaffName);
        tvTotalBeds = findViewById(R.id.tvTotalBeds);
        tvAvailableBeds = findViewById(R.id.tvAvailableBeds);
        tvDoctorsAvailable = findViewById(R.id.tvDoctorsAvailable);
        tvErStatus = findViewById(R.id.tvErStatus);
        btnEditStatus = findViewById(R.id.btnEditStatus);

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
        
        // Setup click listeners
        setupClickListeners();
        
        // Load hospital status
        loadHospitalStatus();
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
        // First check if we have stored user credentials
        userId = sharedPreferences.getString(KEY_USER_ID, null);
        String storedUserType = sharedPreferences.getString(KEY_USER_TYPE, null);

        if (userId != null && storedUserType != null) {
            // We have stored credentials, update userType if needed
            this.userType = storedUserType;
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
            }
        }
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
                Toast.makeText(this, "Location permission denied. Unable to update location.",
                        Toast.LENGTH_LONG).show();
                tvCurrentLocation.setText("Location: Permission denied");
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
            tvCurrentLocation.setText("Location: " + locationText);
        } else {
            // Fallback to coordinates if address can't be determined
            tvCurrentLocation.setText(String.format(Locale.getDefault(),
                    "Location: %.6f, %.6f", latitude, longitude));
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
                    Toast.makeText(Hospital_Dashboard.this,
                            "Failed to update location: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
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
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        // Load hospital name
                        String hospitalName = documentSnapshot.getString("hospitalName");
                        if (hospitalName != null && tvHospitalName != null) {
                            tvHospitalName.setText(hospitalName);
                        }

                        // Load status information
                        Long totalBeds = documentSnapshot.getLong("totalBeds");
                        Long availableBeds = documentSnapshot.getLong("availableBeds");
                        Long doctorsAvailable = documentSnapshot.getLong("doctorsAvailable");
                        String erStatus = documentSnapshot.getString("erStatus");

                        if (totalBeds != null && tvTotalBeds != null) {
                            tvTotalBeds.setText(String.valueOf(totalBeds));
                        }
                        if (availableBeds != null && tvAvailableBeds != null) {
                            tvAvailableBeds.setText(String.valueOf(availableBeds));
                        }
                        if (doctorsAvailable != null && tvDoctorsAvailable != null) {
                            tvDoctorsAvailable.setText(String.valueOf(doctorsAvailable));
                        }

                                                 // Calculate and set automatic status
                         if (totalBeds != null && availableBeds != null && doctorsAvailable != null && tvErStatus != null) {
                             String autoStatus = calculateAutoStatus(totalBeds.intValue(), availableBeds.intValue(), doctorsAvailable.intValue());
                             if (!autoStatus.equals("unknown")) {
                                 String statusText = getStatusEmoji(autoStatus) + " " + autoStatus.toUpperCase();
                                 tvErStatus.setText(statusText);
                                 tvErStatus.setTextColor(getStatusColor(autoStatus));
                             } else {
                                 tvErStatus.setText("⚪ Status not available");
                                 tvErStatus.setTextColor(getStatusColor("unknown"));
                             }
                         }
                    }
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

    private String calculateAutoStatus(int totalBeds, int availableBeds, int doctors) {
        // Validate input
        if (totalBeds <= 0 || availableBeds < 0 || doctors <= 0) {
            return "unknown";
        }
        
        if (availableBeds > totalBeds) {
            return "unknown";
        }
        
        // Calculate capacity percentage
        double capacityPercentage = ((double) (totalBeds - availableBeds) / totalBeds) * 100;
        
        // Calculate beds per doctor ratio
        double bedsPerDoctor = (double) totalBeds / doctors;
        
        // Automatic status logic based on multiple factors
        if (capacityPercentage >= 90 || availableBeds == 0) {
            return "crowded"; // At or near capacity
        } else if (capacityPercentage >= 70 || bedsPerDoctor > 8 || doctors < 2) {
            return "busy"; // High capacity or insufficient staff
        } else if (capacityPercentage >= 50 || bedsPerDoctor > 6) {
            return "busy"; // Moderate capacity
        } else {
            return "available"; // Good capacity and staff ratio
        }
    }
}