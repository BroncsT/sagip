package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.provider.Settings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.content.Context;
import android.os.Build;

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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.maps.model.Marker;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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

    // Variables for senior tracking mode (senior viewing rescuers)
    private boolean isSeniorTrackingMode = false;
    private String helpRequestIdForTracking = "";
    private ListenerRegistration rescuerLocationListener = null;
    private Map<String, Marker> rescuerMarkers = new HashMap<>();

    // Routing variables
    private LatLng currentLocation = null;
    private LatLng destinationLocation = null;
    private Polyline currentRoute = null;
    private List<LatLng> routePoints = new ArrayList<>();
    private boolean routeDisplayed = false;

    // UI Elements
    private LinearLayout emergencyInfoCard;
    private TextView tvEmergencyTitle;
    private TextView tvEmergencyAddress;
    private TextView tvDistanceTime; // New TextView for distance and time
    private Button btnNavigate;
    private Button btnCallSenior;
    private Button btnShowRoute;
    private ImageButton btnBack;

    // Distance and time estimation
    private String estimatedDistance = "";
    private String estimatedTime = "";
    private boolean isCalculatingRoute = false;
    private ExecutorService executorService;
    private boolean isDestroyed = false;
    
    // Firebase Firestore for tracking rescuers
    private FirebaseFirestore db;
    
    // Google Directions API constants
    private static final String DIRECTIONS_API_KEY = "AIzaSyBkf_blEJ4wc5Q_CNxABKK6-LFxDF-gWv0";
    private static final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";
    
    // Notification constants
    private static final String CHANNEL_ID = "SAGIPP_EMERGENCY_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_NAME = "Emergency Alerts";
    private static final String CHANNEL_DESCRIPTION = "Notifications for emergency responses";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_google_map);

        // Initialize Firebase Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        initializeUI();

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

            // Additional data for senior tracking mode
            isSeniorTrackingMode = intent.getBooleanExtra("isSeniorTrackingMode", false);
            helpRequestIdForTracking = intent.getStringExtra("helpRequestIdForTracking");

            Log.d(TAG, "Received location: " + receivedLat + ", " + receivedLong);
            Log.d(TAG, "Emergency mode: " + isEmergencyMode);
            Log.d(TAG, "Rescuer mode: " + isRescuerMode);
            Log.d(TAG, "Senior tracking mode: " + isSeniorTrackingMode);
            Log.d(TAG, "Help request ID for tracking: " + helpRequestIdForTracking);
        }

        // Set destination location for routing
        if (receivedLat != 0.0 && receivedLong != 0.0) {
            destinationLocation = new LatLng(receivedLat, receivedLong);
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Ensure fusedLocationClient is properly initialized
        if (fusedLocationClient == null) {
            Log.e(TAG, "Failed to initialize fusedLocationClient");
            Toast.makeText(this, "Error initializing location services", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "fusedLocationClient initialized successfully");
        }

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();

        // Create notification channel for emergency alerts
        createNotificationChannel();

        // Check and request notification permissions for Android 13+
        checkNotificationPermissions();

        // Register permission launcher
        registerLocationPermissionLauncher();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void initializeUI() {
        Log.d(TAG, "initializeUI called");

        emergencyInfoCard = findViewById(R.id.emergencyInfoCard);
        tvEmergencyTitle = findViewById(R.id.tvEmergencyTitle);
        tvEmergencyAddress = findViewById(R.id.tvEmergencyAddress);
        tvDistanceTime = findViewById(R.id.tvDistanceTime); // New TextView
        btnNavigate = findViewById(R.id.btnNavigate);
        btnCallSenior = findViewById(R.id.btnCallSenior);
        btnShowRoute = findViewById(R.id.btnShowRoute);
        btnBack = findViewById(R.id.btnBack);

        Log.d(TAG, "UI Elements found - emergencyInfoCard: " + (emergencyInfoCard != null) +
                ", btnNavigate: " + (btnNavigate != null) +
                ", tvDistanceTime: " + (tvDistanceTime != null));

        if (btnNavigate != null) {
            btnNavigate.setOnClickListener(v -> {
                Log.d(TAG, "Navigate button clicked!");
                startInternalNavigation();
            });
            Log.d(TAG, "Navigate button click listener set");
        } else {
            Log.e(TAG, "btnNavigate is null in initializeUI!");
        }

        if (btnCallSenior != null) {
            btnCallSenior.setOnClickListener(v -> callSenior());
        }

        if (btnShowRoute != null) {
            btnShowRoute.setOnClickListener(v -> toggleRouteDisplay());
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Show emergency info card for rescuer mode
        if (isRescuerMode && emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
            updateEmergencyInfo();
            Log.d(TAG, "Emergency info card made visible for rescuer mode");
            
            // Auto-show route if both locations are available
            if (currentLocation != null && destinationLocation != null) {
                // Delay slightly to ensure map is fully loaded
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!routeDisplayed) {
                        showRoute();
                    }
                }, 1000);
            }
        } else {
            Log.d(TAG, "isRescuerMode: " + isRescuerMode + ", emergencyInfoCard: " + (emergencyInfoCard != null));
        }
    }

    private void updateEmergencyInfo() {
        Log.d(TAG, "updateEmergencyInfo called - currentLocation: " + (currentLocation != null) + 
              ", destinationLocation: " + (destinationLocation != null));
        
        if (tvEmergencyTitle != null) {
            String title = getString(R.string.senior_needs_help, seniorName != null ? seniorName : "Senior");
            tvEmergencyTitle.setText(title);
        }

        if (tvEmergencyAddress != null && receivedAddress != null) {
            tvEmergencyAddress.setText("📍 " + receivedAddress);
        }
        
        // If we have distance/time data, update it
        if (tvDistanceTime != null && !estimatedDistance.isEmpty() && !estimatedTime.isEmpty()) {
            String displayText = "📍 " + estimatedDistance + " • ⏱️ " + estimatedTime;
            tvDistanceTime.setText(displayText);
            Log.d(TAG, "Distance/time updated: " + displayText);
        } else if (tvDistanceTime != null) {
            if (currentLocation == null) {
                tvDistanceTime.setText("📍 Getting your location...");
                Log.d(TAG, "Showing 'Getting your location...' message");
            } else if (destinationLocation == null) {
                tvDistanceTime.setText("📍 Getting destination...");
                Log.d(TAG, "Showing 'Getting destination...' message");
            } else {
                tvDistanceTime.setText("📍 Calculating distance...");
                Log.d(TAG, "Showing 'Calculating distance...' message");
            }
        }
        
        // Make sure the emergency info card is visible
        if (emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
            Log.d(TAG, "Emergency info card made visible");
        }
    }

    private void startInternalNavigation() {
        Log.d(TAG, "startInternalNavigation called");

        if (destinationLocation == null) {
            Log.e(TAG, "Destination location is null");
            Toast.makeText(this, "Destination not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting internal navigation to: " + destinationLocation.latitude + ", " + destinationLocation.longitude);

        // Show route on the map
        showRoute();

        // Calculate simple distance and time
        calculateSimpleDistanceAndTime();

        // Center camera on destination with zoom
        myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLocation, 18f));

        // Show navigation mode message
        Toast.makeText(this, "🗺️ Navigation started - Follow the blue route line", Toast.LENGTH_LONG).show();

        // Update button text to indicate navigation mode
        if (btnNavigate != null) {
            btnNavigate.setText("📍 Stop Navigation");
            btnNavigate.setOnClickListener(v -> stopInternalNavigation());
            Log.d(TAG, "Navigation button updated to Stop Navigation");
        } else {
            Log.e(TAG, "btnNavigate is null!");
        }
    }

    private void stopInternalNavigation() {
        // Clear the route
        clearRoute();

        // Reset button
        if (btnNavigate != null) {
            btnNavigate.setText(getString(R.string.btn_navigate));
            btnNavigate.setOnClickListener(v -> startInternalNavigation());
        }

        // Clear distance and time
        if (tvDistanceTime != null) {
            tvDistanceTime.setText("");
        }

        Toast.makeText(this, "Navigation stopped", Toast.LENGTH_SHORT).show();
    }

    // Calculate simple distance and time (no API required)
    private void calculateSimpleDistanceAndTime() {
        Log.d(TAG, "calculateSimpleDistanceAndTime called");
        Log.d(TAG, "currentLocation: " + (currentLocation != null) + 
              ", destinationLocation: " + (destinationLocation != null));

        if (currentLocation == null || destinationLocation == null) {
            Log.d(TAG, "Cannot calculate distance - missing location data");
            return;
        }

        // Calculate straight-line distance
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                destinationLocation.latitude, destinationLocation.longitude,
                results
        );

        float distanceInMeters = results[0];
        float distanceInKm = distanceInMeters / 1000;

        // Estimate time (assuming 30 km/h average speed)
        float estimatedTimeInMinutes = (distanceInKm / 30) * 60;

        // Format distance
        if (distanceInKm < 1) {
            estimatedDistance = String.format("%.0f m", distanceInMeters);
        } else {
            estimatedDistance = String.format("%.1f km", distanceInKm);
        }

        // Format time
        if (estimatedTimeInMinutes < 1) {
            estimatedTime = "Less than 1 min";
        } else if (estimatedTimeInMinutes < 60) {
            estimatedTime = String.format("%.0f min", estimatedTimeInMinutes);
        } else {
            float hours = estimatedTimeInMinutes / 60;
            estimatedTime = String.format("%.1f hours", hours);
        }

        Log.d(TAG, "Distance calculated: " + estimatedDistance + ", Time: " + estimatedTime);

        // Update the display
        updateDistanceTimeDisplay();
    }

    // Calculate distance and time using Google Directions API (modern implementation)
    private void calculateDistanceAndTime() {
        Log.d(TAG, "calculateDistanceAndTime called");
        Log.d(TAG, "currentLocation: " + (currentLocation != null) +
                ", destinationLocation: " + (destinationLocation != null) +
                ", isCalculatingRoute: " + isCalculatingRoute);

        if (currentLocation == null || destinationLocation == null || isCalculatingRoute) {
            Log.d(TAG, "Skipping distance calculation - conditions not met");
            return;
        }

        Log.d(TAG, "Starting route calculation...");
        isCalculatingRoute = true;

        // Use ExecutorService instead of deprecated AsyncTask
        executorService.execute(() -> {
            try {
                // This is where you would implement Google Directions API call
                // For now, we'll just simulate some work
                Thread.sleep(100); // Simulate network delay

                // Run UI updates on main thread
                runOnUiThread(() -> {
                    isCalculatingRoute = false;
                    // Fallback to simple calculation
                    calculateStraightLineDistance();
                });
            } catch (InterruptedException e) {
                runOnUiThread(() -> {
                    isCalculatingRoute = false;
                    Log.e(TAG, "Route calculation interrupted", e);
                });
            }
        });
    }

    // Fallback method for straight line distance (single implementation)
    private void calculateStraightLineDistance() {
        if (currentLocation != null && destinationLocation != null) {
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    currentLocation.latitude, currentLocation.longitude,
                    destinationLocation.latitude, destinationLocation.longitude,
                    results
            );

            estimatedDistance = String.format("%.1f km", results[0] / 1000);
            estimatedTime = "~" + String.format("%.0f min", results[0] / 1000 * 2); // Rough estimate

            updateDistanceTimeDisplay();
        }
    }

    // Update the distance and time display
    private void updateDistanceTimeDisplay() {
        Log.d(TAG, "updateDistanceTimeDisplay called");
        Log.d(TAG, "tvDistanceTime: " + (tvDistanceTime != null) +
                ", estimatedDistance: '" + estimatedDistance + "'" +
                ", estimatedTime: '" + estimatedTime + "'");

        if (tvDistanceTime != null && !estimatedDistance.isEmpty() && !estimatedTime.isEmpty()) {
            String displayText = "📍 " + estimatedDistance + " • ⏱️ " + estimatedTime;
            tvDistanceTime.setText(displayText);
            Log.d(TAG, "Distance and time display updated: " + displayText);
            
            // Make sure the emergency info card is visible if we have distance/time data
            if (isRescuerMode && emergencyInfoCard != null) {
                emergencyInfoCard.setVisibility(View.VISIBLE);
            }
        } else {
            Log.d(TAG, "Cannot update distance/time display - missing data");
            if (tvDistanceTime != null) {
                if (currentLocation == null) {
                    tvDistanceTime.setText("📍 Getting your location...");
                } else if (destinationLocation == null) {
                    tvDistanceTime.setText("📍 Getting destination...");
                } else {
                    tvDistanceTime.setText("📍 Calculating distance...");
                }
            }
        }
    }

    // Simple route update (no API required)
    private void updateRouteWithDirections() {
        // For now, just use the simple straight line route
        // This can be enhanced later with actual road routing
        Log.d(TAG, "Using simple straight line route");
    }

    private void openExternalNavigation() {
        if (destinationLocation != null) {
            // Open Google Maps with navigation
            String uri = "google.navigation:q=" + destinationLocation.latitude + "," + destinationLocation.longitude;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage("com.google.android.apps.maps");

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Fallback to web browser
                String webUri = "https://www.google.com/maps/dir/?api=1&destination=" +
                        destinationLocation.latitude + "," + destinationLocation.longitude;
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUri));
                startActivity(webIntent);
            }
        } else {
            Toast.makeText(this, getString(R.string.destination_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void callSenior() {
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            startActivity(callIntent);
        } else {
            Toast.makeText(this, getString(R.string.phone_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleRouteDisplay() {
        Log.d(TAG, "toggleRouteDisplay called - currentLocation: " + (currentLocation != null) + 
              ", destinationLocation: " + (destinationLocation != null));
        
        if (currentLocation == null) {
            Toast.makeText(this, "Getting your location... Please wait a moment.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Cannot toggle route - current location not available");
            
            // Try to get location again
            if (fusedLocationClient != null && 
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                 ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                getLastKnownLocation();
                startLocationUpdates();
            } else {
                Log.d(TAG, "Cannot start location updates - fusedLocationClient is null or no permissions");
            }
            return;
        }
        
        if (destinationLocation == null) {
            Toast.makeText(this, "Emergency location not available", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Cannot toggle route - destination location not available");
            return;
        }

        if (routeDisplayed) {
            clearRoute();
        } else {
            showRoute();
        }
    }

    private void showRoute() {
        Log.d(TAG, "showRoute called - currentLocation: " + (currentLocation != null) + 
              ", destinationLocation: " + (destinationLocation != null));
        
        // Check if activity is destroyed
        if (isDestroyed) {
            Log.d(TAG, "Activity is destroyed, skipping route display");
            return;
        }
        
        if (currentLocation == null || destinationLocation == null) {
            Toast.makeText(this, getString(R.string.location_data_not_available), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Cannot show route - missing location data");
            return;
        }

        // Check if already calculating route to avoid multiple toasts
        if (isCalculatingRoute) {
            Log.d(TAG, "Route calculation already in progress, skipping");
            return;
        }

        // Clear existing route
        clearRoute();

        // Show loading message only if not already calculating
        if (!isCalculatingRoute) {
            Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show();
        }

        // Get actual road route using Google Directions API
        getDirectionsRoute(currentLocation, destinationLocation);
    }

    private void clearRoute() {
        Log.d(TAG, "clearRoute called");
        
        if (currentRoute != null) {
            currentRoute.remove();
            currentRoute = null;
            Log.d(TAG, "Route polyline removed");
        }
        
        routePoints.clear();
        routeDisplayed = false;
        
        // Reset calculating flag when clearing route
        isCalculatingRoute = false;
        
        if (btnShowRoute != null) {
            btnShowRoute.setText(getString(R.string.btn_show_route));
        }
        
        Log.d(TAG, "Route cleared successfully");
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
            // Only proceed if fusedLocationClient is initialized
            if (fusedLocationClient != null) {
                enableMyLocation();
                startLocationUpdates();
                
                // Try to get last known location immediately
                getLastKnownLocation();
            } else {
                Log.d(TAG, "fusedLocationClient is null, cannot start location services");
            }
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
                Log.d(TAG, "Location callback triggered with " + locationResult.getLocations().size() + " locations");
                
                // Check if activity is destroyed before processing location updates
                if (isDestroyed) {
                    Log.d(TAG, "Activity is destroyed, ignoring location updates");
                    return;
                }
                
                for (Location location : locationResult.getLocations()) {
                    Log.d(TAG, "Location update received: " + location.getLatitude() + ", " + location.getLongitude());
                    
                    // Update current location
                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    updateMapLocation(location);
                    
                    // If we have both current and destination locations, calculate distance
                    if (destinationLocation != null) {
                        Log.d(TAG, "Both locations available, calculating distance");
                        calculateSimpleDistanceAndTime();
                        
                        // Auto-show route in rescuer mode if not already displayed
                        if (isRescuerMode && !routeDisplayed && myMap != null && !isDestroyed) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                if (!routeDisplayed && !isDestroyed) {
                                    Log.d(TAG, "Auto-showing route in rescuer mode");
                                    showRoute();
                                }
                            }, 500);
                        }
                    } else {
                        Log.d(TAG, "Destination location not available yet");
                    }
                }
            }
        };
    }

    private void startLocationUpdates() {
        Log.d(TAG, "startLocationUpdates called");
        
        // Ensure locationCallback is initialized
        if (locationCallback == null) {
            Log.d(TAG, "LocationCallback is null, setting it up");
            setupLocationCallback();
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No location permissions granted");
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(10000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(5000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        locationUpdatesActive = true;
        Log.d(TAG, "Location updates started successfully");
    }

    private void updateMapLocation(Location location) {
        // Check if activity is destroyed before updating map
        if (isDestroyed) {
            Log.d(TAG, "Activity is destroyed, skipping map location update");
            return;
        }
        
        if (myMap != null && location != null) {
            currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

            // Clear previous markers
            myMap.clear();

            // Add destination marker back if in rescuer mode
            if (isRescuerMode && destinationLocation != null) {
                MarkerOptions destinationMarker = new MarkerOptions()
                        .position(destinationLocation)
                        .title(getString(R.string.emergency_location_title))
                        .snippet(receivedAddress);
                myMap.addMarker(destinationMarker);
            }

            // Add current location marker
            MarkerOptions currentMarker = new MarkerOptions()
                    .position(currentLocation)
                    .title(getString(R.string.your_location))
                    .snippet(getString(R.string.rescuer_position))
                    .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                            com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN));
            myMap.addMarker(currentMarker);

            // Calculate distance and time when we have both locations
            if (destinationLocation != null) {
                calculateSimpleDistanceAndTime();
            }

            // If route is displayed, update it
            if (routeDisplayed && !isDestroyed) {
                showRoute();
            }

            // If in navigation mode, keep camera centered on current location
            if (btnNavigate != null && btnNavigate.getText().toString().contains("Stop")) {
                myMap.animateCamera(CameraUpdateFactory.newLatLng(currentLocation));
            } else {
                // Normal mode - fit both locations
                if (isRescuerMode && destinationLocation != null) {
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    builder.include(currentLocation);
                    builder.include(destinationLocation);
                    LatLngBounds bounds = builder.build();
                    myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                } else {
                    myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                }
            }

            Log.d(TAG, "Map updated with location: " + location.getLatitude() + ", " + location.getLongitude());
        }
    }

    // Enhanced method to display location for both senior and rescuer modes
    private void displayReceivedLocation() {
        Log.d(TAG, "displayReceivedLocation called - receivedLat: " + receivedLat + ", receivedLong: " + receivedLong);
        
        if (myMap != null && receivedLat != 0.0 && receivedLong != 0.0) {
            LatLng emergencyLocation = new LatLng(receivedLat, receivedLong);

            // Set destination location for rescuer mode
            if (isRescuerMode) {
                destinationLocation = emergencyLocation;
                Log.d(TAG, "Destination location set for rescuer mode: " + destinationLocation);
            }

            // Clear any existing markers
            myMap.clear();

            String markerTitle;
            String markerSnippet;

            if (isRescuerMode) {
                // Rescuer viewing senior's emergency location
                markerTitle = "🚨 " + (seniorName != null ? seniorName : "Senior") + " NEEDS HELP";
                markerSnippet = buildRescuerSnippet();

                // Add destination marker
                MarkerOptions destinationMarker = new MarkerOptions()
                        .position(emergencyLocation)
                        .title(markerTitle)
                        .snippet(markerSnippet);
                myMap.addMarker(destinationMarker);

                // If we have current location, add it as well
                if (currentLocation != null) {
                    Log.d(TAG, "Current location available, adding marker");
                    MarkerOptions currentMarker = new MarkerOptions()
                            .position(currentLocation)
                            .title(getString(R.string.your_location))
                            .snippet(getString(R.string.rescuer_position))
                            .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN));
                    myMap.addMarker(currentMarker);

                    // Calculate distance and time when both locations are available
                    calculateSimpleDistanceAndTime();

                    // Fit camera to show both locations
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    builder.include(currentLocation);
                    builder.include(emergencyLocation);
                    LatLngBounds bounds = builder.build();
                    myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                } else {
                    Log.d(TAG, "Current location not available yet, showing only destination");
                    // Just show destination if current location not available
                    myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 18f));
                }
            } else if (isSeniorTrackingMode) {
                // Senior viewing their own location and tracking rescuers
                markerTitle = "📍 Your Location";
                markerSnippet = receivedAddress != null && !receivedAddress.isEmpty() ? receivedAddress : "Your current location";

                // Add senior's location marker in light blue
                MarkerOptions seniorMarker = new MarkerOptions()
                        .position(emergencyLocation)
                        .title(markerTitle)
                        .snippet(markerSnippet)
                        .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE));
                myMap.addMarker(seniorMarker);

                // Start tracking rescuers
                startRescuerTracking();

                // Fit camera to show senior's location
                myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 15f));
                
                Log.d(TAG, "Senior tracking mode activated - showing blue location marker");
            } else if (isEmergencyMode) {
                // Senior viewing their own emergency location
                markerTitle = "🆘 EMERGENCY LOCATION 🚨";
                markerSnippet = receivedAddress != null && !receivedAddress.isEmpty() ?
                        receivedAddress : "Emergency Help Needed";

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(emergencyLocation)
                        .title(markerTitle)
                        .snippet(markerSnippet);
                myMap.addMarker(markerOptions);

                myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 18f));
            } else {
                // Regular location display
                markerTitle = "Current Location";
                markerSnippet = receivedAddress != null ? receivedAddress : "";

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(emergencyLocation)
                        .title(markerTitle)
                        .snippet(markerSnippet);
                myMap.addMarker(markerOptions);

                myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 15f));
            }

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

    private void startRescuerTracking() {
        if (helpRequestIdForTracking == null || helpRequestIdForTracking.isEmpty()) {
            Log.d(TAG, "No help request ID for tracking");
            return;
        }

        Log.d(TAG, "Starting rescuer tracking for help request: " + helpRequestIdForTracking);

        // Listen for rescuers responding to this help request
        rescuerLocationListener = db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .document(helpRequestIdForTracking)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening for rescuer updates", e);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        String respondedBy = documentSnapshot.getString("respondedBy");
                        
                        Log.d(TAG, "Help request status: " + status + ", responded by: " + respondedBy);

                        if ("responded".equals(status) && respondedBy != null) {
                            // A rescuer has responded, start tracking their location
                            trackRespondingRescuer(respondedBy);
                        }
                    }
                });
    }

    private void trackRespondingRescuer(String rescuerId) {
        Log.d(TAG, "Tracking responding rescuer: " + rescuerId);

        // Listen for the responding rescuer's location updates
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening for rescuer location", e);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Double latitude = documentSnapshot.getDouble("latitude");
                        Double longitude = documentSnapshot.getDouble("longitude");
                        String rescuerName = documentSnapshot.getString("rescuegroup");
                        
                        if (latitude != null && longitude != null) {
                            updateRescuerMarker(rescuerId, rescuerName, latitude, longitude);
                        }
                    } else {
                        // Rescuer document doesn't exist or was deleted, remove marker
                        removeRescuerMarker(rescuerId);
                    }
                });
    }

    private void removeRescuerMarker(String rescuerId) {
        if (rescuerMarkers.containsKey(rescuerId)) {
            rescuerMarkers.get(rescuerId).remove();
            rescuerMarkers.remove(rescuerId);
            updateTrackingInfo();
            Log.d(TAG, "Removed rescuer marker: " + rescuerId);
        }
    }

    private void updateRescuerMarker(String rescuerId, String rescuerName, double latitude, double longitude) {
        LatLng rescuerLocation = new LatLng(latitude, longitude);
        
        if (myMap == null) return;

        // Check if this is a new rescuer (first time seeing this rescuer)
        boolean isNewRescuer = !rescuerMarkers.containsKey(rescuerId);

        // Remove existing marker for this rescuer
        if (rescuerMarkers.containsKey(rescuerId)) {
            rescuerMarkers.get(rescuerId).remove();
        }

        // Create new marker with ambulance icon
        MarkerOptions rescuerMarker = new MarkerOptions()
                .position(rescuerLocation)
                .title("🚑 " + (rescuerName != null ? rescuerName : "Rescuer"))
                .snippet("Coming to help you")
                .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(R.drawable.ic_ambulance));

        Marker marker = myMap.addMarker(rescuerMarker);
        rescuerMarkers.put(rescuerId, marker);

        // Update tracking info
        updateTrackingInfo();

        // Send notification alert if this is a new rescuer and we're in senior tracking mode
        Log.d(TAG, "Checking notification conditions - isNewRescuer: " + isNewRescuer + ", isSeniorTrackingMode: " + isSeniorTrackingMode);
        
        if (isNewRescuer && isSeniorTrackingMode) {
            String displayName = rescuerName != null ? rescuerName : "A Rescuer";
            Log.d(TAG, "Sending notification for new rescuer: " + displayName + " (rescuerMarkers.size: " + rescuerMarkers.size() + ")");
            
            // Check if this is the first rescuer to respond
            if (rescuerMarkers.size() == 1) {
                // This is the first rescuer - send special notification
                Log.d(TAG, "Sending FIRST rescuer notification");
                sendFirstRescuerAlertNotification(displayName);
            } else {
                // Additional rescuers - send regular notification
                Log.d(TAG, "Sending additional rescuer notification");
                sendRescuerAlertNotification(displayName);
            }
        } else {
            Log.d(TAG, "Notification conditions not met - isNewRescuer: " + isNewRescuer + ", isSeniorTrackingMode: " + isSeniorTrackingMode);
        }

        Log.d(TAG, "Updated rescuer marker: " + rescuerId + " at " + latitude + ", " + longitude + 
              " (New rescuer: " + isNewRescuer + ")");
    }

    private void updateTrackingInfo() {
        if (tvEmergencyTitle != null) {
            tvEmergencyTitle.setText("🚑 Tracking Rescuers");
        }

        if (tvEmergencyAddress != null && receivedAddress != null) {
            tvEmergencyAddress.setText("📍 " + receivedAddress);
        }
        
        // Show tracking status
        if (tvDistanceTime != null) {
            if (rescuerMarkers.isEmpty()) {
                tvDistanceTime.setText("⏳ Waiting for rescuers to respond...");
            } else {
                tvDistanceTime.setText("🚑 " + rescuerMarkers.size() + " rescuer(s) coming to help");
            }
        }
        
        // Make sure the emergency info card is visible
        if (emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
            Log.d(TAG, "Tracking info card made visible");
        }
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
            
            // ALWAYS start location updates in rescuer mode to get current location
            if (isRescuerMode) {
                Log.d(TAG, "Starting location updates for rescuer mode");
                enableMyLocation();
                requestLocationPermissions();
                
                // Try to get last known location immediately
                getLastKnownLocation();
                
                // Set up a delayed check for distance calculation
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "Delayed check - currentLocation: " + (currentLocation != null) + 
                          ", destinationLocation: " + (destinationLocation != null));
                    if (currentLocation != null && destinationLocation != null) {
                        Log.d(TAG, "Both locations available in delayed check, calculating distance");
                        calculateSimpleDistanceAndTime();
                        if (!routeDisplayed) {
                            showRoute();
                        }
                    }
                }, 2000); // Check after 2 seconds
            } else if (isSeniorTrackingMode) {
                Log.d(TAG, "Senior tracking mode - no location updates needed");
                // For senior tracking mode, we don't need location updates
                // The senior's location is already set and we're tracking rescuers
            }
            
            // If we already have current location, calculate distance immediately
            if (currentLocation != null && destinationLocation != null) {
                calculateSimpleDistanceAndTime();
            }
        } else {
            Log.d(TAG, "No received location, starting normal mode");
            // Enable location layer if permission is granted (normal mode)
            enableMyLocation();

            // Request location permissions for normal mode
            requestLocationPermissions();
        }

        // Show emergency info card for rescuer mode
        if (isRescuerMode && emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
            updateEmergencyInfo();
            
            // Auto-show route if both locations are available
            if (currentLocation != null && destinationLocation != null) {
                // Delay slightly to ensure map is fully loaded
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!routeDisplayed) {
                        showRoute();
                    }
                }, 1000);
            }
        } else if (isSeniorTrackingMode && emergencyInfoCard != null) {
            // Show tracking info card for senior tracking mode
            emergencyInfoCard.setVisibility(View.VISIBLE);
            updateTrackingInfo();
        } else {
            Log.d(TAG, "isRescuerMode: " + isRescuerMode + ", isSeniorTrackingMode: " + isSeniorTrackingMode + 
                  ", emergencyInfoCard: " + (emergencyInfoCard != null));
        }
    }

    private void getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation called");
        
        // Check if fusedLocationClient is initialized
        if (fusedLocationClient == null) {
            Log.d(TAG, "fusedLocationClient is null, cannot get last known location");
            return;
        }
        
        // Check if we have location permissions first
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No location permissions granted, requesting permissions");
            requestLocationPermissions();
            return;
        }
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        Log.d(TAG, "Last known location received: " + location.getLatitude() + ", " + location.getLongitude());
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        updateMapLocation(location);
                        // If we have current and destination, calculate distance
                        if (destinationLocation != null) {
                            calculateSimpleDistanceAndTime();
                        }
                    } else {
                        Log.d(TAG, "No last known location available, starting location updates");
                        // If no last known location, start location updates to get current location
                        startLocationUpdates();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Failed to get last known location", e);
                    // Fallback to starting location updates
                    startLocationUpdates();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Only proceed if fusedLocationClient is initialized
        if (fusedLocationClient == null) {
            Log.d(TAG, "fusedLocationClient is null in onResume, skipping location updates");
            return;
        }

        // Start location updates if we have permissions and not already active
        if (!locationUpdatesActive) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                // Always start location updates in rescuer mode to get current location
                if (isRescuerMode) {
                    Log.d(TAG, "Starting location updates in onResume for rescuer mode");
                    startLocationUpdates();
                } else if (receivedLat == 0.0 && receivedLong == 0.0) {
                    // Only start in normal mode if no received location
                    startLocationUpdates();
                }
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
        
        // Mark activity as destroyed to prevent new operations
        isDestroyed = true;
        
        // Clean up rescuer tracking listener
        if (rescuerLocationListener != null) {
            rescuerLocationListener.remove();
            rescuerLocationListener = null;
        }
        
        // Clear emergency notifications
        clearEmergencyNotifications();
        
        // Clean up executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private void getDirectionsRoute(LatLng origin, LatLng destination) {
        Log.d(TAG, "Getting directions route from " + origin + " to " + destination);
        
        // Check if activity is destroyed
        if (isDestroyed) {
            Log.d(TAG, "Activity is destroyed, skipping route calculation");
            return;
        }
        
        // Check if executor service is available and not terminated
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            Log.d(TAG, "Executor service is not available or terminated, skipping route calculation");
            return;
        }
        
        // Set calculating flag
        isCalculatingRoute = true;
        
        // Build the URL for Google Directions API
        String url = DIRECTIONS_API_URL + "?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&key=" + DIRECTIONS_API_KEY +
                "&mode=driving";

        Log.d(TAG, "Directions API URL: " + url);

        // Execute the API call in background thread
        try {
            executorService.execute(() -> {
                // Check again if activity is destroyed before making HTTP request
                if (isDestroyed) {
                    Log.d(TAG, "Activity destroyed during route calculation, aborting");
                    return;
                }
                
                try {
                    String response = makeHttpRequest(url);
                    Log.d(TAG, "Directions API response: " + response);
                    
                    // Check if activity is still alive before updating UI
                    if (!isDestroyed) {
                        runOnUiThread(() -> {
                            if (!isDestroyed) {
                                parseDirectionsResponse(response);
                                // Clear calculating flag after successful parsing
                                isCalculatingRoute = false;
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting directions", e);
                    if (!isDestroyed) {
                        runOnUiThread(() -> {
                            if (!isDestroyed) {
                                Toast.makeText(this, "Error getting route. Using straight line.", Toast.LENGTH_SHORT).show();
                                // Fallback to straight line route
                                showStraightLineRoute();
                                // Clear calculating flag after error
                                isCalculatingRoute = false;
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error submitting task to executor service", e);
            isCalculatingRoute = false;
        }
    }

    private String makeHttpRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } finally {
            connection.disconnect();
        }

        return response.toString();
    }

    private void parseDirectionsResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            String status = jsonResponse.getString("status");
            
            if ("OK".equals(status)) {
                JSONArray routes = jsonResponse.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    JSONArray legs = route.getJSONArray("legs");
                    
                    if (legs.length() > 0) {
                        JSONObject leg = legs.getJSONObject(0);
                        
                        // Get distance and duration
                        JSONObject distance = leg.getJSONObject("distance");
                        JSONObject duration = leg.getJSONObject("duration");
                        
                        estimatedDistance = distance.getString("text");
                        estimatedTime = duration.getString("text");
                        
                        Log.d(TAG, "Route distance: " + estimatedDistance + ", duration: " + estimatedTime);
                        
                        // Get route points
                        JSONObject polyline = route.getJSONObject("overview_polyline");
                        String points = polyline.getString("points");
                        
                        // Decode polyline points
                        List<LatLng> decodedPoints = decodePolyline(points);
                        
                        // Draw the route
                        drawRouteOnMap(decodedPoints);
                        
                        // Update distance/time display
                        updateDistanceTimeDisplay();
                        
                        Toast.makeText(this, "Route calculated: " + estimatedDistance + " • " + estimatedTime, Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Log.e(TAG, "Directions API error: " + status);
                Toast.makeText(this, "Error getting route. Using straight line.", Toast.LENGTH_SHORT).show();
                showStraightLineRoute();
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing directions response", e);
            Toast.makeText(this, "Error parsing route. Using straight line.", Toast.LENGTH_SHORT).show();
            showStraightLineRoute();
        }
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng(((double) lat / 1E5), ((double) lng / 1E5));
            poly.add(p);
        }

        return poly;
    }

    private void drawRouteOnMap(List<LatLng> points) {
        if (myMap == null || points.isEmpty()) {
            Log.d(TAG, "Cannot draw route - map is null or no points");
            return;
        }

        // Clear existing route
        if (currentRoute != null) {
            currentRoute.remove();
        }

        // Draw the new route
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(points)
                .color(getResources().getColor(android.R.color.holo_blue_dark))
                .width(8);

        currentRoute = myMap.addPolyline(polylineOptions);
        routePoints.clear();
        routePoints.addAll(points);
        routeDisplayed = true;

        // Update button text
        if (btnShowRoute != null) {
            btnShowRoute.setText(getString(R.string.btn_hide_route));
        }

        // Fit camera to show the entire route
        if (points.size() > 1) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng point : points) {
                builder.include(point);
            }
            LatLngBounds bounds = builder.build();
            myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
        }

        Log.d(TAG, "Route drawn with " + points.size() + " points");
    }

    private void showStraightLineRoute() {
        Log.d(TAG, "Showing straight line route as fallback");
        
        routePoints.clear();
        routePoints.add(currentLocation);
        routePoints.add(destinationLocation);

        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .color(getResources().getColor(android.R.color.holo_blue_dark))
                .width(8);

        currentRoute = myMap.addPolyline(polylineOptions);

        // Fit camera to show both locations
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(currentLocation);
        builder.include(destinationLocation);
        LatLngBounds bounds = builder.build();

        myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

        routeDisplayed = true;
        if (btnShowRoute != null) {
            btnShowRoute.setText(getString(R.string.btn_hide_route));
        }

        // Calculate simple distance and time
        calculateSimpleDistanceAndTime();
        
        // Clear calculating flag since route is now displayed
        isCalculatingRoute = false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESCRIPTION);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private void checkNotificationPermissions() {
        Log.d(TAG, "Checking notification permissions...");
        
        // Check for Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "POST_NOTIFICATIONS permission granted: " + hasPermission);
            
            if (!hasPermission) {
                Log.w(TAG, "Notification permission not granted. Requesting...");
                requestNotificationPermissions();
            } else {
                Log.d(TAG, "Notification permission already granted.");
            }
        } else {
            Log.d(TAG, "Android version < 13, notification permission not required");
        }
    }

    private void requestNotificationPermissions() {
        Log.d(TAG, "Requesting notification permissions...");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, we need to request POST_NOTIFICATIONS permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted");
            }
        } else {
            Log.d(TAG, "Android version < 13, opening notification settings");
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
            Toast.makeText(this, "Please enable notifications for this app in settings.", Toast.LENGTH_LONG).show();
        }
    }

    private void sendRescuerAlertNotification(String rescuerName) {
        Log.d(TAG, "Sending rescuer alert notification for: " + rescuerName);
        Log.d(TAG, "Current mode - isSeniorTrackingMode: " + isSeniorTrackingMode + ", rescuerMarkers.size: " + rescuerMarkers.size());
        
        // Create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ambulance)
                .setContentTitle("🚑 Rescuer Responding!")
                .setContentText(rescuerName + " is coming to help you")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(rescuerName + " has responded to your emergency and is on the way to help you. You can track their location on the map."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500, 200, 500}) // Vibration pattern
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);

        // Show notification
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            
            // Check notification permission
            boolean hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Notification permission granted: " + hasPermission);
            
            if (hasPermission) {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                Log.d(TAG, "Rescuer alert notification sent successfully with ID: " + NOTIFICATION_ID);
                
                // Also show a toast message
                runOnUiThread(() -> {
                    Toast.makeText(this, "🚑 " + rescuerName + " is responding to your emergency!", Toast.LENGTH_LONG).show();
                });
            } else {
                Log.w(TAG, "Notification permission not granted - requesting permission");
                requestNotificationPermissions();
                
                // Fallback to just toast message
                runOnUiThread(() -> {
                    Toast.makeText(this, "🚑 " + rescuerName + " is responding to your emergency!", Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending notification", e);
            // Fallback to just toast message
            runOnUiThread(() -> {
                Toast.makeText(this, "🚑 " + rescuerName + " is responding to your emergency!", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void sendFirstRescuerAlertNotification(String rescuerName) {
        Log.d(TAG, "Sending FIRST rescuer alert notification for: " + rescuerName);
        Log.d(TAG, "Current mode - isSeniorTrackingMode: " + isSeniorTrackingMode + ", rescuerMarkers.size: " + rescuerMarkers.size());
        
        // Create a more prominent notification for the first responder
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ambulance)
                .setContentTitle("🚨 EMERGENCY RESPONSE!")
                .setContentText(rescuerName + " is responding to your emergency")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("🎉 " + rescuerName + " has responded to your emergency call! Help is on the way. You can track their location on the map."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000}) // Longer vibration pattern
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setOngoing(true); // Make it persistent until user dismisses

        // Show notification
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            
            // Check notification permission
            boolean hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Notification permission granted: " + hasPermission);
            
            if (hasPermission) {
                notificationManager.notify(NOTIFICATION_ID + 1, builder.build()); // Different ID for first responder
                Log.d(TAG, "First rescuer alert notification sent successfully with ID: " + (NOTIFICATION_ID + 1));
                
                // Also show a prominent toast message
                runOnUiThread(() -> {
                    Toast.makeText(this, "🎉 " + rescuerName + " is responding to your emergency! Help is on the way!", Toast.LENGTH_LONG).show();
                });
            } else {
                Log.w(TAG, "Notification permission not granted - requesting permission");
                requestNotificationPermissions();
                
                // Fallback to just toast message
                runOnUiThread(() -> {
                    Toast.makeText(this, "🎉 " + rescuerName + " is responding to your emergency! Help is on the way!", Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending first rescuer notification", e);
            // Fallback to just toast message
            runOnUiThread(() -> {
                Toast.makeText(this, "🎉 " + rescuerName + " is responding to your emergency! Help is on the way!", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void clearEmergencyNotifications() {
        Log.d(TAG, "Clearing emergency notifications");
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.cancel(NOTIFICATION_ID);
            notificationManager.cancel(NOTIFICATION_ID + 1);
            Log.d(TAG, "Emergency notifications cleared successfully");
            
            // Also show a toast to confirm
            runOnUiThread(() -> {
                Toast.makeText(this, "Emergency notifications cleared", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error clearing notifications", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1002) { // Notification permission request
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted by user");
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Notification permission denied by user");
                Toast.makeText(this, "Notification permission denied. Some features may not work properly.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Test method to verify notifications are working
    private void testNotification() {
        Log.d(TAG, "Testing notification system...");
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ambulance)
                .setContentTitle("🧪 Test Notification")
                .setContentText("This is a test notification to verify the system is working")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            boolean hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            
            if (hasPermission) {
                notificationManager.notify(9999, builder.build()); // Use different ID for test
                Log.d(TAG, "Test notification sent successfully");
                Toast.makeText(this, "Test notification sent!", Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "Cannot send test notification - permission not granted");
                Toast.makeText(this, "Cannot send test notification - permission not granted", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending test notification", e);
            Toast.makeText(this, "Error sending test notification: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}