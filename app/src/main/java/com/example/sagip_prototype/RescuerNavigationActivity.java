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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
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

public class RescuerNavigationActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "RescuerNavigation";
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
        mAuth = FirebaseAuth.getInstance();

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

        // Get current location
        getCurrentLocation();
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
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        isNavigating = true;
        
        // Show route
        showRoute();
        
        // Switch to Google Maps-style UI
        switchToNavigationUI();
        
        updateEmergencyInfo();
        
        // Start navigation monitoring
        startNavigationMonitoring();
        
        Toast.makeText(this, "🗺️ Turn-by-turn navigation started!", Toast.LENGTH_LONG).show();
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
        
        Toast.makeText(this, "Navigation stopped", Toast.LENGTH_SHORT).show();
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
                if (isNavigating && currentLocation != null && currentStep != null) {
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
                    
                    // Check if we're close to destination
                    if (emergencyLocation != null) {
                        Location.distanceBetween(
                            currentLocation.latitude, currentLocation.longitude,
                            emergencyLocation.latitude, emergencyLocation.longitude,
                            results
                        );
                        
                        if (results[0] < 30) {
                            // We're at the destination
                            if (voiceEnabled && textToSpeech != null) {
                                textToSpeech.speak("You have arrived at your destination", TextToSpeech.QUEUE_FLUSH, null, null);
                            }
                            Toast.makeText(RescuerNavigationActivity.this, "🏁 You have arrived at the emergency location!", Toast.LENGTH_LONG).show();
                        }
                    }
                }
                
                // Schedule next update
                if (isNavigating) {
                    navigationHandler.postDelayed(this, 5000); // Check every 5 seconds
                }
            }
        };
        
        navigationHandler.post(navigationUpdateRunnable);
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

        // Add new route polyline
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .width(10)
                .color(0xFF2196F3) // Blue color
                .geodesic(true);

        routePolyline = googleMap.addPolyline(polylineOptions);
        routeDisplayed = true;

        Log.d(TAG, "Route displayed with " + routePoints.size() + " points");

        // Adjust camera to show the entire route
        if (currentLocation != null && emergencyLocation != null) {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            boundsBuilder.include(currentLocation);
            boundsBuilder.include(emergencyLocation);
            
            try {
                LatLngBounds bounds = boundsBuilder.build();
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } catch (Exception e) {
                Log.e(TAG, "Error adjusting camera bounds", e);
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
                Map<String, Object> updates = new HashMap<>();
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

        Map<String, Object> rescuerLocation = new HashMap<>();
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
        builder.setTitle("Route Options");
        builder.setMessage("Choose your preferred route:");
        
        builder.setPositiveButton("Fastest Route", (dialog, which) -> {
            // Recalculate route with fastest option
            showRoute();
            Toast.makeText(this, "Fastest route selected", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNeutralButton("Shortest Route", (dialog, which) -> {
            // Recalculate route with shortest option
            showRoute();
            Toast.makeText(this, "Shortest route selected", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void resetMapOrientation() {
        if (googleMap != null && currentLocation != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 18f));
            Toast.makeText(this, "Map orientation reset", Toast.LENGTH_SHORT).show();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume location updates if needed
        if (!locationUpdatesActive) {
            getCurrentLocation();
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
}
