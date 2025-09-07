package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.LatLngBounds;

// Google Navigation SDK imports removed - using enhanced Google Maps instead

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GoogleNavigationActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "GoogleNavigation";
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesActive = false;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    // Emergency data
    private double emergencyLat = 0.0;
    private double emergencyLong = 0.0;
    private String emergencyAddress = "";
    private String seniorName = "";
    private String seniorPhone = "";
    private String helpRequestId = "";

    // Location tracking
    private LatLng currentLocation = null;
    private LatLng emergencyLocation = null;

    // UI Elements
    private LinearLayout emergencyInfoCard;
    private LinearLayout topNavigationBanner;
    private LinearLayout bottomNavigationBar;
    private LinearLayout speedIndicator;
    private LinearLayout mapControls;
    private TextView tvEmergencyTitle;
    private TextView tvEmergencyAddress;
    private TextView tvDistanceTime;
    private TextView tvRemainingDistance;
    private TextView tvDestinationName;
    private TextView tvCurrentInstruction;
    private TextView tvEstimatedTime;
    private TextView tvRemainingDistanceBottom;
    private TextView tvArrivalTime;
    private TextView tvCurrentSpeed;
    private TextView tvGpsStatus;
    private Button btnStartNavigation;
    private Button btnStopNavigation;
    private Button btnCallSenior;
    private Button btnOpenExternalMaps;
    private Button btnArrived;
    private ImageButton btnBack;
    private ImageButton btnCancelNavigation;
    private ImageButton btnRouteOptions;
    private ImageButton btnCompass;
    private ImageButton btnZoomIn;
    private ImageButton btnZoomOut;
    private ImageButton btnAudioToggle;

    // Navigation state
    private boolean isNavigating = false;
    private String estimatedDistance = "";
    private String estimatedTime = "";
    private ExecutorService executorService;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Enhanced Google Maps Navigation
    private boolean isEnhancedNavigationActive = false;
    
    // Route handling
    private Polyline currentRoute;
    private List<LatLng> routePoints = new ArrayList<>();
    
    // Markers
    private com.google.android.gms.maps.model.Marker rescuerMarker;
    private com.google.android.gms.maps.model.Marker emergencyMarker;
    
    // Navigation
    private TextToSpeech textToSpeech;
    private boolean isVoiceEnabled = true;
    private List<String> navigationSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    
    // Real-time movement tracking
    private List<LatLng> routePolyline = new ArrayList<>();
    private int currentRouteIndex = 0;
    private Location lastKnownLocation;
    private float currentBearing = 0f;
    
    // GPS Navigation features
    private LocationRequest gpsLocationRequest;
    private LocationCallback gpsLocationCallback;
    private float currentSpeed = 0f; // in km/h
    private double totalDistance = 0.0; // in meters
    private long startTime;
    private boolean isGpsNavigationActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "GoogleNavigationActivity onCreate started");
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_google_navigation);
        Log.d(TAG, "Layout set successfully");

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        Log.d(TAG, "Firebase initialized");

        // Initialize UI
        initializeUI();
        Log.d(TAG, "UI initialized");
        
        // Initialize TextToSpeech
        initializeTextToSpeech();
        Log.d(TAG, "TextToSpeech initialized");

        // Get emergency data from Intent
        Intent intent = getIntent();
        if (intent != null) {
            emergencyLat = intent.getDoubleExtra("latitude", 0.0);
            emergencyLong = intent.getDoubleExtra("longitude", 0.0);
            emergencyAddress = intent.getStringExtra("locationAddress");
            seniorName = intent.getStringExtra("seniorName");
            seniorPhone = intent.getStringExtra("seniorPhone");
            helpRequestId = intent.getStringExtra("helpRequestId");

            Log.d(TAG, "Emergency data received: " + seniorName + " at " + emergencyAddress);
            Log.d(TAG, "Emergency coordinates: " + emergencyLat + ", " + emergencyLong);
        } else {
            Log.e(TAG, "No intent data received!");
        }

        // Set emergency location
        if (emergencyLat != 0.0 && emergencyLong != 0.0) {
            emergencyLocation = new LatLng(emergencyLat, emergencyLong);
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newSingleThreadExecutor();
        
        // Initialize GPS navigation
        setupGpsNavigation();

        // Register permission launcher
        registerLocationPermissionLauncher();

        // Initialize Enhanced Google Maps Navigation
        initializeEnhancedNavigation();

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Enhanced navigation is ready
    }

    private void initializeEnhancedNavigation() {
        try {
            Log.d(TAG, "Enhanced Google Maps Navigation initialized successfully");
            // Don't show toast immediately to avoid spam
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Enhanced Navigation", e);
            Toast.makeText(this, "Error initializing navigation. Using fallback.", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeUI() {
        try {
            Log.d(TAG, "Starting UI initialization");
            
            // Initialize layout containers
            emergencyInfoCard = findViewById(R.id.emergencyInfoCard);
            topNavigationBanner = findViewById(R.id.topNavigationBanner);
            bottomNavigationBar = findViewById(R.id.bottomNavigationBar);
            speedIndicator = findViewById(R.id.speedIndicator);
            mapControls = findViewById(R.id.mapControls);
            
            Log.d(TAG, "Layout containers initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing layout containers", e);
            Toast.makeText(this, "Error initializing UI", Toast.LENGTH_SHORT).show();
        }

        // Initialize text views
        tvEmergencyTitle = findViewById(R.id.tvEmergencyTitle);
        tvEmergencyAddress = findViewById(R.id.tvEmergencyAddress);
        tvDistanceTime = findViewById(R.id.tvDistanceTime);
        tvRemainingDistance = findViewById(R.id.tvRemainingDistance);
        tvDestinationName = findViewById(R.id.tvDestinationName);
        tvCurrentInstruction = findViewById(R.id.tvCurrentInstruction);
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        tvRemainingDistanceBottom = findViewById(R.id.tvRemainingDistanceBottom);
        tvArrivalTime = findViewById(R.id.tvArrivalTime);
        tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed);
        tvGpsStatus = findViewById(R.id.tvGpsStatus);

        // Initialize buttons
        try {
            btnStartNavigation = findViewById(R.id.btnStartNavigation);
            btnStopNavigation = findViewById(R.id.btnStopNavigation);
            btnCallSenior = findViewById(R.id.btnCallSenior);
            btnOpenExternalMaps = findViewById(R.id.btnOpenExternalMaps);
            btnArrived = findViewById(R.id.btnArrived);
            btnBack = findViewById(R.id.btnBack);
            btnCancelNavigation = findViewById(R.id.btnCancelNavigation);
            btnRouteOptions = findViewById(R.id.btnRouteOptions);
            btnCompass = findViewById(R.id.btnCompass);
            btnZoomIn = findViewById(R.id.btnZoomIn);
            btnZoomOut = findViewById(R.id.btnZoomOut);
            btnAudioToggle = findViewById(R.id.btnAudioToggle);
            
            Log.d(TAG, "Buttons initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing buttons", e);
            Toast.makeText(this, "Error initializing buttons", Toast.LENGTH_SHORT).show();
        }

        // Set click listeners
        btnStartNavigation.setOnClickListener(v -> {
            Log.d(TAG, "Start Navigation button clicked");
            startGoogleNavigation();
        });
        btnStopNavigation.setOnClickListener(v -> {
            Log.d(TAG, "Stop Navigation button clicked");
            stopGoogleNavigation();
        });
        btnCallSenior.setOnClickListener(v -> {
            Log.d(TAG, "Call Senior button clicked");
            callSenior();
        });
        btnOpenExternalMaps.setOnClickListener(v -> {
            Log.d(TAG, "Open External Maps button clicked");
            openExternalMaps();
        });
        btnArrived.setOnClickListener(v -> {
            Log.d(TAG, "Mark Arrived button clicked");
            markArrived();
        });
        btnBack.setOnClickListener(v -> {
            Log.d(TAG, "Back button clicked");
            finish();
        });
        btnCancelNavigation.setOnClickListener(v -> {
            Log.d(TAG, "Cancel Navigation button clicked");
            stopGoogleNavigation();
        });
        btnRouteOptions.setOnClickListener(v -> {
            Log.d(TAG, "Route Options button clicked - Starting movement simulation");
            if (isNavigating && !routePolyline.isEmpty()) {
                simulateMovementAlongRoute();
                Toast.makeText(this, "🚗 Starting movement simulation along route!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ No route available for movement simulation", Toast.LENGTH_SHORT).show();
            }
        });
        btnCompass.setOnClickListener(v -> {
            Log.d(TAG, "Compass button clicked");
            resetMapOrientation();
        });
        btnZoomIn.setOnClickListener(v -> {
            Log.d(TAG, "Zoom In button clicked");
            zoomIn();
        });
        btnZoomOut.setOnClickListener(v -> {
            Log.d(TAG, "Zoom Out button clicked");
            zoomOut();
        });
        btnAudioToggle.setOnClickListener(v -> {
            Log.d(TAG, "Audio Toggle button clicked");
            toggleAudio();
        });

        // Update emergency info
        updateEmergencyInfo();
    }
    
    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "TextToSpeech initialized successfully");
                    // Set language to English
                    int result = textToSpeech.setLanguage(java.util.Locale.ENGLISH);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "Language not supported");
                    }
                } else {
                    Log.e(TAG, "TextToSpeech initialization failed");
                }
            }
        });
    }

    private void updateEmergencyInfo() {
        if (tvEmergencyTitle != null) {
            tvEmergencyTitle.setText("🚨 EMERGENCY: " + (seniorName != null ? seniorName : "Senior"));
        }

        if (tvEmergencyAddress != null && emergencyAddress != null) {
            tvEmergencyAddress.setText("📍 " + emergencyAddress);
        }

        if (tvDistanceTime != null && !estimatedDistance.isEmpty() && !estimatedTime.isEmpty()) {
            tvDistanceTime.setText("📍 " + estimatedDistance + " • ⏱️ " + estimatedTime);
        } else if (tvDistanceTime != null) {
            tvDistanceTime.setText("📍 Calculating distance...");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;

        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED 
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }

        // Enable location features
        try {
            googleMap.setMyLocationEnabled(true); // Enable default location dot
            googleMap.getUiSettings().setMyLocationButtonEnabled(true); // Enable default location button
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setZoomGesturesEnabled(true);
            googleMap.getUiSettings().setScrollGesturesEnabled(true);
            googleMap.getUiSettings().setTiltGesturesEnabled(true);
            googleMap.getUiSettings().setRotateGesturesEnabled(true);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException enabling location features", e);
        }

        // Display emergency location
        if (emergencyLocation != null) {
            displayEmergencyLocation();
        }

        // Get current location
        getCurrentLocation();
    }

    private void displayEmergencyLocation() {
        if (googleMap == null || emergencyLocation == null) {
            return;
        }

        // Remove existing emergency marker
        if (emergencyMarker != null) {
            emergencyMarker.remove();
        }
        
        // Add emergency marker with red pin
        MarkerOptions markerOptions = new MarkerOptions()
                .position(emergencyLocation)
                .title("🚨 Emergency Location")
                .snippet("👤 " + seniorName + "\n📍 " + emergencyAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));

        emergencyMarker = googleMap.addMarker(markerOptions);
        
        // Move camera to show both locations
        if (currentLocation != null) {
            // Show both current location and emergency location
            LatLng center = new LatLng(
                (currentLocation.latitude + emergencyLocation.latitude) / 2,
                (currentLocation.longitude + emergencyLocation.longitude) / 2
            );
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 12f));
            
            // Fetch and draw route
            fetchRoute(currentLocation, emergencyLocation);
        } else {
            // Move camera to emergency location only
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 15f));
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED 
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }

        if (fusedLocationClient != null) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        Log.d(TAG, "Current location obtained: " + currentLocation.latitude + ", " + currentLocation.longitude);
                        
                        // Calculate distance and time
                        calculateDistanceAndTime();
                        updateEmergencyInfo();
                    } else {
                        startLocationUpdates();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting last location", e);
                    startLocationUpdates();
                });
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (fusedLocationClient != null) {
            LocationRequest locationRequest = new LocationRequest.Builder(5000)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(3000)
                    .build();

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        Location location = locationResult.getLastLocation();
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        
                        Log.d(TAG, "Location updated: " + currentLocation.latitude + ", " + currentLocation.longitude);
                        
                        // Update distance and time
                        calculateDistanceAndTime();
                        updateEmergencyInfo();
                        
                        // Update rescuer location in Firebase
                        updateRescuerLocationInFirebase(location);
                        
                        // Update speed indicator
                        updateSpeedIndicator(location);
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper());
            locationUpdatesActive = true;
            Log.d(TAG, "Location updates started");
        }
    }

    private void calculateDistanceAndTime() {
        if (currentLocation == null || emergencyLocation == null) {
            return;
        }

        // Calculate straight-line distance
        float[] results = new float[1];
        Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                emergencyLocation.latitude, emergencyLocation.longitude,
                results
        );

        float distanceInMeters = results[0];
        
        // Format distance
        if (distanceInMeters < 1000) {
            estimatedDistance = String.format(Locale.getDefault(), "%.0f m", distanceInMeters);
        } else {
            estimatedDistance = String.format(Locale.getDefault(), "%.1f km", distanceInMeters / 1000);
        }

        // Estimate time (assuming average driving speed of 40 km/h in urban areas)
        float estimatedTimeMinutes = (distanceInMeters / 1000) / 40 * 60;
        
        if (estimatedTimeMinutes < 1) {
            estimatedTime = "< 1 min";
        } else {
            estimatedTime = String.format(Locale.getDefault(), "%.0f min", estimatedTimeMinutes);
        }

        Log.d(TAG, "Calculated distance: " + estimatedDistance + ", time: " + estimatedTime);
        
        // Update rescuer marker with ambulance icon
        updateRescuerMarker();
        
        // Fetch route if we have both locations
        if (currentLocation != null && emergencyLocation != null) {
            fetchRoute(currentLocation, emergencyLocation);
        }
    }
    
    private void updateRescuerMarker() {
        if (googleMap == null || currentLocation == null) {
            return;
        }
        
        // Remove existing rescuer marker
        if (rescuerMarker != null) {
            rescuerMarker.remove();
        }
        
        // Calculate position along route for real-time movement
        LatLng markerPosition = currentLocation;
        float bearing = 0f;
        
        if (isNavigating && !routePolyline.isEmpty()) {
            // Find closest point on route and move along it
            markerPosition = getPositionAlongRoute(currentLocation);
            bearing = calculateBearing(markerPosition);
        }
        
        // Add new rescuer marker with blue pin and rotation
        MarkerOptions rescuerMarkerOptions = new MarkerOptions()
                .position(markerPosition)
                .title("🚑 Rescuer Location")
                .snippet("Emergency responder en route")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .rotation(bearing);
        
        rescuerMarker = googleMap.addMarker(rescuerMarkerOptions);
        
        // Update last known location and bearing
        lastKnownLocation = new Location("rescuer");
        lastKnownLocation.setLatitude(markerPosition.latitude);
        lastKnownLocation.setLongitude(markerPosition.longitude);
        currentBearing = bearing;
        
        Log.d(TAG, "Rescuer marker updated at: " + markerPosition + " with bearing: " + bearing);
    }
    
    private BitmapDescriptor createCustomMarkerIcon(int drawableResId) {
        Drawable drawable = getResources().getDrawable(drawableResId);
        
        // Create a reasonably sized bitmap
        int size = 64; // 64dp - smaller but still visible
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Scale the drawable to fit the bitmap
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void startGoogleNavigation() {
        Log.d(TAG, "startGoogleNavigation called");
        Log.d(TAG, "currentLocation: " + currentLocation);
        Log.d(TAG, "emergencyLocation: " + emergencyLocation);
        
        if (currentLocation == null || emergencyLocation == null) {
            Log.e(TAG, "Location not available - currentLocation: " + currentLocation + ", emergencyLocation: " + emergencyLocation);
            Toast.makeText(this, "Location not available. Getting location...", Toast.LENGTH_SHORT).show();
            getCurrentLocation();
            return;
        }

        try {
            // Start enhanced Google Maps navigation
            isEnhancedNavigationActive = true;
            isNavigating = true;
            switchToNavigationUI();
            
            // Start GPS navigation for real-time tracking
            startGpsNavigation();
            
            // Show enhanced navigation features
            Toast.makeText(this, "🗺️ GPS Navigation started with real-time tracking!", Toast.LENGTH_LONG).show();
            
            // Update UI with navigation information
            updateTopNavigationBanner();
            
            // Start continuous location updates for navigation
            startLocationUpdates();
            
            Log.d(TAG, "Enhanced Google Maps Navigation started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error starting Enhanced Google Navigation", e);
            Toast.makeText(this, "Error starting navigation. Using fallback.", Toast.LENGTH_LONG).show();
            // Fallback to external Google Maps
            openExternalMaps();
        }
    }

    private void stopGoogleNavigation() {
        isNavigating = false;
        isEnhancedNavigationActive = false;
        
        // Stop GPS navigation
        stopGpsNavigation();
        
        // Stop any current voice guidance
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        
        try {
            // Stop enhanced navigation
            Log.d(TAG, "GPS Navigation stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping GPS navigation", e);
        }
        
        // Switch back to emergency info UI
        switchToEmergencyInfoUI();
        
        Toast.makeText(this, "🛰️ GPS Navigation stopped", Toast.LENGTH_SHORT).show();
    }

    private void switchToNavigationUI() {
        // Hide emergency info card
        if (emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.GONE);
        }
        
        // Show Google Maps-style navigation UI
        if (topNavigationBanner != null) {
            topNavigationBanner.setVisibility(View.VISIBLE);
        }
        if (bottomNavigationBar != null) {
            bottomNavigationBar.setVisibility(View.VISIBLE);
        }
        if (speedIndicator != null) {
            speedIndicator.setVisibility(View.VISIBLE);
        }
        if (mapControls != null) {
            mapControls.setVisibility(View.VISIBLE);
        }
        
        // Update top banner with emergency info
        updateTopNavigationBanner();
    }

    private void switchToEmergencyInfoUI() {
        // Hide Google Maps-style navigation UI
        if (topNavigationBanner != null) {
            topNavigationBanner.setVisibility(View.GONE);
        }
        if (bottomNavigationBar != null) {
            bottomNavigationBar.setVisibility(View.GONE);
        }
        if (speedIndicator != null) {
            speedIndicator.setVisibility(View.GONE);
        }
        if (mapControls != null) {
            mapControls.setVisibility(View.GONE);
        }
        
        // Show emergency info card
        if (emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
        }
        
        // Reset button visibility
        if (btnStartNavigation != null) btnStartNavigation.setVisibility(View.VISIBLE);
        if (btnStopNavigation != null) btnStopNavigation.setVisibility(View.GONE);
        if (btnArrived != null) btnArrived.setVisibility(View.GONE);
    }

    private void updateTopNavigationBanner() {
        if (tvRemainingDistance != null && !estimatedDistance.isEmpty()) {
            tvRemainingDistance.setText(estimatedDistance);
        }
        
        if (tvDestinationName != null && seniorName != null) {
            tvDestinationName.setText("Emergency: " + seniorName);
        }
        
        if (tvCurrentInstruction != null) {
            tvCurrentInstruction.setText("Follow the route to emergency location");
        }
    }

    private void updateSpeedIndicator(Location location) {
        if (location != null && tvCurrentSpeed != null) {
            float speed = location.getSpeed(); // Speed in m/s
            float speedKmh = speed * 3.6f; // Convert to km/h
            
            if (speedKmh < 1) {
                tvCurrentSpeed.setText("0 km/h");
            } else {
                tvCurrentSpeed.setText(String.format(Locale.getDefault(), "%.0f km/h", speedKmh));
            }
        }
    }

    private void callSenior() {
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            startActivity(callIntent);
        } else {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternalMaps() {
        if (emergencyLocation == null) {
            Toast.makeText(this, "Emergency location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create Google Maps navigation intent
            String navigationUri = String.format(Locale.getDefault(), "google.navigation:q=%f,%f&mode=d", 
                emergencyLocation.latitude, emergencyLocation.longitude);
            Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
            navigationIntent.setPackage("com.google.android.apps.maps");
            
            if (navigationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navigationIntent);
                Toast.makeText(this, "🚗 Opening Google Maps navigation", Toast.LENGTH_LONG).show();
            } else {
                // Fallback to web-based Google Maps
                String webMapsUri = String.format(Locale.getDefault(), "https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving", 
                    emergencyLocation.latitude, emergencyLocation.longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webMapsUri));
                startActivity(webIntent);
                Toast.makeText(this, "🌐 Opening web-based navigation", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening external Google Maps navigation", e);
            Toast.makeText(this, "Error opening navigation", Toast.LENGTH_SHORT).show();
        }
    }

    private void markArrived() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mark as Arrived");
        builder.setMessage("Have you arrived at the emergency location?");
        
        builder.setPositiveButton("Yes, Arrived", (dialog, which) -> {
            // Update help request status
            if (helpRequestId != null && !helpRequestId.isEmpty()) {
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("status", "rescuer_arrived");
                updates.put("rescuerArrivedTime", System.currentTimeMillis());
                
                db.collection("Sagip")
                    .document("helpRequests")
                    .collection("activeRequests")
                    .document(helpRequestId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "✅ Marked as arrived! Help request updated.", Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating help request status", e);
                        Toast.makeText(this, "Error updating status", Toast.LENGTH_SHORT).show();
                    });
            } else {
                Toast.makeText(this, "✅ Marked as arrived!", Toast.LENGTH_LONG).show();
                finish();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateRescuerLocationInFirebase(Location location) {
        if (helpRequestId == null || helpRequestId.isEmpty()) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        java.util.Map<String, Object> rescuerLocation = new java.util.HashMap<>();
        rescuerLocation.put("latitude", location.getLatitude());
        rescuerLocation.put("longitude", location.getLongitude());
        rescuerLocation.put("timestamp", System.currentTimeMillis());
        rescuerLocation.put("rescuerId", currentUser.getUid());
        rescuerLocation.put("rescuerName", getCurrentRescuerName());

        // Update rescuer location in Firebase
        db.collection("Sagip")
            .document("helpRequests")
            .collection("activeRequests")
            .document(helpRequestId)
            .collection("rescuerLocations")
            .document(currentUser.getUid())
            .set(rescuerLocation)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Rescuer location updated in Firebase");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating rescuer location in Firebase", e);
            });
    }

    private String getCurrentRescuerName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
            String email = currentUser.getEmail();
            if (email != null && !email.isEmpty()) {
                return email.split("@")[0];
            }
        }
        return "Rescuer";
    }

    private void showRouteOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Route Options");
        builder.setMessage("Choose your preferred route:");
        
        builder.setPositiveButton("Fastest Route", (dialog, which) -> {
            Toast.makeText(this, "Fastest route selected", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNeutralButton("Shortest Route", (dialog, which) -> {
            Toast.makeText(this, "Shortest route selected", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void resetMapOrientation() {
        if (googleMap != null && currentLocation != null) {
            // Recenter map to current location with navigation zoom level
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
            Toast.makeText(this, "📍 Recentered to your location", Toast.LENGTH_SHORT).show();
        } else if (googleMap != null && emergencyLocation != null) {
            // If no current location, center on emergency location
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 16f));
            Toast.makeText(this, "📍 Recentered to emergency location", Toast.LENGTH_SHORT).show();
        }
    }

    private void zoomIn() {
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.zoomIn());
            Toast.makeText(this, "🔍 Zoomed in", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void zoomOut() {
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.zoomOut());
            Toast.makeText(this, "🔍 Zoomed out", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleAudio() {
        isVoiceEnabled = !isVoiceEnabled;
        
        // Stop any current speech
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        
        if (isVoiceEnabled) {
            Toast.makeText(this, "🔊 Voice guidance enabled", Toast.LENGTH_SHORT).show();
            // Update button icon to show voice is on
            if (btnAudioToggle != null) {
                btnAudioToggle.setImageResource(R.drawable.ic_volume_on);
            }
        } else {
            Toast.makeText(this, "🔇 Voice guidance disabled", Toast.LENGTH_SHORT).show();
            // Update button icon to show voice is off
            if (btnAudioToggle != null) {
                btnAudioToggle.setImageResource(R.drawable.ic_volume_off);
            }
        }
        
        Log.d(TAG, "Voice guidance toggled: " + isVoiceEnabled);
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void registerLocationPermissionLauncher() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (fineLocationGranted != null && fineLocationGranted) {
                        getCurrentLocation();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        getCurrentLocation();
                    } else {
                        Toast.makeText(this, "Location permission needed for navigation", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop GPS navigation
        stopGpsNavigation();
        
        // Stop location updates
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
        
        // Shutdown executor service
        if (executorService != null) {
            executorService.shutdown();
        }
        
        // Shutdown TextToSpeech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        
        Log.d(TAG, "GoogleNavigationActivity destroyed and cleaned up");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause location updates to save battery
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume location updates if needed
        if (!locationUpdatesActive) {
            getCurrentLocation();
        }
    }
    
    private void fetchRoute(LatLng origin, LatLng destination) {
        Log.d(TAG, "Fetching route from " + origin + " to " + destination);
        
        executorService.execute(() -> {
            try {
                String url = buildDirectionsUrl(origin, destination);
                String response = makeDirectionsRequest(url);
                parseDirectionsResponse(response);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching route", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error fetching route: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private String buildDirectionsUrl(LatLng origin, LatLng destination) {
        String apiKey = getString(R.string.google_maps_key);
        String originStr = origin.latitude + "," + origin.longitude;
        String destinationStr = destination.latitude + "," + destination.longitude;
        
        return "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + originStr +
                "&destination=" + destinationStr +
                "&mode=driving" +
                "&key=" + apiKey;
    }
    
    private String makeDirectionsRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        InputStream inputStream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        
        reader.close();
        inputStream.close();
        connection.disconnect();
        
        return response.toString();
    }
    
    private void parseDirectionsResponse(String response) {
        try {
            Log.d(TAG, "Directions API Response: " + response);
            
            JSONObject jsonResponse = new JSONObject(response);
            
            // Check for errors first
            if (jsonResponse.has("error_message")) {
                String errorMessage = jsonResponse.getString("error_message");
                Log.e(TAG, "Google Directions API Error: " + errorMessage);
                runOnUiThread(() -> {
                    Toast.makeText(this, "API Error: " + errorMessage, Toast.LENGTH_LONG).show();
                });
                return;
            }
            
            // Check status
            String status = jsonResponse.getString("status");
            Log.d(TAG, "API Status: " + status);
            
            if (!status.equals("OK")) {
                Log.e(TAG, "API returned non-OK status: " + status);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Route request failed: " + status, Toast.LENGTH_LONG).show();
                });
                return;
            }
            
            JSONArray routes = jsonResponse.getJSONArray("routes");
            
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                String encodedPolyline = overviewPolyline.getString("points");
                
                // Extract turn-by-turn navigation steps
                JSONArray legs = route.getJSONArray("legs");
                if (legs.length() > 0) {
                    JSONObject leg = legs.getJSONObject(0);
                    JSONArray steps = leg.getJSONArray("steps");
                    
                    navigationSteps.clear();
                    for (int i = 0; i < steps.length(); i++) {
                        JSONObject step = steps.getJSONObject(i);
                        String instruction = step.getString("html_instructions");
                        // Clean HTML tags from instruction
                        instruction = instruction.replaceAll("<[^>]*>", "");
                        navigationSteps.add(instruction);
                    }
                    
                    Log.d(TAG, "Extracted " + navigationSteps.size() + " navigation steps");
                }
                
                // Decode polyline
                List<LatLng> decodedPoints = decodePolyline(encodedPolyline);
                
                // Draw route on UI thread
                runOnUiThread(() -> {
                    drawRoute(decodedPoints);
                    startNavigationGuidance();
                });
                
                Log.d(TAG, "Route fetched successfully with " + decodedPoints.size() + " points");
            } else {
                Log.e(TAG, "No routes found in response");
                runOnUiThread(() -> {
                    Toast.makeText(this, "No route found", Toast.LENGTH_SHORT).show();
                });
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing directions response", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Error parsing route data", Toast.LENGTH_SHORT).show();
            });
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
            
            LatLng p = new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)));
            poly.add(p);
        }
        
        return poly;
    }
    
    private void drawRoute(List<LatLng> points) {
        if (googleMap == null || points.isEmpty()) {
            return;
        }
        
        // Clear existing route
        if (currentRoute != null) {
            currentRoute.remove();
        }
        
        // Draw new route with Google Maps-like styling
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(points)
                .width(12) // Thicker line like Google Maps
                .color(0xFF1976D2) // Blue color like Google Maps
                .geodesic(true)
                .zIndex(1); // Higher z-index to appear above other elements
        
        currentRoute = googleMap.addPolyline(polylineOptions);
        routePoints = new ArrayList<>(points);
        
        // Store route polyline for real-time movement tracking
        routePolyline = new ArrayList<>(points);
        currentRouteIndex = 0;
        isNavigating = true;
        
        // Fit camera to show entire route like Google Maps
        if (points.size() > 1) {
            com.google.android.gms.maps.model.LatLngBounds.Builder builder = new com.google.android.gms.maps.model.LatLngBounds.Builder();
            for (LatLng point : points) {
                builder.include(point);
            }
            com.google.android.gms.maps.model.LatLngBounds bounds = builder.build();
            
            // Add padding to the bounds like Google Maps
            int padding = 100; // pixels
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        }
        
        Log.d(TAG, "Route drawn with " + points.size() + " points");
        Toast.makeText(this, "🗺️ Route loaded successfully!", Toast.LENGTH_SHORT).show();
    }
    
    private void startNavigationGuidance() {
        if (!navigationSteps.isEmpty() && isEnhancedNavigationActive) {
            currentStepIndex = 0;
            // Only announce first instruction, don't auto-advance
            announceCurrentInstruction();
            
            // Start real-time movement simulation
            if (isNavigating && !routePolyline.isEmpty()) {
                simulateMovementAlongRoute();
            }
            
            Log.d(TAG, "Navigation guidance started with " + navigationSteps.size() + " steps");
        }
    }
    
    private void announceCurrentInstruction() {
        if (currentStepIndex < navigationSteps.size()) {
            String instruction = navigationSteps.get(currentStepIndex);
            
            // Update UI with current instruction
            if (tvCurrentInstruction != null) {
                tvCurrentInstruction.setText(instruction);
            }
            
            // Announce via voice if enabled and navigation is active
            if (isVoiceEnabled && textToSpeech != null && isEnhancedNavigationActive) {
                textToSpeech.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null);
            }
            
            Log.d(TAG, "Announced instruction " + (currentStepIndex + 1) + ": " + instruction);
        }
    }
    
    private void moveToNextInstruction() {
        if (currentStepIndex < navigationSteps.size() - 1) {
            currentStepIndex++;
            announceCurrentInstruction();
        } else {
            // Navigation complete
            announceArrival();
        }
    }
    
    private void announceArrival() {
        String arrivalMessage = "You have arrived at the emergency location";
        
        if (tvCurrentInstruction != null) {
            tvCurrentInstruction.setText(arrivalMessage);
        }
        
        if (isVoiceEnabled && textToSpeech != null) {
            textToSpeech.speak(arrivalMessage, TextToSpeech.QUEUE_FLUSH, null, null);
        }
        
        Toast.makeText(this, "🎯 Arrived at emergency location!", Toast.LENGTH_LONG).show();
        Log.d(TAG, "Navigation completed - arrived at destination");
    }
    
    private void speakInstruction(String instruction) {
        if (isVoiceEnabled && textToSpeech != null) {
            textToSpeech.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
    
    // Real-time movement tracking methods
    private LatLng getPositionAlongRoute(LatLng currentLocation) {
        if (routePolyline.isEmpty()) {
            return currentLocation;
        }
        
        // Find the closest point on the route
        LatLng closestPoint = routePolyline.get(0);
        double minDistance = Double.MAX_VALUE;
        int closestIndex = 0;
        
        for (int i = 0; i < routePolyline.size(); i++) {
            LatLng routePoint = routePolyline.get(i);
            double distance = calculateDistance(currentLocation, routePoint);
            
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = routePoint;
                closestIndex = i;
            }
        }
        
        // Move forward along the route based on current progress
        int targetIndex = Math.min(closestIndex + 5, routePolyline.size() - 1);
        return routePolyline.get(targetIndex);
    }
    
    private float calculateBearing(LatLng position) {
        if (lastKnownLocation == null) {
            return 0f;
        }
        
        double lat1 = Math.toRadians(lastKnownLocation.getLatitude());
        double lat2 = Math.toRadians(position.latitude);
        double deltaLng = Math.toRadians(position.longitude - lastKnownLocation.getLongitude());
        
        double y = Math.sin(deltaLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLng);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }
    
    private double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        );
        return results[0];
    }
    
    // Simulate movement along route for testing
    private void simulateMovementAlongRoute() {
        if (!isNavigating || routePolyline.isEmpty()) {
            return;
        }
        
        // Move the rescuer marker along the route
        if (currentRouteIndex < routePolyline.size() - 1) {
            currentRouteIndex++;
            LatLng newPosition = routePolyline.get(currentRouteIndex);
            
            // Update current location to simulate movement
            currentLocation = newPosition;
            updateRescuerMarker();
            
            // Update camera to follow the rescuer
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPosition, 16f));
            }
            
            // Continue movement after a delay
            new android.os.Handler().postDelayed(this::simulateMovementAlongRoute, 2000); // 2 seconds
        }
    }
    
    // GPS Navigation setup
    private void setupGpsNavigation() {
        // Create high-precision location request for GPS navigation
        gpsLocationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) // 1 second updates
                .setMinUpdateIntervalMillis(500) // Minimum 500ms between updates
                .setMaxUpdateDelayMillis(2000) // Maximum 2 second delay
                .setWaitForAccurateLocation(false)
                .build();
        
        // Create GPS location callback
        gpsLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null && isGpsNavigationActive) {
                        updateGpsNavigation(location);
                    }
                }
            }
        };
        
        Log.d(TAG, "GPS Navigation setup completed");
    }
    
    // Start GPS navigation
    private void startGpsNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isGpsNavigationActive = true;
            startTime = System.currentTimeMillis();
            totalDistance = 0.0;
            
            // Start high-precision location updates
            fusedLocationClient.requestLocationUpdates(gpsLocationRequest, gpsLocationCallback, null);
            
            Log.d(TAG, "GPS Navigation started with high-precision location updates");
            Toast.makeText(this, "🛰️ GPS Navigation activated!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "❌ Location permission required for GPS navigation", Toast.LENGTH_SHORT).show();
        }
    }
    
    // Stop GPS navigation
    private void stopGpsNavigation() {
        isGpsNavigationActive = false;
        if (gpsLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(gpsLocationCallback);
        }
        Log.d(TAG, "GPS Navigation stopped");
    }
    
    // Update GPS navigation with new location
    private void updateGpsNavigation(Location location) {
        if (lastKnownLocation != null) {
            // Calculate distance traveled
            float distance = lastKnownLocation.distanceTo(location);
            totalDistance += distance;
            
            // Calculate speed in km/h
            long timeDiff = System.currentTimeMillis() - startTime;
            if (timeDiff > 0) {
                currentSpeed = (float) (totalDistance / (timeDiff / 1000.0)) * 3.6f; // Convert m/s to km/h
            }
        }
        
        // Update current location
        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        lastKnownLocation = location;
        
        // Update UI with GPS data
        updateGpsUI();
        
        // Update rescuer marker with real GPS position
        updateRescuerMarker();
        
        // Update camera to follow GPS position
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
        }
        
        Log.d(TAG, "GPS Update - Speed: " + currentSpeed + " km/h, Distance: " + totalDistance + " m");
    }
    
    // Update GPS UI elements
    private void updateGpsUI() {
        runOnUiThread(() -> {
            // Update GPS status
            if (tvGpsStatus != null) {
                if (isGpsNavigationActive) {
                    tvGpsStatus.setText("🛰️ GPS Active");
                } else {
                    tvGpsStatus.setText("🛰️ GPS");
                }
            }
            
            // Update speed display
            if (tvCurrentSpeed != null) {
                tvCurrentSpeed.setText(String.format("%.0f km/h", currentSpeed));
            }
            
            // Update distance traveled
            if (tvRemainingDistance != null) {
                tvRemainingDistance.setText(String.format("%.1f km traveled", totalDistance / 1000.0));
            }
            
            // Update estimated time based on current speed
            if (emergencyLocation != null && currentLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                    currentLocation.latitude, currentLocation.longitude,
                    emergencyLocation.latitude, emergencyLocation.longitude,
                    results
                );
                
                double remainingDistance = results[0];
                double estimatedTime = 0;
                
                if (currentSpeed > 0) {
                    estimatedTime = (remainingDistance / 1000.0) / currentSpeed; // hours
                }
                
                if (tvEstimatedTime != null) {
                    if (estimatedTime > 0) {
                        int hours = (int) estimatedTime;
                        int minutes = (int) ((estimatedTime - hours) * 60);
                        tvEstimatedTime.setText(String.format("%d:%02d", hours, minutes));
                    } else {
                        tvEstimatedTime.setText("Calculating...");
                    }
                }
            }
        });
    }
}
