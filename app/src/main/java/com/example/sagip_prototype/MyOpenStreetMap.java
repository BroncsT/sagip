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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

// OpenStreetMap imports
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

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

public class MyOpenStreetMap extends AppCompatActivity {

    private static final String TAG = "MyOpenStreetMap";
    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesActive = false;
    private ActivityResultLauncher<String[]> locationPermissionRequest;
    private MyLocationNewOverlay myLocationOverlay;

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
    private GeoPoint currentLocation = null;
    private GeoPoint destinationLocation = null;
    private Polyline currentRoute = null;
    private List<GeoPoint> routePoints = new ArrayList<>();
    private boolean routeDisplayed = false;

    // UI Elements
    private LinearLayout emergencyInfoCard;
    private TextView tvEmergencyTitle;
    private TextView tvEmergencyAddress;
    private TextView tvDistanceTime;
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
    
    // OpenStreetMap Routing API constants (using OSRM)
    private static final String OSRM_API_URL = "https://router.project-osrm.org/route/v1";
    
    // Notification constants
    private static final String CHANNEL_ID = "SAGIPP_EMERGENCY_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_NAME = "Emergency Alerts";
    private static final String CHANNEL_DESCRIPTION = "Notifications for emergency responses";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_my_openstreet_map);

        // Initialize OpenStreetMap configuration
        Configuration.getInstance().setUserAgentValue(getPackageName());

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
            seniorNameForTracking = intent.getStringExtra("seniorName");

            Log.d(TAG, "Received location: " + receivedLat + ", " + receivedLong);
            Log.d(TAG, "Emergency mode: " + isEmergencyMode);
            Log.d(TAG, "Rescuer mode: " + isRescuerMode);
            Log.d(TAG, "Senior tracking mode: " + isSeniorTrackingMode);
            Log.d(TAG, "Help request ID for tracking: " + helpRequestIdForTracking);
        }

        // Set destination location for routing
        if (receivedLat != 0.0 && receivedLong != 0.0) {
            destinationLocation = new GeoPoint(receivedLat, receivedLong);
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

        // Initialize map
        initializeMap();
    }

    private void initializeMap() {
        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);
        // Note: osmdroid handles zoom levels differently
        // The zoom controls are handled by setBuiltInZoomControls(true)

        // Initialize location overlay
        myLocationOverlay = new MyLocationNewOverlay(mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        // Set up marker click listener (simplified for osmdroid)
        // Note: osmdroid handles tap events differently than Google Maps

        // Display received location if available
        if (receivedLat != 0.0 && receivedLong != 0.0) {
            displayReceivedLocation();
        }

        // Start location updates if needed
        if (isRescuerMode) {
            enableMyLocation();
            requestLocationPermissions();
            getLastKnownLocation();
        }
    }

    private void initializeUI() {
        Log.d(TAG, "initializeUI called");

        emergencyInfoCard = findViewById(R.id.emergencyInfoCard);
        tvEmergencyTitle = findViewById(R.id.tvEmergencyTitle);
        tvEmergencyAddress = findViewById(R.id.tvEmergencyAddress);
        tvDistanceTime = findViewById(R.id.tvDistanceTime);
        btnNavigate = findViewById(R.id.btnNavigate);
        btnCallSenior = findViewById(R.id.btnCallSenior);
        btnShowRoute = findViewById(R.id.btnShowRoute);
        btnCallClosestRescuer = findViewById(R.id.btnCallClosestRescuer);
        btnTestTracking = findViewById(R.id.btnTestTracking);
        btnBack = findViewById(R.id.btnBack);

        if (btnNavigate != null) {
            btnNavigate.setOnClickListener(v -> {
                Log.d(TAG, "Navigate button clicked!");
                if (isRescuerMode) {
                    showNavigationOptions();
                } else {
                    startInternalNavigation();
                }
            });
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
        }
    }

    private void displayReceivedLocation() {
        Log.d(TAG, "displayReceivedLocation called - receivedLat: " + receivedLat + ", receivedLong: " + receivedLong);
        
        if (mapView != null && receivedLat != 0.0 && receivedLong != 0.0) {
            GeoPoint emergencyLocation = new GeoPoint(receivedLat, receivedLong);

            // Set destination location for rescuer mode
            if (isRescuerMode) {
                destinationLocation = emergencyLocation;
            }

            // Clear existing markers
            clearAllMarkers();

            String markerTitle;
            String markerSnippet;

            if (isRescuerMode) {
                // Rescuer viewing senior's emergency location
                markerTitle = "🚨 " + (seniorName != null ? seniorName : "Senior") + " NEEDS HELP";
                markerSnippet = buildRescuerSnippet();

                // Add destination marker
                Marker destinationMarker = new Marker(mapView);
                destinationMarker.setPosition(emergencyLocation);
                destinationMarker.setTitle(markerTitle);
                destinationMarker.setSnippet(markerSnippet);
                destinationMarker.setIcon(getResources().getDrawable(R.drawable.ic_ambulance));
                mapView.getOverlays().add(destinationMarker);

                // If we have current location, add it as well
                if (currentLocation != null) {
                    Marker currentMarker = new Marker(mapView);
                    currentMarker.setPosition(currentLocation);
                    currentMarker.setTitle("Your Location");
                    currentMarker.setSnippet("Rescuer Position");
                    currentMarker.setIcon(getResources().getDrawable(R.drawable.baseline_location_pin_24));
                    mapView.getOverlays().add(currentMarker);

                    // Calculate distance and time
                    calculateSimpleDistanceAndTime();

                    // Fit camera to show both locations
                    mapView.zoomToBoundingBox(getBoundingBox(currentLocation, emergencyLocation), true, 100);
                } else {
                    mapView.getController().setZoom(18.0);
                    mapView.getController().setCenter(emergencyLocation);
                }
            } else if (isSeniorTrackingMode) {
                // Senior viewing their own location and tracking rescuers
                markerTitle = "📍 Your Emergency Location";
                markerSnippet = receivedAddress != null && !receivedAddress.isEmpty() ? receivedAddress : "Your current location";

                // Add senior's location marker in red (emergency)
                Marker seniorMarker = new Marker(mapView);
                seniorMarker.setPosition(emergencyLocation);
                seniorMarker.setTitle(markerTitle);
                seniorMarker.setSnippet(markerSnippet);
                seniorMarker.setIcon(getResources().getDrawable(R.drawable.ic_ambulance));
                mapView.getOverlays().add(seniorMarker);

                // Start tracking rescuers
                startRescuerTracking();

                // Fit camera to show senior's location
                mapView.getController().setZoom(14.0);
                mapView.getController().setCenter(emergencyLocation);
            } else if (isEmergencyMode) {
                // Senior viewing their own emergency location
                markerTitle = "🆘 EMERGENCY LOCATION 🚨";
                markerSnippet = receivedAddress != null && !receivedAddress.isEmpty() ?
                        receivedAddress : "Emergency Help Needed";

                Marker marker = new Marker(mapView);
                marker.setPosition(emergencyLocation);
                marker.setTitle(markerTitle);
                marker.setSnippet(markerSnippet);
                mapView.getOverlays().add(marker);

                mapView.getController().setZoom(18.0);
                mapView.getController().setCenter(emergencyLocation);
            } else {
                // Regular location display
                markerTitle = "Current Location";
                markerSnippet = receivedAddress != null ? receivedAddress : "";

                Marker marker = new Marker(mapView);
                marker.setPosition(emergencyLocation);
                marker.setTitle(markerTitle);
                marker.setSnippet(markerSnippet);
                mapView.getOverlays().add(marker);

                mapView.getController().setZoom(15.0);
                mapView.getController().setCenter(emergencyLocation);
            }

            mapView.invalidate();

            String toastMessage = isRescuerMode ?
                    "Senior's emergency location displayed" :
                    "Emergency location displayed on map";
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void clearAllMarkers() {
        List<org.osmdroid.views.overlay.Overlay> overlaysToRemove = new ArrayList<>();
        for (org.osmdroid.views.overlay.Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof Marker) {
                overlaysToRemove.add(overlay);
            }
        }
        mapView.getOverlays().removeAll(overlaysToRemove);
    }

    private org.osmdroid.util.BoundingBox getBoundingBox(GeoPoint point1, GeoPoint point2) {
        double minLat = Math.min(point1.getLatitude(), point2.getLatitude());
        double maxLat = Math.max(point1.getLatitude(), point2.getLatitude());
        double minLon = Math.min(point1.getLongitude(), point2.getLongitude());
        double maxLon = Math.max(point1.getLongitude(), point2.getLongitude());
        
        return new org.osmdroid.util.BoundingBox(maxLat, maxLon, minLat, minLon);
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

    // Location and navigation methods
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
            if (fusedLocationClient != null) {
                enableMyLocation();
                startLocationUpdates();
                getLastKnownLocation();
            }
        }
    }

    private void enableMyLocation() {
        if (myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (isDestroyed) return;
                
                for (Location location : locationResult.getLocations()) {
                    currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    updateMapLocation(location);
                    
                    if (destinationLocation != null) {
                        calculateSimpleDistanceAndTime();
                        
                        if (isRescuerMode && !routeDisplayed && mapView != null && !isDestroyed) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                if (!routeDisplayed && !isDestroyed) {
                                    startInternalNavigation();
                                }
                            }, 500);
                        }
                    }
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (locationCallback == null) {
            setupLocationCallback();
        }
        
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
        if (isDestroyed || mapView == null || location == null) return;
        
        currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        // Clear previous markers
        clearAllMarkers();

        // Add destination marker back if in rescuer mode
        if (isRescuerMode && destinationLocation != null) {
            Marker destinationMarker = new Marker(mapView);
            destinationMarker.setPosition(destinationLocation);
            destinationMarker.setTitle("Emergency Location");
            destinationMarker.setSnippet(receivedAddress);
            destinationMarker.setIcon(getResources().getDrawable(R.drawable.ic_ambulance));
            mapView.getOverlays().add(destinationMarker);
        }

        // Add current location marker
        Marker currentMarker = new Marker(mapView);
        currentMarker.setPosition(currentLocation);
        currentMarker.setTitle("Your Location");
        currentMarker.setSnippet("Rescuer Position");
                        currentMarker.setIcon(getResources().getDrawable(R.drawable.baseline_location_pin_24));
        mapView.getOverlays().add(currentMarker);

        // Calculate distance and time when we have both locations
        if (destinationLocation != null) {
            calculateSimpleDistanceAndTime();
        }

        // If route is displayed, update it
        if (routeDisplayed && !isDestroyed) {
            showRoute();
        }

        mapView.invalidate();
    }

    private void getLastKnownLocation() {
        if (fusedLocationClient == null) return;
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
            return;
        }
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                        updateMapLocation(location);
                        if (destinationLocation != null) {
                            calculateSimpleDistanceAndTime();
                        }
                    } else {
                        startLocationUpdates();
                    }
                })
                .addOnFailureListener(this, e -> {
                    startLocationUpdates();
                });
    }

    // Navigation methods
    private void showNavigationOptions() {
        if (destinationLocation == null) {
            Toast.makeText(this, "Destination not available", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Navigation Method");
        builder.setMessage("How would you like to navigate to the emergency location?");
        
        builder.setPositiveButton("🗺️ In-App Route", (dialog, which) -> {
            startInternalNavigation();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void startInternalNavigation() {
        if (destinationLocation == null) {
            Toast.makeText(this, "Destination not available", Toast.LENGTH_SHORT).show();
            return;
        }

        showRoute();
        calculateSimpleDistanceAndTime();
        mapView.getController().animateTo(destinationLocation);
        mapView.getController().setZoom(18.0);

        Toast.makeText(this, "🗺️ Navigation started - Follow the blue route line", Toast.LENGTH_LONG).show();

        if (btnNavigate != null) {
            btnNavigate.setText("📍 Stop Navigation");
            btnNavigate.setOnClickListener(v -> stopInternalNavigation());
        }
    }

    private void stopInternalNavigation() {
        clearRoute();
        if (btnNavigate != null) {
            btnNavigate.setText("Get Route");
            btnNavigate.setOnClickListener(v -> startInternalNavigation());
        }
        if (tvDistanceTime != null) {
            tvDistanceTime.setText("");
        }
        Toast.makeText(this, "Navigation stopped", Toast.LENGTH_SHORT).show();
    }

    private void toggleRouteDisplay() {
        if (currentLocation == null) {
            Toast.makeText(this, "Getting your location... Please wait a moment.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (destinationLocation == null) {
            Toast.makeText(this, "Emergency location not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (routeDisplayed) {
            clearRoute();
        } else {
            showRoute();
        }
    }

    private void showRoute() {
        if (currentLocation == null || destinationLocation == null) {
            Toast.makeText(this, "Location data not available", Toast.LENGTH_SHORT).show();
            return;
        }

        clearRoute();
        Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show();

        // Use OSRM API for routing
        getOSRMRoute(currentLocation, destinationLocation);
    }

    private void clearRoute() {
        if (currentRoute != null) {
            mapView.getOverlays().remove(currentRoute);
            currentRoute = null;
        }
        routePoints.clear();
        routeDisplayed = false;
        isCalculatingRoute = false;
        
        if (btnShowRoute != null) {
            btnShowRoute.setText("Show Route");
        }
        
        mapView.invalidate();
    }

    private void getOSRMRoute(GeoPoint origin, GeoPoint destination) {
        if (isCalculatingRoute) return;
        isCalculatingRoute = true;

        String url = String.format("%s/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                OSRM_API_URL,
                origin.getLongitude(), origin.getLatitude(),
                destination.getLongitude(), destination.getLatitude());

        executorService.execute(() -> {
            try {
                String response = makeHttpRequest(url);
                if (!isDestroyed) {
                    runOnUiThread(() -> {
                        if (!isDestroyed) {
                            parseOSRMResponse(response);
                            isCalculatingRoute = false;
                        }
                    });
                }
            } catch (Exception e) {
                if (!isDestroyed) {
                    runOnUiThread(() -> {
                        if (!isDestroyed) {
                            Toast.makeText(this, "Error getting route. Using straight line.", Toast.LENGTH_SHORT).show();
                            showStraightLineRoute();
                            isCalculatingRoute = false;
                        }
                    });
                }
            }
        });
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

    private void parseOSRMResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            String code = jsonResponse.getString("code");
            
            if ("Ok".equals(code)) {
                JSONArray routes = jsonResponse.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONObject route = jsonResponse.getJSONArray("routes").getJSONObject(0);
                    
                    // Get distance and duration
                    double distance = route.getDouble("distance");
                    double duration = route.getDouble("duration");
                    
                    estimatedDistance = String.format("%.1f km", distance / 1000);
                    estimatedTime = String.format("%.0f min", duration / 60);
                    
                    // Get route geometry
                    JSONObject geometry = route.getJSONObject("geometry");
                    JSONArray coordinates = geometry.getJSONArray("coordinates");
                    
                    List<GeoPoint> points = new ArrayList<>();
                    for (int i = 0; i < coordinates.length(); i++) {
                        JSONArray coord = coordinates.getJSONArray(i);
                        double lon = coord.getDouble(0);
                        double lat = coord.getDouble(1);
                        points.add(new GeoPoint(lat, lon));
                    }
                    
                    drawRouteOnMap(points);
                    updateDistanceTimeDisplay();
                    
                    Toast.makeText(this, "Route calculated: " + estimatedDistance + " • " + estimatedTime, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Error getting route. Using straight line.", Toast.LENGTH_SHORT).show();
                showStraightLineRoute();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Error parsing route. Using straight line.", Toast.LENGTH_SHORT).show();
            showStraightLineRoute();
        }
    }

    private void drawRouteOnMap(List<GeoPoint> points) {
        if (mapView == null || points.isEmpty()) return;

        clearRoute();

        currentRoute = new Polyline();
        currentRoute.setPoints(points);
        currentRoute.setColor(getResources().getColor(android.R.color.holo_blue_dark));
        currentRoute.setWidth(8.0f);

        mapView.getOverlays().add(currentRoute);
        routePoints.clear();
        routePoints.addAll(points);
        routeDisplayed = true;

        if (btnShowRoute != null) {
            btnShowRoute.setText("Hide Route");
        }

        // Fit camera to show the entire route
        if (points.size() > 1) {
            org.osmdroid.util.BoundingBox bounds = getBoundingBox(points.get(0), points.get(points.size() - 1));
            mapView.zoomToBoundingBox(bounds, true, 100);
        }

        mapView.invalidate();
    }

    private void showStraightLineRoute() {
        routePoints.clear();
        routePoints.add(currentLocation);
        routePoints.add(destinationLocation);

        currentRoute = new Polyline();
        currentRoute.setPoints(routePoints);
        currentRoute.setColor(getResources().getColor(android.R.color.holo_blue_dark));
        currentRoute.setWidth(8.0f);

        mapView.getOverlays().add(currentRoute);

        org.osmdroid.util.BoundingBox bounds = getBoundingBox(currentLocation, destinationLocation);
        mapView.zoomToBoundingBox(bounds, true, 100);

        routeDisplayed = true;
        if (btnShowRoute != null) {
            btnShowRoute.setText("Hide Route");
        }

        calculateSimpleDistanceAndTime();
        isCalculatingRoute = false;
        mapView.invalidate();
    }

    // Distance and time calculation
    private void calculateSimpleDistanceAndTime() {
        if (currentLocation == null || destinationLocation == null) return;

        float[] results = new float[1];
        android.location.Location.distanceBetween(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                destinationLocation.getLatitude(), destinationLocation.getLongitude(),
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

        updateDistanceTimeDisplay();
    }

    private void updateDistanceTimeDisplay() {
        if (tvDistanceTime != null && !estimatedDistance.isEmpty() && !estimatedTime.isEmpty()) {
            String displayText = "📍 " + estimatedDistance + " • ⏱️ " + estimatedTime;
            tvDistanceTime.setText(displayText);
            
            if (isRescuerMode && emergencyInfoCard != null) {
                emergencyInfoCard.setVisibility(View.VISIBLE);
            }
        } else {
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

    private void updateEmergencyInfo() {
        if (tvEmergencyTitle != null) {
            String title = getString(R.string.senior_needs_help, seniorName != null ? seniorName : "Senior");
            tvEmergencyTitle.setText(title);
        }

        if (tvEmergencyAddress != null && receivedAddress != null) {
            tvEmergencyAddress.setText("📍 " + receivedAddress);
        }
        
        if (tvDistanceTime != null && !estimatedDistance.isEmpty() && !estimatedTime.isEmpty()) {
            String displayText = "📍 " + estimatedDistance + " • ⏱️ " + estimatedTime;
            tvDistanceTime.setText(displayText);
        } else if (tvDistanceTime != null) {
            if (currentLocation == null) {
                tvDistanceTime.setText("📍 Getting your location...");
            } else if (destinationLocation == null) {
                tvDistanceTime.setText("📍 Getting destination...");
            } else {
                tvDistanceTime.setText("📍 Calculating distance...");
            }
        }
        
        if (btnNavigate != null && isRescuerMode) {
            btnNavigate.setText("Get Route");
        }
        
        if (emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
        }
    }

    // Utility methods
    private void callSenior() {
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            startActivity(callIntent);
        } else {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void callClosestRescuer() {
        if (rescuerMarkers.isEmpty()) {
            Toast.makeText(this, "No rescuers available to call", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find the closest rescuer
        String closestRescuerId = null;
        float closestDistance = Float.MAX_VALUE;

        for (Map.Entry<String, Marker> entry : rescuerMarkers.entrySet()) {
            String rescuerId = entry.getKey();
            Marker marker = entry.getValue();
            
            if (marker != null && destinationLocation != null) {
                float[] results = new float[1];
                android.location.Location.distanceBetween(
                        destinationLocation.getLatitude(), destinationLocation.getLongitude(),
                        marker.getPosition().getLatitude(), marker.getPosition().getLongitude(),
                        results
                );
                
                if (results[0] < closestDistance) {
                    closestDistance = results[0];
                    closestRescuerId = rescuerId;
                }
            }
        }

        if (closestRescuerId != null) {
            String rescuerPhone = rescuerPhones.get(closestRescuerId);
            String rescuerName = rescuerNames.get(closestRescuerId);
            
            if (rescuerPhone != null && !rescuerPhone.isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + rescuerPhone));
                startActivity(callIntent);
                
                String displayName = rescuerName != null ? rescuerName : "Rescuer";
                Toast.makeText(this, "Calling " + displayName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Phone number not available for this rescuer", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Unable to find closest rescuer", Toast.LENGTH_SHORT).show();
        }
    }

    // Rescuer tracking methods (simplified version)
    private void startRescuerTracking() {
        if (helpRequestIdForTracking == null || helpRequestIdForTracking.isEmpty()) {
            return;
        }

        // Start tracking rescuers
        startActiveRescuerTracking();
    }

    private void startActiveRescuerTracking() {
        rescuerLocationListener = db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null || querySnapshot == null) return;

                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        String rescuerId = document.getId();
                        
                        Double latitude = document.getDouble("latitude");
                        Double longitude = document.getDouble("longitude");
                        
                        if (latitude == null || longitude == null) {
                            com.google.firebase.firestore.GeoPoint geoPoint = document.getGeoPoint("currentLocation");
                            if (geoPoint != null) {
                                latitude = geoPoint.getLatitude();
                                longitude = geoPoint.getLongitude();
                            }
                        }
                        
                        String rescuerName = document.getString("rescuegroup");
                        String phoneNumber = document.getString("mobileNumber");
                        
                        if (latitude != null && longitude != null) {
                            rescuerNames.put(rescuerId, rescuerName != null ? rescuerName : "Rescuer");
                            rescuerPhones.put(rescuerId, phoneNumber != null ? phoneNumber : "");
                            updateRescuerMarker(rescuerId, rescuerName, latitude, longitude);
                        }
                    }
                });
    }

    private void updateRescuerMarker(String rescuerId, String rescuerName, double latitude, double longitude) {
        GeoPoint rescuerLocation = new GeoPoint(latitude, longitude);
        
        if (mapView == null) return;

        // Remove existing marker for this rescuer
        if (rescuerMarkers.containsKey(rescuerId)) {
            mapView.getOverlays().remove(rescuerMarkers.get(rescuerId));
        }

        // Calculate distance from senior to rescuer
        String distanceText = "";
        if (destinationLocation != null) {
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    destinationLocation.getLatitude(), destinationLocation.getLongitude(),
                    latitude, longitude, results
            );
            float distanceInMeters = results[0];
            if (distanceInMeters < 1000) {
                distanceText = String.format("%.0f m away", distanceInMeters);
            } else {
                distanceText = String.format("%.1f km away", distanceInMeters / 1000);
            }
        }

        // Create new marker
        Marker rescuerMarker = new Marker(mapView);
        rescuerMarker.setPosition(rescuerLocation);
        rescuerMarker.setTitle("🚑 " + (rescuerName != null ? rescuerName : "Rescuer"));
        rescuerMarker.setSnippet(distanceText.isEmpty() ? "Coming to help you" : distanceText);
        rescuerMarker.setIcon(getResources().getDrawable(R.drawable.ic_ambulance));

        mapView.getOverlays().add(rescuerMarker);
        rescuerMarkers.put(rescuerId, rescuerMarker);
        updateTrackingInfo();
        mapView.invalidate();
    }

    private void updateTrackingInfo() {
        if (tvEmergencyTitle != null) {
            String title = seniorNameForTracking != null && !seniorNameForTracking.isEmpty() ? 
                "🚑 Tracking Help for " + seniorNameForTracking : "🚑 Tracking Rescuers";
            tvEmergencyTitle.setText(title);
        }

        if (tvEmergencyAddress != null && receivedAddress != null) {
            tvEmergencyAddress.setText("📍 " + receivedAddress);
        }
        
        if (tvDistanceTime != null) {
            if (rescuerMarkers.isEmpty()) {
                tvDistanceTime.setText("⏳ Waiting for rescuers to respond...");
            } else {
                tvDistanceTime.setText("🚑 " + rescuerMarkers.size() + " rescuer(s) coming to help");
            }
        }
        
        if (emergencyInfoCard != null) {
            emergencyInfoCard.setVisibility(View.VISIBLE);
        }

        if (btnCallClosestRescuer != null) {
            if (isSeniorTrackingMode && !rescuerMarkers.isEmpty()) {
                btnCallClosestRescuer.setVisibility(View.VISIBLE);
            } else {
                btnCallClosestRescuer.setVisibility(View.GONE);
            }
        }

        if (btnTestTracking != null) {
            if (isSeniorTrackingMode) {
                btnTestTracking.setVisibility(View.VISIBLE);
            } else {
                btnTestTracking.setVisibility(View.GONE);
            }
        }
    }

    private void testRescuerTracking() {
        if (isSeniorTrackingMode) {
            checkAllRescuersInDatabase();
            if (rescuerLocationListener != null) {
                rescuerLocationListener.remove();
                rescuerLocationListener = null;
            }
            startActiveRescuerTracking();
            Toast.makeText(this, "Rescuer tracking refreshed. Check logs for details.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Not in senior tracking mode", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAllRescuersInDatabase() {
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Found " + querySnapshot.size() + " rescuers in database");
                    
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        String rescuerId = document.getId();
                        String rescuerName = document.getString("rescuegroup");
                        Double latitude = document.getDouble("latitude");
                        Double longitude = document.getDouble("longitude");
                        com.google.firebase.firestore.GeoPoint geoPoint = document.getGeoPoint("currentLocation");
                        
                        Log.d(TAG, "Rescuer " + rescuerId + ":");
                        Log.d(TAG, "  Name: " + rescuerName);
                        Log.d(TAG, "  Latitude: " + latitude);
                        Log.d(TAG, "  Longitude: " + longitude);
                        Log.d(TAG, "  GeoPoint: " + (geoPoint != null ? geoPoint.getLatitude() + ", " + geoPoint.getLongitude() : "null"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking rescuers in database", e);
                });
    }

    // Notification methods
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!hasPermission) {
                requestNotificationPermissions();
            }
        }
    }

    private void requestNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
            }
        }
    }

    // Lifecycle methods
    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        
        if (fusedLocationClient != null && !locationUpdatesActive) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                if (isRescuerMode) {
                    startLocationUpdates();
                } else if (receivedLat == 0.0 && receivedLong == 0.0) {
                    startLocationUpdates();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        
        if (rescuerLocationListener != null) {
            rescuerLocationListener.remove();
            rescuerLocationListener = null;
        }
        
        if (helpRequestListener != null) {
            helpRequestListener.remove();
            helpRequestListener = null;
        }
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        if (mapView != null) {
            mapView.onDetach();
        }
    }

    private void stopLocationUpdates() {
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1002) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied. Some features may not work properly.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
