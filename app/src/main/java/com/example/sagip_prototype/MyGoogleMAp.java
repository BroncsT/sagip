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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.media.RingtoneManager;

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
import java.util.Locale;

import com.google.android.gms.maps.model.Marker;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
    private String seniorNameForTracking = "";
    private ListenerRegistration rescuerLocationListener = null;
    private ListenerRegistration helpRequestListener = null;
    private Map<String, Marker> rescuerMarkers = new HashMap<>();
    private Map<String, String> rescuerNames = new HashMap<>();
    private Map<String, String> rescuerPhones = new HashMap<>();

    // Routing variables
    private LatLng currentLocation = null;
    private LatLng destinationLocation = null;
    private Polyline currentRoute = null;
    private Polyline routePolyline = null; // Add this missing variable
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
    private Button btnCallClosestRescuer;
    private Button btnTestTracking;
    private ImageButton btnBack;

    // Distance and time estimation
    private String estimatedDistance = "";
    private String estimatedTime = "";
    private boolean isCalculatingRoute = false;
    private ExecutorService executorService;
    private boolean isDestroyed = false;
    
    // Firebase Firestore for tracking rescuers
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    
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

        // Initialize Firebase Firestore and Auth
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

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
            seniorNameForTracking = intent.getStringExtra("seniorName");

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
        btnCallClosestRescuer = findViewById(R.id.btnCallClosestRescuer);
        btnTestTracking = findViewById(R.id.btnTestTracking);
        btnBack = findViewById(R.id.btnBack);

        Log.d(TAG, "UI Elements found - emergencyInfoCard: " + (emergencyInfoCard != null) +
                ", btnNavigate: " + (btnNavigate != null) +
                ", tvDistanceTime: " + (tvDistanceTime != null));

        if (btnNavigate != null) {
            btnNavigate.setOnClickListener(v -> {
                Log.d(TAG, "Navigate button clicked!");
                if (isRescuerMode) {
                    // For rescuers, show navigation options
                    showNavigationOptions();
                } else {
                    // For other modes, use internal navigation
                    startInternalNavigation();
                }
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

        if (btnCallClosestRescuer != null) {
            btnCallClosestRescuer.setOnClickListener(v -> callClosestRescuer());
        }

        if (btnTestTracking != null) {
            btnTestTracking.setOnClickListener(v -> testRescuerTracking());
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Show emergency info card for rescuer mode
        if (isRescuerMode && emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
            updateEmergencyInfo();
            Log.d(TAG, "Emergency info card made visible for rescuer mode");
            
            // Auto-start navigation for rescuers if both locations are available
            if (currentLocation != null && destinationLocation != null) {
                // Delay slightly to ensure map is fully loaded
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!routeDisplayed) {
                        startInternalNavigation();
                    }
                }, 1500);
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
        
        // Update navigate button text for rescuers
        if (btnNavigate != null && isRescuerMode) {
            btnNavigate.setText(getString(R.string.get_route));
            Log.d(TAG, "Updated navigate button text for rescuer mode");
        }
        
        // Make sure the emergency info card is visible
        if (emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
            Log.d(TAG, "Emergency info card made visible");
        }
    }

    private void showNavigationOptions() {
        Log.d(TAG, "showNavigationOptions called");
        
        if (destinationLocation == null) {
            Log.e(TAG, "Destination location is null");
            Toast.makeText(this, "Destination not available", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Navigation Method");
        builder.setMessage("How would you like to navigate to the emergency location?");
        
        builder.setPositiveButton("🚗 Google Maps App", (dialog, which) -> {
            // Open external Google Maps with turn-by-turn navigation
            openExternalGoogleMapsNavigation();
        });
        
        builder.setNeutralButton("📍 In-App Route", (dialog, which) -> {
            // Show route on the in-app map
            startInternalNavigation();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openExternalGoogleMapsNavigation() {
        Log.d(TAG, "openExternalGoogleMapsNavigation called");
        
        if (destinationLocation == null) {
            Log.e(TAG, "Destination location is null");
            Toast.makeText(this, "Destination not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create Google Maps navigation intent with turn-by-turn directions
            String navigationUri = String.format(Locale.getDefault(), "google.navigation:q=%f,%f&mode=d", 
                destinationLocation.latitude, destinationLocation.longitude);
            Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
            navigationIntent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps is installed
            if (navigationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navigationIntent);
                Toast.makeText(this, "🚗 Opening Google Maps navigation to " + 
                    (seniorName != null ? seniorName : "emergency location"), Toast.LENGTH_LONG).show();
            } else {
                // Fallback to web-based Google Maps
                String webMapsUri = String.format(Locale.getDefault(), "https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving", 
                    destinationLocation.latitude, destinationLocation.longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webMapsUri));
                startActivity(webIntent);
                Toast.makeText(this, "🌐 Opening web-based navigation", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening external Google Maps navigation", e);
            Toast.makeText(this, "Error opening navigation", Toast.LENGTH_SHORT).show();
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
            btnNavigate.setText("Get Route");
            btnNavigate.setOnClickListener(v -> startInternalNavigation());
        }

        // Clear distance and time
        if (tvDistanceTime != null) {
            tvDistanceTime.setText("");
        }

        Toast.makeText(this, "Navigation stopped", Toast.LENGTH_SHORT).show();
    }

    private void showRoute() {
        Log.d(TAG, "showRoute called");
        
        if (currentLocation == null || destinationLocation == null) {
            Log.e(TAG, "Cannot show route - missing locations");
            return;
        }

        // Get directions using Google Directions API
        String directionsUrl = buildDirectionsUrl(currentLocation, destinationLocation);
        executeDirectionsRequest(directionsUrl);
    }

    private void clearRoute() {
        Log.d(TAG, "clearRoute called");
        
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
            routeDisplayed = false;
            Log.d(TAG, "Route cleared from map");
        }
    }

    private void toggleRouteDisplay() {
        Log.d(TAG, "toggleRouteDisplay called");
        
        if (routeDisplayed) {
            clearRoute();
            if (btnShowRoute != null) {
                btnShowRoute.setText("Show Route");
            }
            Toast.makeText(this, "Route hidden", Toast.LENGTH_SHORT).show();
        } else {
            showRoute();
            if (btnShowRoute != null) {
                btnShowRoute.setText("Hide Route");
            }
        }
    }

    private void calculateSimpleDistanceAndTime() {
        Log.d(TAG, "calculateSimpleDistanceAndTime called");

        if (currentLocation == null || destinationLocation == null) {
            Log.e(TAG, "Cannot calculate distance - locations missing");
            return;
        }

        // Calculate straight-line distance
        float[] results = new float[1];
        Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                destinationLocation.latitude, destinationLocation.longitude,
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
        
        // Update UI
        updateEmergencyInfo();
    }

    private String buildDirectionsUrl(LatLng origin, LatLng destination) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + destination.latitude + "," + destination.longitude;
        String parameters = str_origin + "&" + str_dest + "&key=" + getString(R.string.google_maps_key);
        String output = "json";
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
    }

    private void executeDirectionsRequest(String directionsUrl) {
        Log.d(TAG, "executeDirectionsRequest called with URL: " + directionsUrl);
        
        if (executorService == null) {
            Log.e(TAG, "ExecutorService is null");
            return;
        }

        executorService.execute(() -> {
            try {
                String jsonResponse = makeDirectionsRequest(directionsUrl);
                if (jsonResponse != null) {
                    runOnUiThread(() -> parseDirectionsResponse(jsonResponse));
                } else {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Failed to get directions response");
                        Toast.makeText(MyGoogleMAp.this, "Failed to get directions", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in executeDirectionsRequest", e);
                runOnUiThread(() -> Toast.makeText(MyGoogleMAp.this, "Error getting directions", Toast.LENGTH_SHORT).show());
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
            Log.d(TAG, "Directions API response code: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                return response.toString();
            } else {
                Log.e(TAG, "HTTP error code: " + responseCode);
                return null;
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error making directions request", e);
            return null;
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
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Log.d(TAG, "onMapReady called");
        myMap = googleMap;

        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED 
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permissions not granted, requesting...");
            requestLocationPermission();
            return;
        }

        // Enable location features
        try {
            myMap.setMyLocationEnabled(true);
            myMap.getUiSettings().setMyLocationButtonEnabled(true);
            myMap.getUiSettings().setZoomControlsEnabled(true);
            myMap.getUiSettings().setCompassEnabled(true);
            Log.d(TAG, "Location features enabled");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException enabling location features", e);
        }

        // Display the received location if available
        if (receivedLat != 0.0 && receivedLong != 0.0) {
            displayReceivedLocation();
        } else {
            Log.d(TAG, "No received location to display");
        }

        // Get current location and start tracking
        getCurrentLocation();
        
        // Start rescuer tracking if in rescuer mode
        if (isRescuerMode && helpRequestId != null) {
            Log.d(TAG, "Starting rescuer tracking for help request: " + helpRequestId);
            startRescuerTracking();
        }

        // Start senior tracking if in senior tracking mode
        if (isSeniorTrackingMode && helpRequestIdForTracking != null) {
            Log.d(TAG, "Starting senior tracking for help request: " + helpRequestIdForTracking);
            startSeniorTracking();
        }
    }

    private void displayReceivedLocation() {
        Log.d(TAG, "displayReceivedLocation called with: " + receivedLat + ", " + receivedLong);
        
        if (myMap == null) {
            Log.e(TAG, "Map is null in displayReceivedLocation");
            return;
        }

        LatLng receivedLocation = new LatLng(receivedLat, receivedLong);
        destinationLocation = receivedLocation;

        // Add marker for the received location
        MarkerOptions markerOptions = new MarkerOptions()
                .position(receivedLocation)
                .title(isRescuerMode ? "🚨 Emergency Location" : "📍 Destination");

        if (receivedAddress != null && !receivedAddress.isEmpty()) {
            markerOptions.snippet("📍 " + receivedAddress);
        }

        if (isEmergencyMode || isRescuerMode) {
            markerOptions.title("🚨 Emergency Location");
            if (seniorName != null && !seniorName.isEmpty()) {
                markerOptions.snippet("👤 " + seniorName + (receivedAddress != null ? "\n📍 " + receivedAddress : ""));
            }
        }

        myMap.addMarker(markerOptions);
        
        // Move camera to the received location
        myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(receivedLocation, 15f));
        
        Log.d(TAG, "Marker added and camera moved to received location");
    }

    private void parseDirectionsResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routes = jsonObject.getJSONArray("routes");
            
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                
                // Get distance and duration from the route
                JSONArray legs = route.getJSONArray("legs");
                if (legs.length() > 0) {
                    JSONObject leg = legs.getJSONObject(0);
                    
                    JSONObject distance = leg.getJSONObject("distance");
                    JSONObject duration = leg.getJSONObject("duration");
                    
                    estimatedDistance = distance.getString("text");
                    estimatedTime = duration.getString("text");
                    
                    Log.d(TAG, "Route distance: " + estimatedDistance + ", duration: " + estimatedTime);
                    
                    // Update UI with accurate distance and time
                    updateEmergencyInfo();
                }
                
                // Get polyline points
                JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                String encodedPolyline = overviewPolyline.getString("points");
                
                // Decode and display the route
                List<LatLng> routePoints = decodePolyline(encodedPolyline);
                displayRoute(routePoints);
                
            } else {
                Log.e(TAG, "No routes found in directions response");
                Toast.makeText(this, "No route found", Toast.LENGTH_SHORT).show();
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing directions response", e);
            Toast.makeText(this, "Error parsing route data", Toast.LENGTH_SHORT).show();
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
        if (myMap == null || routePoints == null || routePoints.isEmpty()) {
            Log.e(TAG, "Cannot display route - map or points null/empty");
            return;
        }

        // Clear existing route
        clearRoute();

        // Add new route polyline
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .width(8)
                .color(0xFF2196F3) // Blue color
                .geodesic(true);

        routePolyline = myMap.addPolyline(polylineOptions);
        routeDisplayed = true;

        Log.d(TAG, "Route displayed with " + routePoints.size() + " points");

        // Update button text
        if (btnShowRoute != null) {
            btnShowRoute.setText("Hide Route");
        }

        // Adjust camera to show the entire route
        if (currentLocation != null && destinationLocation != null) {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            boundsBuilder.include(currentLocation);
            boundsBuilder.include(destinationLocation);
            
            try {
                LatLngBounds bounds = boundsBuilder.build();
                myMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            } catch (Exception e) {
                Log.e(TAG, "Error adjusting camera bounds", e);
            }
        }
    }

    // =============== LOCATION METHODS ===============
    
    private void getCurrentLocation() {
        Log.d(TAG, "getCurrentLocation called");
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED 
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permissions not granted, requesting...");
            requestLocationPermission();
            return;
        }

        if (fusedLocationClient != null) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        Log.d(TAG, "Current location obtained: " + currentLocation.latitude + ", " + currentLocation.longitude);
                        
                        // Update UI with current location
                        updateEmergencyInfo();
                        
                        // If in rescuer mode and we have destination, start navigation
                        if (isRescuerMode && destinationLocation != null && !routeDisplayed) {
                            startInternalNavigation();
                        }
                    } else {
                        Log.d(TAG, "No last known location, starting location updates...");
                        startLocationUpdates();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting last location", e);
                    startLocationUpdates();
                });
        }
    }

    private void requestLocationPermission() {
        Log.d(TAG, "requestLocationPermission called");
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    // =============== TRACKING METHODS ===============
    
    private void startRescuerTracking() {
        Log.d(TAG, "startRescuerTracking called for help request: " + helpRequestId);
        
        if (helpRequestId == null || helpRequestId.isEmpty()) {
            Log.e(TAG, "No help request ID for tracking");
            return;
        }

        // Listen for rescuer location updates
        rescuerLocationListener = db.collection("Sagip")
            .document("helpRequests")
            .collection("activeRequests")
            .document(helpRequestId)
            .collection("rescuerLocations")
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error listening to rescuer locations", e);
                    return;
                }

                if (snapshot != null && !snapshot.isEmpty()) {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String rescuerId = doc.getId();
                        Double latitude = doc.getDouble("latitude");
                        Double longitude = doc.getDouble("longitude");
                        String rescuerName = doc.getString("rescuerName");
                        String rescuerPhone = doc.getString("rescuerPhone");
                        Long timestamp = doc.getLong("timestamp");

                        if (latitude != null && longitude != null) {
                            LatLng rescuerLocation = new LatLng(latitude, longitude);
                            
                            // Update or create rescuer marker
                            if (rescuerMarkers.containsKey(rescuerId)) {
                                rescuerMarkers.get(rescuerId).setPosition(rescuerLocation);
                            } else {
                                MarkerOptions markerOptions = new MarkerOptions()
                                    .position(rescuerLocation)
                                    .title("🚑 Rescuer: " + (rescuerName != null ? rescuerName : "Unknown"))
                                    .snippet("📞 " + (rescuerPhone != null ? rescuerPhone : "No phone"));
                                
                                Marker marker = myMap.addMarker(markerOptions);
                                rescuerMarkers.put(rescuerId, marker);
                                rescuerNames.put(rescuerId, rescuerName != null ? rescuerName : "Unknown");
                                rescuerPhones.put(rescuerId, rescuerPhone != null ? rescuerPhone : "");
                            }
                            
                            Log.d(TAG, "Updated rescuer location: " + rescuerName + " at " + latitude + ", " + longitude);
                        }
                    }
                }
            });
    }

    private void startSeniorTracking() {
        Log.d(TAG, "startSeniorTracking called for help request: " + helpRequestIdForTracking);
        
        if (helpRequestIdForTracking == null || helpRequestIdForTracking.isEmpty()) {
            Log.e(TAG, "No help request ID for senior tracking");
            return;
        }

        // Listen for help request status updates
        helpRequestListener = db.collection("Sagip")
            .document("helpRequests")
            .collection("activeRequests")
            .document(helpRequestIdForTracking)
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error listening to help request updates", e);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    String status = snapshot.getString("status");
                    Log.d(TAG, "Help request status updated: " + status);
                    
                    if ("resolved".equals(status)) {
                        // Help request resolved, show success message
                        Toast.makeText(this, "✅ Emergency resolved! Help request closed.", Toast.LENGTH_LONG).show();
                        
                        // Stop tracking
                        if (helpRequestListener != null) {
                            helpRequestListener.remove();
                        }
                    }
                }
            });
    }

    private void callSenior() {
        Log.d(TAG, "callSenior called");
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent);
            } else {
                Toast.makeText(this, "Call permission not granted", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show();
        }
    }

    private void callClosestRescuer() {
        Log.d(TAG, "callClosestRescuer called");
        Toast.makeText(this, "Calling closest rescuer feature will be implemented", Toast.LENGTH_SHORT).show();
    }

    private void testRescuerTracking() {
        Log.d(TAG, "testRescuerTracking called");
        Toast.makeText(this, "Test rescuer tracking feature", Toast.LENGTH_SHORT).show();
    }

    // =============== NOTIFICATION METHODS ===============
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "emergency_channel",
                    "Emergency Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Emergency help requests from seniors");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void checkNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Request notification permission for Android 13+
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
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
                    }
                }
        );
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (fusedLocationClient != null) {
            LocationRequest locationRequest = new LocationRequest.Builder(10000)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(5000)
                    .build();

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        Location location = locationResult.getLastLocation();
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        
                        Log.d(TAG, "Location updated: " + currentLocation.latitude + ", " + currentLocation.longitude);
                        
                        // Update UI
                        updateEmergencyInfo();
                        
                        // If in rescuer mode, update rescuer location in Firebase
                        if (isRescuerMode && helpRequestId != null && !helpRequestId.isEmpty()) {
                            updateRescuerLocationInFirebase(location);
                        }
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            locationUpdatesActive = true;
            Log.d(TAG, "Location updates started");
        }
    }

    private void updateRescuerLocationInFirebase(Location location) {
        if (helpRequestId == null || helpRequestId.isEmpty()) {
            return;
        }

        // Get current user info
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            Log.e(TAG, "No current user ID available");
            return;
        }

        Map<String, Object> rescuerLocation = new HashMap<>();
        rescuerLocation.put("latitude", location.getLatitude());
        rescuerLocation.put("longitude", location.getLongitude());
        rescuerLocation.put("timestamp", System.currentTimeMillis());
        rescuerLocation.put("rescuerId", currentUserId);
        
        // Add rescuer name and phone if available
        String rescuerName = getCurrentRescuerName();
        if (rescuerName != null) {
            rescuerLocation.put("rescuerName", rescuerName);
        }

        // Update rescuer location in Firebase
        db.collection("Sagip")
            .document("helpRequests")
            .collection("activeRequests")
            .document(helpRequestId)
            .collection("rescuerLocations")
            .document(currentUserId)
            .set(rescuerLocation)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Rescuer location updated in Firebase");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating rescuer location in Firebase", e);
            });
    }

    private String getCurrentUserId() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            return currentUser.getUid();
        }
        return null;
    }

    private String getCurrentRescuerName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
            // Fallback to email if no display name
            String email = currentUser.getEmail();
            if (email != null && !email.isEmpty()) {
                return email.split("@")[0]; // Get username part of email
            }
        }
        return "Rescuer";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        
        // Stop location updates
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
            Log.d(TAG, "Location updates stopped");
        }
        
        // Remove Firebase listeners
        if (rescuerLocationListener != null) {
            rescuerLocationListener.remove();
            rescuerLocationListener = null;
        }
        
        if (helpRequestListener != null) {
            helpRequestListener.remove();
            helpRequestListener = null;
        }
        
        // Shutdown executor service
        if (executorService != null) {
            executorService.shutdown();
        }
        
        Log.d(TAG, "MyGoogleMAp destroyed and cleaned up");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause location updates to save battery
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
            Log.d(TAG, "Location updates paused");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume location updates if needed
        if (!locationUpdatesActive && !isDestroyed) {
            getCurrentLocation();
        }
    }
}
