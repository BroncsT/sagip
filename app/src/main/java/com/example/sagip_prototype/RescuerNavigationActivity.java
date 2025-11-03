package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RescuerNavigationActivity extends BaseRescuerActivity implements OnMapReadyCallback {

    private static final String TAG = "RescuerNavigation";
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    
    // Emergency notification system variables
    private ListenerRegistration emergencyListener;
    private String userId;
    private String userType;
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
    private Polyline routePolyline = null;
    private boolean routeDisplayed = false;

    // UI Elements
    private LinearLayout emergencyInfoCard;
    private LinearLayout topNavigationBanner;
    private LinearLayout bottomNavigationBar;
    private LinearLayout speedIndicator;
    private LinearLayout mapControls;
    private TextView tvEmergencyTitle;
    private TextView tvEmergencyAddress;
    private TextView tvDistanceTime;
    private TextView tvNavigationStatus;
    private TextView tvCurrentInstruction;
    private TextView tvNextInstruction;
    private TextView tvStepCounter;
    private TextView tvRemainingDistance;
    private TextView tvDestinationName;
    private TextView tvEstimatedTime;
    private TextView tvRemainingDistanceBottom;
    private TextView tvArrivalTime;
    private TextView tvCurrentSpeed;
    private Button btnStartNavigation;
    private Button btnStopNavigation;
    private Button btnCallSenior;
    private Button btnOpenExternalMaps;
    private Button btnArrived;
    private Button btnToggleVoice;
    private Button btnNextStep;
    private Button btnPreviousStep;
    private ImageButton btnBack;
    private ImageButton btnCancelNavigation;
    private ImageButton btnRouteOptions;
    private ImageButton btnCompass;
    private ImageButton btnZoomIn;
    private ImageButton btnAudioToggle;

    // Navigation state
    private boolean isNavigating = false;
    private String estimatedDistance = "";
    private String estimatedTime = "";
    private ExecutorService executorService;

    // Turn-by-turn navigation
    private List<NavigationStep> navigationSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    private NavigationStep currentStep = null;
    private TextToSpeech textToSpeech;
    private boolean voiceEnabled = true;
    private Handler navigationHandler = new Handler();
    private Runnable navigationUpdateRunnable;
    private boolean hasAutomaticallyMarkedArrived = false;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Google Directions API
    private static final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_navigation);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Initialize emergency notification system
        initializeEmergencyNotificationSystem();

        // Initialize UI
        initializeUI();

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
        }

        // Set emergency location
        if (emergencyLat != 0.0 && emergencyLong != 0.0) {
            emergencyLocation = new LatLng(emergencyLat, emergencyLong);
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newSingleThreadExecutor();

        // Register permission launcher
        registerLocationPermissionLauncher();

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void initializeUI() {
        // Initialize layout containers
        emergencyInfoCard = findViewById(R.id.emergencyInfoCard);
        topNavigationBanner = findViewById(R.id.topNavigationBanner);
        bottomNavigationBar = findViewById(R.id.bottomNavigationBar);
        speedIndicator = findViewById(R.id.speedIndicator);
        mapControls = findViewById(R.id.mapControls);

        // Initialize text views
        tvEmergencyTitle = findViewById(R.id.tvEmergencyTitle);
        tvEmergencyAddress = findViewById(R.id.tvEmergencyAddress);
        tvDistanceTime = findViewById(R.id.tvDistanceTime);
        tvNavigationStatus = findViewById(R.id.tvNavigationStatus);
        tvCurrentInstruction = findViewById(R.id.tvCurrentInstruction);
        tvNextInstruction = findViewById(R.id.tvNextInstruction);
        tvStepCounter = findViewById(R.id.tvStepCounter);
        tvRemainingDistance = findViewById(R.id.tvRemainingDistance);
        tvDestinationName = findViewById(R.id.tvDestinationName);
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        tvRemainingDistanceBottom = findViewById(R.id.tvRemainingDistanceBottom);
        tvArrivalTime = findViewById(R.id.tvArrivalTime);
        tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed);

        // Initialize buttons
        btnStartNavigation = findViewById(R.id.btnStartNavigation);
        btnStopNavigation = findViewById(R.id.btnStopNavigation);
        btnCallSenior = findViewById(R.id.btnCallSenior);
        btnOpenExternalMaps = findViewById(R.id.btnOpenExternalMaps);
        btnArrived = findViewById(R.id.btnArrived);
        btnToggleVoice = findViewById(R.id.btnToggleVoice);
        btnNextStep = findViewById(R.id.btnNextStep);
        btnPreviousStep = findViewById(R.id.btnPreviousStep);
        btnBack = findViewById(R.id.btnBack);
        btnCancelNavigation = findViewById(R.id.btnCancelNavigation);
        btnRouteOptions = findViewById(R.id.btnRouteOptions);
        btnCompass = findViewById(R.id.btnCompass);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnAudioToggle = findViewById(R.id.btnAudioToggle);

        // Set click listeners
        btnStartNavigation.setOnClickListener(v -> startNavigation());
        btnStopNavigation.setOnClickListener(v -> stopNavigation());
        btnCallSenior.setOnClickListener(v -> callSenior());
        btnOpenExternalMaps.setOnClickListener(v -> openExternalMaps());
        btnArrived.setOnClickListener(v -> markArrived());
        btnToggleVoice.setOnClickListener(v -> toggleVoice());
        btnNextStep.setOnClickListener(v -> nextStep());
        btnPreviousStep.setOnClickListener(v -> previousStep());
        btnBack.setOnClickListener(v -> finish());
        btnCancelNavigation.setOnClickListener(v -> stopNavigation());
        btnRouteOptions.setOnClickListener(v -> showRouteOptions());
        btnCompass.setOnClickListener(v -> resetMapOrientation());
        btnZoomIn.setOnClickListener(v -> zoomIn());
        btnAudioToggle.setOnClickListener(v -> toggleVoice());

        // Initialize Text-to-Speech
        initializeTextToSpeech();

        // Update emergency info
        updateEmergencyInfo();
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported for TTS");
                    voiceEnabled = false;
                } else {
                    textToSpeech.setSpeechRate(0.8f);
                    textToSpeech.setPitch(1.0f);
                }
            } else {
                Log.e(TAG, "TTS initialization failed");
                voiceEnabled = false;
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

        if (tvNavigationStatus != null) {
            tvNavigationStatus.setText(isNavigating ? "🗺️ Navigating..." : "📍 Ready to navigate");
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
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(true);
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            googleMap.getUiSettings().setCompassEnabled(true);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException enabling location features", e);
        }

        // Display emergency location
        if (emergencyLocation != null) {
            displayEmergencyLocation();
        }

        // Get current location and automatically show route
        getCurrentLocation();
        
        // Auto-display route after a short delay to ensure location is available
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (currentLocation != null && emergencyLocation != null) {
                showRoute();
                Toast.makeText(this, getString(R.string.route_to_emergency_displayed), Toast.LENGTH_SHORT).show();
            }
        }, 2000); // 2 second delay
        
        // Start arrival monitoring immediately (not just when navigating)
        startArrivalMonitoring();
    }

    private void displayEmergencyLocation() {
        if (googleMap == null || emergencyLocation == null) {
            return;
        }

        // Add emergency marker
        MarkerOptions markerOptions = new MarkerOptions()
                .position(emergencyLocation)
                .title("🚨 Emergency Location")
                .snippet("👤 " + seniorName + "\n📍 " + emergencyAddress);

        googleMap.addMarker(markerOptions);
        
        // Move camera to emergency location
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 15f));
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
            LocationRequest locationRequest = new LocationRequest.Builder(5000) // Update every 5 seconds
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

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
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
    }

    private void startNavigation() {
        if (currentLocation == null || emergencyLocation == null) {
            Toast.makeText(this, getString(R.string.location_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        isNavigating = true;
        
        // Show route with enhanced visualization
        showRoute();
        
        // Switch to Google Maps-style UI
        switchToNavigationUI();
        
        updateEmergencyInfo();
        
        // Start navigation monitoring
        startNavigationMonitoring();
        
        // Show enhanced route information
        showRouteInformation();
        
        Toast.makeText(this, getString(R.string.turn_by_turn_navigation_started), Toast.LENGTH_LONG).show();
    }

    private void showRouteInformation() {
        if (estimatedDistance != null && estimatedTime != null) {
            String routeInfo = "📍 Route: " + estimatedDistance + " • ⏱️ " + estimatedTime;
            Toast.makeText(this, routeInfo, Toast.LENGTH_LONG).show();
        }
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

    private void updateTopNavigationBanner() {
        if (tvRemainingDistance != null && !estimatedDistance.isEmpty()) {
            tvRemainingDistance.setText(estimatedDistance);
        }
        
        if (tvDestinationName != null && seniorName != null) {
            tvDestinationName.setText("Emergency: " + seniorName);
        }
        
        if (tvCurrentInstruction != null && currentStep != null) {
            tvCurrentInstruction.setText(currentStep.getInstruction());
        }
    }

    private void stopNavigation() {
        isNavigating = false;
        
        // Clear route
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
            routeDisplayed = false;
        }
        
        // Stop navigation monitoring
        stopNavigationMonitoring();
        
        // Switch back to emergency info UI
        switchToEmergencyInfoUI();
        
        updateEmergencyInfo();
        
        Toast.makeText(this, getString(R.string.navigation_stopped), Toast.LENGTH_SHORT).show();
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

    private void startNavigationMonitoring() {
        navigationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentLocation != null) {
                    // Check if we're close to destination (regardless of navigation state)
                    if (emergencyLocation != null) {
                        float[] results = new float[1];
                        Location.distanceBetween(
                            currentLocation.latitude, currentLocation.longitude,
                            emergencyLocation.latitude, emergencyLocation.longitude,
                            results
                        );
                        
                        float distanceToDestination = results[0];
                        Log.d(TAG, "Distance to destination: " + distanceToDestination + " meters");
                        
                        // Show proximity warnings and enable manual arrival button
                        if (distanceToDestination < 200 && distanceToDestination > 50) {
                            // Close to destination (200m - 50m)
                            if (tvNavigationStatus != null) {
                                tvNavigationStatus.setText("📍 Approaching destination (" + String.format("%.0f", distanceToDestination) + "m)");
                            }
                            // Show manual arrival button when close
                            if (btnArrived != null) {
                                btnArrived.setVisibility(View.VISIBLE);
                                btnArrived.setText("✅ Mark as Arrived (" + String.format("%.0f", distanceToDestination) + "m)");
                            }
                        } else if (distanceToDestination < 50 && distanceToDestination > 20) {
                            // Very close to destination (50m - 20m)
                            if (tvNavigationStatus != null) {
                                tvNavigationStatus.setText("🚨 Very close to destination (" + String.format("%.0f", distanceToDestination) + "m)");
                            }
                            // Show manual arrival button when very close
                            if (btnArrived != null) {
                                btnArrived.setVisibility(View.VISIBLE);
                                btnArrived.setText("✅ Mark as Arrived (" + String.format("%.0f", distanceToDestination) + "m)");
                            }
                        } else if (distanceToDestination > 200) {
                            // Far from destination, hide manual arrival button
                            if (btnArrived != null) {
                                btnArrived.setVisibility(View.GONE);
                            }
                        }
                        
                        // Check if we're within 50 meters of destination (increased from 30)
                        if (distanceToDestination < 50 && !hasAutomaticallyMarkedArrived) {
                            Log.d(TAG, "Arrived at destination! Distance: " + distanceToDestination + " meters");
                            
                            // We're at the destination
                            if (voiceEnabled && textToSpeech != null) {
                                textToSpeech.speak("You have arrived at your destination", TextToSpeech.QUEUE_FLUSH, null, null);
                            }
                            
                            // Show arrival notification and enable arrived button instead of automatic marking
                            showArrivalNotification();
                            
                            // Show the arrived button instead of automatically marking as arrived
                            if (btnArrived != null) {
                                btnArrived.setVisibility(View.VISIBLE);
                                btnArrived.setText("✅ Mark as Arrived (" + String.format("%.0f", distanceToDestination) + "m)");
                            }
                        }
                    }
                    
                    // Navigation-specific logic (only when actively navigating)
                    if (isNavigating && currentStep != null) {
                        // Check if we're close to the current step's end location
                        float[] results = new float[1];
                        Location.distanceBetween(
                            currentLocation.latitude, currentLocation.longitude,
                            currentStep.getEndLocation().latitude, currentStep.getEndLocation().longitude,
                            results
                        );
                        
                        float distanceToStepEnd = results[0];
                        
                        // If we're within 50 meters of the step end, move to next step
                        if (distanceToStepEnd < 50 && currentStepIndex + 1 < navigationSteps.size()) {
                            nextStep();
                        }
                    }
                }
                
                // Schedule next update (always run, not just when navigating)
                navigationHandler.postDelayed(this, 3000); // Check every 3 seconds for better responsiveness
            }
        };
        
        navigationHandler.post(navigationUpdateRunnable);
    }

    private void showArrivalNotification() {
        // Show a prominent arrival notification
        Toast.makeText(this, getString(R.string.arrived_at_emergency_location), Toast.LENGTH_LONG).show();
        
        // Show a dialog for confirmation
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.arrived_at_destination_title));
        builder.setMessage(getString(R.string.arrived_at_destination_message));
        builder.setIcon(android.R.drawable.ic_dialog_info);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void startArrivalMonitoring() {
        // Start monitoring for arrival immediately when activity loads
        Log.d(TAG, "Starting arrival monitoring");
        startNavigationMonitoring(); // This now handles both navigation and arrival monitoring
    }

    private void stopNavigationMonitoring() {
        if (navigationUpdateRunnable != null) {
            navigationHandler.removeCallbacks(navigationUpdateRunnable);
        }
    }

    private void showRoute() {
        if (currentLocation == null || emergencyLocation == null) {
            return;
        }

        // Get directions using Google Directions API
        String directionsUrl = buildDirectionsUrl(currentLocation, emergencyLocation);
        executeDirectionsRequest(directionsUrl);
    }

    private String buildDirectionsUrl(LatLng origin, LatLng destination) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + destination.latitude + "," + destination.longitude;
        String parameters = str_origin + "&" + str_dest + "&key=" + getString(R.string.google_maps_key);
        String output = "json";
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
    }

    private void executeDirectionsRequest(String directionsUrl) {
        executorService.execute(() -> {
            try {
                String jsonResponse = makeDirectionsRequest(directionsUrl);
                if (jsonResponse != null) {
                    runOnUiThread(() -> parseDirectionsResponse(jsonResponse));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in executeDirectionsRequest", e);
            }
        });
    }

    private String makeDirectionsRequest(String directionsUrl) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        
        try {
            URL url = new URL(directionsUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                return response.toString();
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error making directions request", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private void parseDirectionsResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routes = jsonObject.getJSONArray("routes");
            
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                
                // Get distance and duration
                JSONArray legs = route.getJSONArray("legs");
                if (legs.length() > 0) {
                    JSONObject leg = legs.getJSONObject(0);
                    
                    JSONObject distance = leg.getJSONObject("distance");
                    JSONObject duration = leg.getJSONObject("duration");
                    
                    estimatedDistance = distance.getString("text");
                    estimatedTime = duration.getString("text");
                    
                    updateEmergencyInfo();
                    
                    // Parse turn-by-turn steps
                    parseNavigationSteps(leg);
                }
                
                // Get polyline points
                JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                String encodedPolyline = overviewPolyline.getString("points");
                
                // Decode and display the route
                List<LatLng> routePoints = decodePolyline(encodedPolyline);
                displayRoute(routePoints);
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing directions response", e);
        }
    }

    private void parseNavigationSteps(JSONObject leg) {
        try {
            navigationSteps.clear();
            JSONArray steps = leg.getJSONArray("steps");
            
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                
                String instruction = step.getString("html_instructions");
                // Clean HTML tags from instruction
                instruction = instruction.replaceAll("<[^>]*>", "");
                
                JSONObject distance = step.getJSONObject("distance");
                JSONObject duration = step.getJSONObject("duration");
                
                String maneuver = "";
                if (step.has("maneuver")) {
                    maneuver = step.getString("maneuver");
                }
                
                // Get start and end locations
                JSONObject startLocation = step.getJSONObject("start_location");
                JSONObject endLocation = step.getJSONObject("end_location");
                
                LatLng startLatLng = new LatLng(startLocation.getDouble("lat"), startLocation.getDouble("lng"));
                LatLng endLatLng = new LatLng(endLocation.getDouble("lat"), endLocation.getDouble("lng"));
                
                NavigationStep navStep = new NavigationStep(
                    instruction,
                    distance.getString("text"),
                    duration.getString("text"),
                    maneuver,
                    startLatLng,
                    endLatLng,
                    i + 1
                );
                
                navigationSteps.add(navStep);
            }
            
            Log.d(TAG, "Parsed " + navigationSteps.size() + " navigation steps");
            
            // Start with first step
            if (!navigationSteps.isEmpty()) {
                currentStepIndex = 0;
                currentStep = navigationSteps.get(0);
                updateNavigationUI();
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing navigation steps", e);
        }
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> polyline = new ArrayList<>();
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

            LatLng position = new LatLng((lat / 1E5), (lng / 1E5));
            polyline.add(position);
        }

        return polyline;
    }

    private void displayRoute(List<LatLng> routePoints) {
        if (googleMap == null || routePoints == null || routePoints.isEmpty()) {
            return;
        }

        // Clear existing route
        if (routePolyline != null) {
            routePolyline.remove();
        }

        // Add new route polyline with enhanced styling
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .width(12) // Slightly thicker for better visibility
                .color(0xFF1976D2) // Material Design Blue
                .geodesic(true)
                .pattern(null); // Solid line

        routePolyline = googleMap.addPolyline(polylineOptions);
        routeDisplayed = true;

        Log.d(TAG, "Route displayed with " + routePoints.size() + " points");

        // Add start and end markers for better visualization
        addRouteMarkers();

        // Adjust camera to show the entire route with padding
        if (currentLocation != null && emergencyLocation != null) {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            boundsBuilder.include(currentLocation);
            boundsBuilder.include(emergencyLocation);
            
            // Include all route points for better bounds calculation
            for (LatLng point : routePoints) {
                boundsBuilder.include(point);
            }
            
            try {
                LatLngBounds bounds = boundsBuilder.build();
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150)); // More padding
            } catch (Exception e) {
                Log.e(TAG, "Error adjusting camera bounds", e);
                // Fallback to simple zoom
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(emergencyLocation, 15f));
            }
        }
    }

    private void addRouteMarkers() {
        if (googleMap == null) return;

        // Add start marker (current location)
        if (currentLocation != null) {
            MarkerOptions startMarker = new MarkerOptions()
                    .position(currentLocation)
                    .title("🚗 Your Location")
                    .snippet("Starting point");
            googleMap.addMarker(startMarker);
        }

        // Add destination marker (emergency location) - this should already exist
        if (emergencyLocation != null) {
            // Check if emergency marker already exists, if not add it
            MarkerOptions emergencyMarker = new MarkerOptions()
                    .position(emergencyLocation)
                    .title("🚨 Emergency Location")
                    .snippet("👤 " + seniorName + "\n📍 " + emergencyAddress);
            googleMap.addMarker(emergencyMarker);
        }
    }

    private void callSenior() {
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            startActivity(callIntent);
        } else {
            Toast.makeText(this, getString(R.string.no_phone_number_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternalMaps() {
        if (emergencyLocation == null) {
            Toast.makeText(this, getString(R.string.emergency_location_not_available), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, getString(R.string.opening_google_maps_navigation), Toast.LENGTH_LONG).show();
            } else {
                // Fallback to web-based Google Maps
                String webMapsUri = String.format(Locale.getDefault(), "https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving", 
                    emergencyLocation.latitude, emergencyLocation.longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webMapsUri));
                startActivity(webIntent);
                Toast.makeText(this, getString(R.string.opening_web_based_navigation), Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening external Google Maps navigation", e);
            Toast.makeText(this, getString(R.string.error_opening_navigation), Toast.LENGTH_SHORT).show();
        }
    }

    private void markArrived() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mark_as_arrived_title));
        builder.setMessage(getString(R.string.mark_as_arrived_message));
        
        builder.setPositiveButton("Yes, Arrived", (dialog, which) -> {
            // Prevent multiple automatic arrivals
            hasAutomaticallyMarkedArrived = true;
            
            // Update help request status
            if (helpRequestId != null && !helpRequestId.isEmpty()) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "rescuer_arrived");
                updates.put("rescuerArrivedTime", System.currentTimeMillis());
                updates.put("rescuerArrivedBy", getCurrentRescuerName());
                updates.put("rescuerTeam", getCurrentRescuerTeam());
                
                db.collection("Sagip")
                    .document("helpRequests")
                    .collection("activeRequests")
                    .document(helpRequestId)
                    .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Send arrival notification to senior
                    sendArrivalNotificationToSenior();
                    
                    // Show arrival confirmation popup to rescuer
                    showArrivalConfirmationPopup();
                })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating help request status", e);
                        Toast.makeText(this, getString(R.string.error_updating_status), Toast.LENGTH_SHORT).show();
                    });
            } else {
                Toast.makeText(this, getString(R.string.marked_as_arrived), Toast.LENGTH_LONG).show();
                finish();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void automaticallyMarkArrived() {
        // Prevent multiple automatic arrivals
        if (hasAutomaticallyMarkedArrived) {
            return;
        }
        hasAutomaticallyMarkedArrived = true;
        
        Log.d(TAG, "Automatically marking as arrived and sending notification to senior");
        
        // Update help request status
        if (helpRequestId != null && !helpRequestId.isEmpty()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "rescuer_arrived");
            updates.put("rescuerArrivedTime", System.currentTimeMillis());
            updates.put("rescuerArrivedBy", getCurrentRescuerName());
            updates.put("rescuerTeam", getCurrentRescuerTeam());
            
            db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .document(helpRequestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Send arrival notification to senior
                    sendArrivalNotificationToSenior();
                    
                    // Show arrival confirmation popup to rescuer
                    showArrivalConfirmationPopup();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error automatically updating help request status", e);
                    Toast.makeText(this, getString(R.string.error_updating_status), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, getString(R.string.automatically_marked_as_arrived), Toast.LENGTH_LONG).show();
            stopNavigation();
            
            // Close the rescuer navigation activity after a brief delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                finish();
            }, 2000); // 2 second delay to show the success message
        }
    }

    private void updateRescuerLocationInFirebase(Location location) {
        if (helpRequestId == null || helpRequestId.isEmpty()) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        Map<String, Object> rescuerLocation = new HashMap<>();
        rescuerLocation.put("latitude", location.getLatitude());
        rescuerLocation.put("longitude", location.getLongitude());
        rescuerLocation.put("timestamp", System.currentTimeMillis());
        rescuerLocation.put("rescuerId", currentUser.getUid());
        rescuerLocation.put("rescuerName", getCurrentRescuerName());
        rescuerLocation.put("rescuerTeam", getCurrentRescuerTeam());

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

    private String getCurrentRescuerTeam() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Get team information from Firestore
            db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String teamName = documentSnapshot.getString("rescuegroup");
                        if (teamName != null && !teamName.isEmpty()) {
                            Log.d(TAG, "Rescuer team: " + teamName);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting rescuer team info", e);
                });
        }
        return null; // This will be updated asynchronously
    }

    private void sendArrivalNotificationToSenior() {
        if (helpRequestId == null || helpRequestId.isEmpty()) {
            return;
        }

        // Get help request details to find senior information
        db.collection("Sagip")
            .document("helpRequests")
            .collection("activeRequests")
            .document(helpRequestId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String seniorUid = documentSnapshot.getString("seniorUid");
                    String seniorName = documentSnapshot.getString("seniorName");
                    String rescuerName = getCurrentRescuerName();
                    String rescuerTeam = getCurrentRescuerTeam();
                    
                    // Create arrival notification for senior
                    Map<String, Object> arrivalNotification = new HashMap<>();
                    arrivalNotification.put("type", "rescuer_arrived");
                    arrivalNotification.put("title", "🚑 Rescuer Has Arrived!");
                    arrivalNotification.put("message", rescuerName + " from " + (rescuerTeam != null ? rescuerTeam : "Rescue Team") + " has arrived at your location");
                    arrivalNotification.put("helpRequestId", helpRequestId);
                    arrivalNotification.put("rescuerName", rescuerName);
                    arrivalNotification.put("rescuerTeam", rescuerTeam);
                    arrivalNotification.put("timestamp", System.currentTimeMillis());
                    arrivalNotification.put("isActive", true);
                    
                    // Send notification to senior's notification collection
                    db.collection("Sagip")
                        .document("seniorNotifications")
                        .collection("arrivalNotifications")
                        .document(helpRequestId)
                        .set(arrivalNotification)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Arrival notification sent to senior: " + seniorName);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to send arrival notification to senior", e);
                        });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error getting help request details for arrival notification", e);
            });
    }

    private void showArrivalConfirmationPopup() {
        // Create a prominent arrival confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_rescuer_arrival_title));
        builder.setMessage(getString(R.string.dialog_rescuer_arrival_message));
        builder.setIcon(android.R.drawable.ic_dialog_info);
        
        // Make the dialog prominent and non-cancelable
        builder.setCancelable(false);
        
        // Add action buttons
        builder.setPositiveButton(getString(R.string.button_acknowledged), (dialog, which) -> {
            dialog.dismiss();
            // Stop navigation
            stopNavigation();
            
            // Show success toast
            Toast.makeText(this, getString(R.string.toast_arrival_confirmed), Toast.LENGTH_LONG).show();
            
            // Close the rescuer navigation activity after a brief delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                finish();
            }, 1500); // 1.5 second delay to show the success message
        });
        
        // Add a "Call Senior" button if phone number is available
        String seniorPhone = getSeniorPhoneNumber();
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            builder.setNeutralButton(getString(R.string.button_call_senior), (dialog, which) -> {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(android.net.Uri.parse("tel:" + seniorPhone));
                startActivity(callIntent);
            });
        }
        
        AlertDialog dialog = builder.create();
        
        // Style the dialog to make it more prominent
        dialog.setOnShowListener(dialogInterface -> {
            try {
                // Make the positive button green to indicate success
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
                Log.e(TAG, "Error styling arrival confirmation dialog buttons", e);
            }
        });
        
        dialog.show();
        Log.d(TAG, "🎉 Arrival confirmation popup shown to rescuer");
    }

    private String getSeniorPhoneNumber() {
        // Try to get senior phone number from the help request
        if (helpRequestId != null && !helpRequestId.isEmpty()) {
            // This would need to be implemented to fetch from the help request document
            // For now, return null as we don't have direct access to the senior's phone
            return null;
        }
        return null;
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

    private void showRouteOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.route_options_title));
        builder.setMessage(getString(R.string.route_options_message));
        
        builder.setPositiveButton("🚀 Fastest Route", (dialog, which) -> {
            // Recalculate route with fastest option
            showRouteWithOptions("fastest");
            Toast.makeText(this, getString(R.string.fastest_route_selected), Toast.LENGTH_SHORT).show();
        });
        
        builder.setNeutralButton("📏 Shortest Route", (dialog, which) -> {
            // Recalculate route with shortest option
            showRouteWithOptions("shortest");
            Toast.makeText(this, getString(R.string.shortest_route_selected), Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("🚫 Avoid Highways", (dialog, which) -> {
            // Recalculate route avoiding highways
            showRouteWithOptions("avoid_highways");
            Toast.makeText(this, getString(R.string.route_avoiding_highways_selected), Toast.LENGTH_SHORT).show();
        });
        
        // Add a fourth option
        builder.setNeutralButton("🔄 Refresh Route", (dialog, which) -> {
            // Refresh current route
            showRoute();
            Toast.makeText(this, getString(R.string.route_refreshed), Toast.LENGTH_SHORT).show();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showRouteWithOptions(String routeType) {
        if (currentLocation == null || emergencyLocation == null) {
            return;
        }

        // Get directions using Google Directions API with specific options
        String directionsUrl = buildDirectionsUrlWithOptions(currentLocation, emergencyLocation, routeType);
        executeDirectionsRequest(directionsUrl);
    }

    private String buildDirectionsUrlWithOptions(LatLng origin, LatLng destination, String routeType) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + destination.latitude + "," + destination.longitude;
        String parameters = str_origin + "&" + str_dest;
        
        // Add route-specific options
        switch (routeType) {
            case "fastest":
                parameters += "&mode=driving&traffic_model=best_guess&departure_time=now";
                break;
            case "shortest":
                parameters += "&mode=driving&avoid=highways";
                break;
            case "avoid_highways":
                parameters += "&mode=driving&avoid=highways|tolls";
                break;
            default:
                parameters += "&mode=driving";
                break;
        }
        
        parameters += "&key=" + getString(R.string.google_maps_key);
        String output = "json";
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
    }

    private void resetMapOrientation() {
        if (googleMap != null && currentLocation != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 18f));
            Toast.makeText(this, getString(R.string.map_orientation_reset), Toast.LENGTH_SHORT).show();
        }
    }

    private void zoomIn() {
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.zoomIn());
        }
    }

    private void updateNavigationUI() {
        if (currentStep != null) {
            // Update top banner instruction
            if (tvCurrentInstruction != null) {
                tvCurrentInstruction.setText(currentStep.getInstruction());
            }
            
            // Update bottom navigation bar
            updateBottomNavigationBar();
            
            // Speak the instruction
            if (voiceEnabled && textToSpeech != null) {
                speakInstruction(currentStep.getInstruction());
            }
        }
    }

    private void updateBottomNavigationBar() {
        if (tvEstimatedTime != null && !estimatedTime.isEmpty()) {
            tvEstimatedTime.setText(estimatedTime);
        }
        
        if (tvRemainingDistanceBottom != null && currentStep != null) {
            tvRemainingDistanceBottom.setText(currentStep.getDistance());
        }
        
        if (tvArrivalTime != null) {
            // Calculate arrival time
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
            long arrivalTime = System.currentTimeMillis() + (long)(parseTimeToMinutes(estimatedTime) * 60 * 1000);
            tvArrivalTime.setText(timeFormat.format(new java.util.Date(arrivalTime)));
        }
    }

    private float parseTimeToMinutes(String timeString) {
        if (timeString == null || timeString.isEmpty()) return 0;
        
        try {
            if (timeString.contains("min")) {
                return Float.parseFloat(timeString.replaceAll("[^0-9.]", ""));
            } else if (timeString.contains("hour")) {
                return Float.parseFloat(timeString.replaceAll("[^0-9.]", "")) * 60;
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing time: " + timeString, e);
        }
        return 0;
    }

    private void speakInstruction(String instruction) {
        if (textToSpeech != null && voiceEnabled) {
            textToSpeech.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void toggleVoice() {
        voiceEnabled = !voiceEnabled;
        if (btnAudioToggle != null) {
            btnAudioToggle.setImageResource(voiceEnabled ? R.drawable.ic_volume_on : R.drawable.ic_volume_off);
        }
        Toast.makeText(this, voiceEnabled ? "Voice guidance enabled" : "Voice guidance disabled", Toast.LENGTH_SHORT).show();
    }

    private void nextStep() {
        if (currentStepIndex + 1 < navigationSteps.size()) {
            currentStepIndex++;
            currentStep = navigationSteps.get(currentStepIndex);
            updateNavigationUI();
        }
    }

    private void previousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            currentStep = navigationSteps.get(currentStepIndex);
            updateNavigationUI();
        }
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
        
        // Stop location updates
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
        
        // Stop navigation monitoring
        stopNavigationMonitoring();
        
        // Shutdown Text-to-Speech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        
        // Shutdown executor service
        if (executorService != null) {
            executorService.shutdown();
        }
        
        // Remove emergency listener
        Log.d(TAG, "🚨 RescuerNavigationActivity onDestroy - cleaning up emergency listener");
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        Log.d(TAG, "RescuerNavigationActivity destroyed and cleaned up");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause location updates to save battery
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
        
        // Stop emergency listener when activity pauses - EmergencyNotificationService will handle background
        Log.d(TAG, "🚨 RescuerNavigationActivity onPause - stopping emergency listener");
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume location updates if needed
        if (!locationUpdatesActive) {
            getCurrentLocation();
        }
        
        // Start emergency listener when activity resumes
        Log.d(TAG, "🚨 RescuerNavigationActivity onResume - starting emergency listener");
        if (emergencyListener == null) {
            startEmergencyListener();
        }
    }

    // NavigationStep class for turn-by-turn navigation
    public static class NavigationStep {
        private String instruction;
        private String distance;
        private String duration;
        private String maneuver;
        private LatLng startLocation;
        private LatLng endLocation;
        private int stepNumber;

        public NavigationStep(String instruction, String distance, String duration, String maneuver, 
                            LatLng startLocation, LatLng endLocation, int stepNumber) {
            this.instruction = instruction;
            this.distance = distance;
            this.duration = duration;
            this.maneuver = maneuver;
            this.startLocation = startLocation;
            this.endLocation = endLocation;
            this.stepNumber = stepNumber;
        }

        // Getters
        public String getInstruction() { return instruction; }
        public String getDistance() { return distance; }
        public String getDuration() { return duration; }
        public String getManeuver() { return maneuver; }
        public LatLng getStartLocation() { return startLocation; }
        public LatLng getEndLocation() { return endLocation; }
        public int getStepNumber() { return stepNumber; }

        // Get instruction with distance
        public String getFullInstruction() {
            return instruction + (distance != null && !distance.isEmpty() ? " (" + distance + ")" : "");
        }
    }
    
    // =============== EMERGENCY NOTIFICATION SYSTEM ===============
    
    /**
     * Initialize emergency notification system
     */
    private void initializeEmergencyNotificationSystem() {
        Log.d(TAG, "🚨 Initializing emergency notification system in RescuerNavigationActivity");
        
        // Get user info from preferences
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", null);
        userType = prefs.getString("user_type", null);
        
        Log.d(TAG, "🚨 User ID: " + userId + ", User Type: " + userType);
    }
    
    /**
     * Start emergency listener
     */
    private void startEmergencyListener() {
        Log.d(TAG, "🚨 Starting emergency listener in RescuerNavigationActivity...");
        
        // Check if user is a rescuer
        if (userId == null || userType == null || !userType.equals("rescuer")) {
            Log.w(TAG, "⚠️ User is not a rescuer, skipping emergency listener");
            return;
        }
        
        // Prevent duplicate listeners
        if (emergencyListener != null) {
            Log.w(TAG, "Emergency listener already exists, removing old one first");
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Listen for new emergency notifications
        emergencyListener = db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "🚨 Emergency listener failed.", e);
                        return;
                    }
                    
                    Log.d(TAG, "🚨 Emergency listener triggered in RescuerNavigationActivity - snapshots: " + (snapshots != null ? snapshots.size() : "null"));
                    
                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            Log.d(TAG, "🚨 Document change type: " + dc.getType() + " for document: " + dc.getDocument().getId());
                            
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // New emergency detected!
                                DocumentSnapshot emergency = dc.getDocument();
                                Log.d(TAG, "🚨 NEW EMERGENCY DETECTED IN RESCUER_NAVIGATION: " + emergency.getId());
                                handleNewEmergency(emergency);
                            }
                        }
                    } else {
                        Log.d(TAG, "🚨 No active emergencies found in RescuerNavigationActivity");
                    }
                });
        
        Log.d(TAG, "🚨 Emergency listener started successfully in RescuerNavigationActivity");
    }
    
    /**
     * Handle new emergency notification
     */
    private void handleNewEmergency(DocumentSnapshot emergency) {
        String title = emergency.getString("title");
        String message = emergency.getString("message");
        String seniorName = emergency.getString("seniorName");
        String seniorPhone = emergency.getString("seniorPhone");
        String locationAddress = emergency.getString("locationAddress");
        String helpRequestId = emergency.getString("helpRequestId");
        
        Log.d(TAG, "🚨🚨🚨 NEW EMERGENCY RECEIVED IN RESCUER_NAVIGATION 🚨🚨🚨");
        Log.d(TAG, "🚨 Senior: " + seniorName);
        Log.d(TAG, "🚨 Location: " + locationAddress);
        Log.d(TAG, "🚨 Help Request ID: " + helpRequestId);
        
        // Check if this rescuer has already responded to this emergency
        String respondedBy = emergency.getString("respondedBy");
        if (respondedBy != null && respondedBy.equals(userId)) {
            Log.d(TAG, "Current rescuer already responded to this emergency, skipping notification for: " + helpRequestId);
            return;
        }
        
        // Show emergency alert dialog
        showEmergencyAlert(title, message, seniorName, seniorPhone, locationAddress, helpRequestId);
    }
    
    /**
     * Show emergency alert dialog
     */
    private void showEmergencyAlert(String title, String message, String seniorName, 
                                  String seniorPhone, String locationAddress, String helpRequestId) {
        
        String fullMessage = "🚨 EMERGENCY ALERT 🚨\n\n" +
                "👤 Senior: " + seniorName + "\n" +
                "📍 Location: " + locationAddress + "\n" +
                "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not provided") + "\n\n" +
                "Please respond immediately!";
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title != null ? title : "🚨 EMERGENCY HELP REQUEST")
                .setMessage(fullMessage)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("🚑 RESPOND NOW", (dialog, which) -> {
                    // Navigate to dashboard to handle emergency
                    Intent intent = new Intent(this, Rescuer_Dashboard.class);
                    intent.putExtra("emergency_notification", true);
                    intent.putExtra("helpRequestId", helpRequestId);
                    intent.putExtra("senior_name", seniorName);
                    intent.putExtra("location", locationAddress);
                    startActivity(intent);
                    dialog.dismiss();
                })
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                .setCancelable(false); // Prevent dismissing by tapping outside
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        Log.d(TAG, "✅ Emergency alert shown in RescuerNavigationActivity for: " + seniorName);
    }
}
