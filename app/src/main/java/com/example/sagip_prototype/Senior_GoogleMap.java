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
import android.widget.LinearLayout;
import androidx.cardview.widget.CardView;
import android.widget.TextView;
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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

public class Senior_GoogleMap extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "Senior_GoogleMap";
    private GoogleMap myMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesActive = false;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    // Variables to store senior location data
    private double seniorLat = 0.0;
    private double seniorLong = 0.0;
    private String seniorAddress = "";
    private String seniorName = "";

    // Variables for tracking rescuers
    private String helpRequestId = "";
    private ListenerRegistration rescuerLocationListener = null;
    private ListenerRegistration helpRequestListener = null;
    private Map<String, Marker> rescuerMarkers = new HashMap<>();
    private Map<String, String> rescuerNames = new HashMap<>();
    private Map<String, String> rescuerPhones = new HashMap<>();
    private Map<String, String> rescuerTeams = new HashMap<>();

    // Rescuer route tracking variables
    private Map<String, Polyline> rescuerRoutes = new HashMap<>();

    // UI Elements
    private CardView emergencyInfoCard;
    private TextView tvEmergencyTitle;
    private TextView tvEmergencyAddress;
    private TextView tvRescuerInfo;
    private Button btnCloseMap;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_google_map);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize UI elements
        initializeUI();

        // Get data from Intent
        Intent intent = getIntent();
        if (intent != null) {
            seniorLat = intent.getDoubleExtra("latitude", 0.0);
            seniorLong = intent.getDoubleExtra("longitude", 0.0);
            seniorAddress = intent.getStringExtra("locationAddress");
            seniorName = intent.getStringExtra("seniorName");
            helpRequestId = intent.getStringExtra("helpRequestIdForTracking");

            Log.d(TAG, "Received senior location: " + seniorLat + ", " + seniorLong);
            Log.d(TAG, "Help request ID: " + helpRequestId);
        }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Register location permission launcher
        registerLocationPermissionLauncher();

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.seniorMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initializeUI() {
        emergencyInfoCard = findViewById(R.id.emergencyInfoCard);
        tvEmergencyTitle = findViewById(R.id.tvEmergencyTitle);
        tvEmergencyAddress = findViewById(R.id.tvEmergencyAddress);
        tvRescuerInfo = findViewById(R.id.tvRescuerInfo);
        btnCloseMap = findViewById(R.id.btnCloseMap);

        if (btnCloseMap != null) {
            btnCloseMap.setOnClickListener(v -> {
                finish();
            });
        }

        if (tvEmergencyTitle != null) {
            tvEmergencyTitle.setText("🚨 Emergency Help Request");
        }
    }

    private void registerLocationPermissionLauncher() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    
                    if (fineLocationGranted != null && fineLocationGranted) {
                        Log.d(TAG, "Fine location permission granted");
                        getCurrentLocation();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        Log.d(TAG, "Coarse location permission granted");
                        getCurrentLocation();
                    } else {
                        Log.d(TAG, "Location permission denied");
                        Toast.makeText(this, "Location permission is required to show your position", Toast.LENGTH_LONG).show();
                    }
                });
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

        // Display senior location
        displaySeniorLocation();

        // Get current location and start tracking
        getCurrentLocation();
        
        // Start tracking rescuers
        if (helpRequestId != null && !helpRequestId.isEmpty()) {
            Log.d(TAG, "Starting rescuer tracking for help request: " + helpRequestId);
            startRescuerTracking();
            startArrivalNotificationListener();
        }
    }

    private void displaySeniorLocation() {
        Log.d(TAG, "displaySeniorLocation called with: " + seniorLat + ", " + seniorLong);
        
        if (myMap == null) {
            Log.e(TAG, "Map is null in displaySeniorLocation");
            return;
        }

        if (seniorLat == 0.0 || seniorLong == 0.0) {
            Log.e(TAG, "Invalid senior location coordinates");
            return;
        }

        LatLng seniorLocation = new LatLng(seniorLat, seniorLong);

        // Add blue marker for senior location
        MarkerOptions markerOptions = new MarkerOptions()
                .position(seniorLocation)
                .title("📍 Your Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));

        if (seniorAddress != null && !seniorAddress.isEmpty()) {
            markerOptions.snippet("📍 " + seniorAddress);
        }

        myMap.addMarker(markerOptions);
        
        // Move camera to the senior location
        myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(seniorLocation, 15f));
        
        // Update UI
        if (tvEmergencyAddress != null) {
            tvEmergencyAddress.setText("📍 " + (seniorAddress != null ? seniorAddress : "Your current location"));
        }
        
        Log.d(TAG, "Senior marker added and camera moved to location");
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        Log.d(TAG, "Got current location: " + location.getLatitude() + ", " + location.getLongitude());
                        // Update senior location if needed
                        if (seniorLat == 0.0 || seniorLong == 0.0) {
                            seniorLat = location.getLatitude();
                            seniorLong = location.getLongitude();
                            displaySeniorLocation();
                        }
                    } else {
                        Log.d(TAG, "No last known location, starting location updates");
                        startLocationUpdates();
                    }
                });
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
                        Log.d(TAG, "Location updated: " + location.getLatitude() + ", " + location.getLongitude());
                        
                        // Update senior location if needed
                        if (seniorLat == 0.0 || seniorLong == 0.0) {
                            seniorLat = location.getLatitude();
                            seniorLong = location.getLongitude();
                            displaySeniorLocation();
                        }
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            locationUpdatesActive = true;
            Log.d(TAG, "Location updates started");
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

    private void startRescuerTracking() {
        Log.d(TAG, "startRescuerTracking called for help request: " + helpRequestId);
        
        if (helpRequestId == null || helpRequestId.isEmpty()) {
            Log.e(TAG, "No help request ID for tracking");
            return;
        }

        // Listen for help request status updates
        helpRequestListener = db.collection("Sagip")
            .document("helpRequests")
            .collection("activeRequests")
            .document(helpRequestId)
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
                        if (rescuerLocationListener != null) {
                            rescuerLocationListener.remove();
                        }
                    }
                }
            });

        // Listen for rescuer location updates
        startRescuerLocationTracking();
    }

    private void startRescuerLocationTracking() {
        Log.d(TAG, "startRescuerLocationTracking called for help request: " + helpRequestId);
        
        rescuerLocationListener = db.collection("Sagip")
            .document("helpRequests")
            .collection("activeRequests")
            .document(helpRequestId)
            .collection("rescuerLocations")
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error listening to rescuer location updates", e);
                    return;
                }

                if (snapshot != null && !snapshot.isEmpty()) {
                    Log.d(TAG, "Received " + snapshot.size() + " rescuer location updates");
                    
                    // Clear existing rescuer markers and routes
                    clearRescuerMarkersAndRoutes();
                    
                    // Process each rescuer location update
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (document.exists()) {
                            updateRescuerMarkerAndRoute(document);
                        }
                    }
                } else {
                    Log.d(TAG, "No rescuer locations found");
                    updateRescuerInfo("⏳ Waiting for rescuers to respond...");
                }
            });
    }

    private void clearRescuerMarkersAndRoutes() {
        // Clear existing rescuer markers
        for (Marker marker : rescuerMarkers.values()) {
            marker.remove();
        }
        rescuerMarkers.clear();
        rescuerNames.clear();
        rescuerPhones.clear();
        rescuerTeams.clear();
        
        // Clear existing rescuer routes
        for (Polyline route : rescuerRoutes.values()) {
            route.remove();
        }
        rescuerRoutes.clear();
    }

    private void updateRescuerMarkerAndRoute(DocumentSnapshot document) {
        try {
            String rescuerId = document.getId();
            Double latitude = document.getDouble("latitude");
            Double longitude = document.getDouble("longitude");
            String rescuerName = document.getString("rescuerName");
            String rescuerTeam = document.getString("rescuerTeam");
            Long timestamp = document.getLong("timestamp");
            
            if (latitude != null && longitude != null) {
                LatLng rescuerLocation = new LatLng(latitude, longitude);
                
                // Update or create rescuer marker
                Marker existingMarker = rescuerMarkers.get(rescuerId);
                if (existingMarker != null) {
                    // Update existing marker position
                    existingMarker.setPosition(rescuerLocation);
                } else {
                    // Create new marker with ambulance icon for rescuer
                    String markerTitle = "🚑 Rescuer: " + (rescuerName != null ? rescuerName : "Unknown");
                    if (rescuerTeam != null && !rescuerTeam.isEmpty()) {
                        markerTitle += "\n🏥 Team: " + rescuerTeam;
                    }
                    
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(rescuerLocation)
                            .title(markerTitle)
                            .snippet("Click to see route")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_ambulance_rescuer));
                    
                    Marker marker = myMap.addMarker(markerOptions);
                    rescuerMarkers.put(rescuerId, marker);
                    rescuerNames.put(rescuerId, rescuerName != null ? rescuerName : "Unknown");
                    if (rescuerTeam != null) {
                        rescuerTeams.put(rescuerId, rescuerTeam);
                    }
                }
                
                // Show route from rescuer to senior location
                if (seniorLat != 0.0 && seniorLong != 0.0) {
                    showRouteFromRescuerToSenior(rescuerLocation, rescuerId);
                }
                
                // Update UI with rescuer information
                updateRescuerInfo();
                
                Log.d(TAG, "Updated rescuer marker for: " + rescuerName + " at " + latitude + ", " + longitude);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating rescuer marker", e);
        }
    }

    private void showRouteFromRescuerToSenior(LatLng rescuerLocation, String rescuerId) {
        if (seniorLat == 0.0 || seniorLong == 0.0) {
            Log.e(TAG, "Senior location not available for routing");
            return;
        }
        
        LatLng seniorLocation = new LatLng(seniorLat, seniorLong);
        
        // Get route from Google Directions API
        getRouteFromGoogleDirections(rescuerLocation, seniorLocation, rescuerId);
    }

    private void getRouteFromGoogleDirections(LatLng origin, LatLng destination, String rescuerId) {
        // Use a background thread for API call
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                        "origin=" + origin.latitude + "," + origin.longitude +
                        "&destination=" + destination.latitude + "," + destination.longitude +
                        "&key=" + getString(R.string.google_maps_key);
                
                Log.d(TAG, "Requesting route from Google Directions API");
                
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject jsonResponse = new JSONObject(response.toString());
                
                if (jsonResponse.getString("status").equals("OK")) {
                    JSONArray routes = jsonResponse.getJSONArray("routes");
                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                        String encodedPolyline = overviewPolyline.getString("points");
                        
                        // Decode polyline and draw route on main thread
                        runOnUiThread(() -> drawRouteFromPolyline(encodedPolyline, rescuerId));
                    }
                } else {
                    Log.e(TAG, "Google Directions API error: " + jsonResponse.getString("status"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting route from Google Directions API", e);
            }
        });
    }

    private void drawRouteFromPolyline(String encodedPolyline, String rescuerId) {
        try {
            List<LatLng> routePoints = decodePolyline(encodedPolyline);
            
            if (routePoints.size() > 1) {
                // Remove existing route for this rescuer
                Polyline existingRoute = rescuerRoutes.get(rescuerId);
                if (existingRoute != null) {
                    existingRoute.remove();
                }
                
                // Create new route with different colors for different rescuers
                int routeColor = getRouteColorForRescuer(rescuerId);
                
                PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(routePoints)
                        .color(routeColor)
                        .width(8)
                        .pattern(Arrays.asList(new Dash(20), new Gap(10))); // Dashed line
                
                Polyline route = myMap.addPolyline(polylineOptions);
                
                // Store route for this specific rescuer
                rescuerRoutes.put(rescuerId, route);
                
                Log.d(TAG, "Drawn route for rescuer: " + rescuerId + " with color: " + Integer.toHexString(routeColor));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error drawing route", e);
        }
    }

    private int getRouteColorForRescuer(String rescuerId) {
        // Assign different colors for different rescuers
        int hash = rescuerId.hashCode();
        int[] colors = {
            0xFF00FF00, // Green
            0xFF0000FF, // Blue
            0xFFFF0000, // Red
            0xFFFFFF00, // Yellow
            0xFFFF00FF, // Magenta
            0xFF00FFFF, // Cyan
            0xFFFFA500, // Orange
            0xFF800080  // Purple
        };
        return colors[Math.abs(hash) % colors.length];
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

            LatLng p = new LatLng(lat / 1E5, lng / 1E5);
            poly.add(p);
        }
        return poly;
    }

    private void updateRescuerInfo() {
        updateRescuerInfo(null);
    }

    private void startArrivalNotificationListener() {
        Log.d(TAG, "Starting arrival notification listener for help request: " + helpRequestId);
        
        db.collection("Sagip")
            .document("seniorNotifications")
            .collection("arrivalNotifications")
            .document(helpRequestId)
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error listening to arrival notifications", e);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    String type = snapshot.getString("type");
                    if ("rescuer_arrived".equals(type)) {
                        String title = snapshot.getString("title");
                        String message = snapshot.getString("message");
                        String rescuerName = snapshot.getString("rescuerName");
                        String rescuerTeam = snapshot.getString("rescuerTeam");
                        
                        Log.d(TAG, "Rescuer arrival notification received: " + rescuerName);
                        
                        // Show arrival notification to senior
                        showArrivalNotification(title, message, rescuerName, rescuerTeam);
                    }
                }
            });
    }

    private void showArrivalNotification(String title, String message, String rescuerName, String rescuerTeam) {
        // Create a custom dialog for arrival notification
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        
        // Make the dialog prominent
        builder.setPositiveButton("✅ OK", (dialog, which) -> {
            dialog.dismiss();
            // Update UI to show rescuer has arrived
            updateRescuerInfo("🚑 " + rescuerName + " from " + (rescuerTeam != null ? rescuerTeam : "Rescue Team") + " has arrived!");
        });
        
        // Make dialog not cancelable so senior must acknowledge
        builder.setCancelable(false);
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make the dialog more prominent
        if (dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }
    }

    private void updateRescuerInfo(String customMessage) {
        if (tvRescuerInfo != null) {
            if (customMessage != null) {
                tvRescuerInfo.setText(customMessage);
            } else {
                int rescuerCount = rescuerMarkers.size();
                if (rescuerCount > 0) {
                    StringBuilder rescuerInfo = new StringBuilder();
                    rescuerInfo.append("🚑 ").append(rescuerCount).append(" rescuer(s) responding:\n");
                    
                    for (String rescuerId : rescuerNames.keySet()) {
                        String rescuerName = rescuerNames.get(rescuerId);
                        String rescuerTeam = rescuerTeams.get(rescuerId);
                        rescuerInfo.append("• ").append(rescuerName);
                        if (rescuerTeam != null && !rescuerTeam.isEmpty()) {
                            rescuerInfo.append(" (").append(rescuerTeam).append(")");
                        }
                        rescuerInfo.append("\n");
                    }
                    
                    tvRescuerInfo.setText(rescuerInfo.toString());
                } else {
                    tvRescuerInfo.setText("⏳ Waiting for rescuers to respond...");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationUpdatesActive && fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationUpdatesActive = false;
        }
        
        // Remove Firebase listeners
        if (rescuerLocationListener != null) {
            rescuerLocationListener.remove();
        }
        if (helpRequestListener != null) {
            helpRequestListener.remove();
        }
        
        // Clear all rescuer markers and routes
        clearRescuerMarkersAndRoutes();
        
        Log.d(TAG, "Senior_GoogleMap destroyed and cleaned up");
    }
}
