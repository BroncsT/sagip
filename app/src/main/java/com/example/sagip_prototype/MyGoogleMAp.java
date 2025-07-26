package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MyGoogleMAp extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MyGoogleMAp";
    private GoogleMap myMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesActive = false;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    // Variables to store location data from Senior_Dashboard
    private double receivedLat = 0.0;
    private double receivedLong = 0.0;
    private String receivedAddress = "";
    private boolean isEmergencyMode = false;

    // Variables for rescuer mode
    private boolean isRescuerMode = false;
    private String seniorName = "";
    private String seniorPhone = "";
    private String helpRequestId = "";
    private String emergencyDescription = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_google_map);

        // Get location data from Intent (from Senior_Dashboard or Rescuer)
        Intent intent = getIntent();
        if (intent != null) {
            receivedLat = intent.getDoubleExtra("latitude", 0.0);
            receivedLong = intent.getDoubleExtra("longitude", 0.0);
            receivedAddress = intent.getStringExtra("locationAddress");
            isEmergencyMode = intent.getBooleanExtra("isEmergency", false);

            // Additional data for rescuer mode
            isRescuerMode = intent.getBooleanExtra("isRescuerMode", false);
            seniorName = intent.getStringExtra("seniorName");
            seniorPhone = intent.getStringExtra("seniorPhone");
            helpRequestId = intent.getStringExtra("helpRequestId");
            emergencyDescription = intent.getStringExtra("emergencyDescription");

            Log.d(TAG, "Received location: " + receivedLat + ", " + receivedLong);
            Log.d(TAG, "Emergency mode: " + isEmergencyMode);
            Log.d(TAG, "Rescuer mode: " + isRescuerMode);
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Register permission launcher
        registerLocationPermissionLauncher();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Log.d(TAG, "onMapReady called");
        myMap = googleMap;

        if (myMap == null) {
            Log.e(TAG, "GoogleMap is null in onMapReady!");
            Toast.makeText(this, "Error initializing map", Toast.LENGTH_LONG).show();
            return;
        }

        // Always set up location callback first
        setupLocationCallback();

        Log.d(TAG, "Checking received location: " + receivedLat + ", " + receivedLong);

        // Check if location data was passed from Senior_Dashboard or Rescuer
        if (receivedLat != 0.0 && receivedLong != 0.0) {
            Log.d(TAG, "Displaying received location");
            // Display the received location immediately
            displayReceivedLocation();
        } else {
            Log.d(TAG, "No received location, starting normal mode");
            // Enable location layer if permission is granted (normal mode)
            enableMyLocation();

            // Request location permissions for normal mode
            requestLocationPermissions();
        }
    }

    private void registerLocationPermissionLauncher() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (fineLocationGranted != null && fineLocationGranted) {
                        enableMyLocation();
                        startLocationUpdates();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        enableMyLocation();
                        startLocationUpdates();
                    } else {
                        Toast.makeText(this, "Location permission needed to show current location", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void requestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            enableMyLocation();
            startLocationUpdates();
        }
    }

    private void enableMyLocation() {
        if (myMap == null) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            myMap.setMyLocationEnabled(true);
            myMap.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateMapLocation(location);
                }
            }
        };
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
    }

    private void updateMapLocation(Location location) {
        if (myMap != null && location != null) {
            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

            // Clear previous markers (optional - remove this line if you want to keep all markers)
            myMap.clear();

            // Add a marker at current location
            myMap.addMarker(new MarkerOptions()
                    .position(currentLatLng)
                    .title("My Current Location"));

            // Move camera to current location with zoom level 15
            myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));

            Log.d(TAG, "Map updated with location: " + location.getLatitude() + ", " + location.getLongitude());
        }
    }

    // Enhanced method to display location for both senior and rescuer modes
    private void displayReceivedLocation() {
        if (myMap != null && receivedLat != 0.0 && receivedLong != 0.0) {
            LatLng emergencyLocation = new LatLng(receivedLat, receivedLong);

            // Clear any existing markers
            myMap.clear();

            String markerTitle;
            String markerSnippet;

            if (isRescuerMode) {
                // Rescuer viewing senior's emergency location
                markerTitle = "🆘 " + (seniorName != null ? seniorName : "Senior") + " NEEDS HELP";
                markerSnippet = buildRescuerSnippet();
            } else if (isEmergencyMode) {
                // Senior viewing their own emergency location
                markerTitle = "🚨 EMERGENCY LOCATION 🚨";
                markerSnippet = receivedAddress != null && !receivedAddress.isEmpty() ?
                        receivedAddress : "Emergency Help Needed";
            } else {
                // Regular location display
                markerTitle = "Current Location";
                markerSnippet = receivedAddress != null ? receivedAddress : "";
            }

            // Add marker with appropriate styling
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(emergencyLocation)
                    .title(markerTitle)
                    .snippet(markerSnippet);

            myMap.addMarker(markerOptions);

            // Move camera to location with appropriate zoom level
            float zoomLevel = isRescuerMode || isEmergencyMode ? 18f : 15f;
            myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, zoomLevel));

            // Enable additional UI elements for emergency situations
            if (isEmergencyMode || isRescuerMode) {
                myMap.getUiSettings().setZoomControlsEnabled(true);
                myMap.getUiSettings().setCompassEnabled(true);
                myMap.getUiSettings().setMapToolbarEnabled(true);
                myMap.getUiSettings().setAllGesturesEnabled(true);
            }

            Log.d(TAG, "Location displayed: " + receivedLat + ", " + receivedLong);

            String toastMessage = isRescuerMode ?
                    "Senior's emergency location displayed" :
                    "Emergency location displayed on map";
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        }
    }

    private String buildRescuerSnippet() {
        StringBuilder snippet = new StringBuilder();

        if (receivedAddress != null && !receivedAddress.isEmpty()) {
            snippet.append("📍 ").append(receivedAddress).append("\n");
        }

        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            snippet.append("📞 ").append(seniorPhone).append("\n");
        }

        if (emergencyDescription != null && !emergencyDescription.isEmpty()) {
            snippet.append("ℹ️ ").append(emergencyDescription);
        } else {
            snippet.append("ℹ️ Senior needs immediate assistance");
        }

        return snippet.toString();
    }

    private void stopLocationUpdates() {
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Only start location updates if we're not in emergency/rescuer mode and don't have received location
        if (receivedLat == 0.0 && receivedLong == 0.0 && !locationUpdatesActive) {
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
}