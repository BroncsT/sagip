package com.example.sagip_prototype;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.sagip_prototype.models.Hospital;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.SetOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Rescuer_Dashboard extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "RescuerDashboard";
    
    // MediaPlayer for emergency sound playback
    private MediaPlayer currentEmergencySoundPlayer = null;
    
    // Emergency item class for FIFO queue management
    private static class EmergencyItem {
        String title;
        String message;
        String seniorName;
        String seniorPhone;
        String locationAddress;
        Double latitude;
        Double longitude;
        String helpRequestId;
        String emergencyId;
        long timestamp;
        int priority; // Higher number = higher priority
        int queuePosition; // Position in FIFO queue (1-based)
        long queueEntryTime; // When this item entered the queue
        double distance; // Distance from rescuer in km
        
        EmergencyItem(String title, String message, String seniorName, String seniorPhone,
                     String locationAddress, Double latitude, Double longitude, 
                     String helpRequestId, String emergencyId, int priority, int queuePosition, double distance) {
            this.title = title;
            this.message = message;
            this.seniorName = seniorName;
            this.seniorPhone = seniorPhone;
            this.locationAddress = locationAddress;
            this.latitude = latitude;
            this.longitude = longitude;
            this.helpRequestId = helpRequestId;
            this.emergencyId = emergencyId;
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
            this.queuePosition = queuePosition;
            this.queueEntryTime = System.currentTimeMillis();
            this.distance = distance;
        }
        
        // Get time spent in queue
        public long getTimeInQueue() {
            return System.currentTimeMillis() - queueEntryTime;
        }
        
        // Get formatted queue position
        public String getQueuePositionText() {
            return "#" + queuePosition;
        }
        
        // Get formatted distance text
        public String getDistanceText() {
            if (distance < 1.0) {
                return String.format("%.0f m", distance * 1000);
            } else {
                return String.format("%.1f km", distance);
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        rescuerMap = googleMap;
        rescuerMap.getUiSettings().setZoomControlsEnabled(true);
        rescuerMap.getUiSettings().setCompassEnabled(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                rescuerMap.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {}
        }

        // Center if we already have location
        if (currentLat != 0.0 && currentLong != 0.0) {
            LatLng here = new LatLng(currentLat, currentLong);
            rescuerMap.moveCamera(CameraUpdateFactory.newLatLngZoom(here, 14f));
        }

        // Add marker click listener for route functionality
        rescuerMap.setOnMarkerClickListener(marker -> {
            // Check if it's a hospital marker (not current location)
            if (!marker.getTitle().equals("You are here")) {
                showRouteToHospital(marker);
                return true; // Consume the event
            }
            return false; // Let default behavior handle other markers
        });

        // Load hospitals
        loadAndRenderNearbyHospitals();
    }

    private void loadAndRenderNearbyHospitals() {
        if (db == null || rescuerMap == null) return;

        // Store current route points before clearing
        List<LatLng> savedRoutePoints = currentRoutePoints;
        
        // Clear existing markers
        rescuerMap.clear();
        
        // Redraw the route if it existed
        if (savedRoutePoints != null && !savedRoutePoints.isEmpty()) {
            redrawRoute(savedRoutePoints);
        }

        // Add current location marker
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        final boolean[] hasAnyPoint = new boolean[]{false};
        if (!(currentLat == 0.0 && currentLong == 0.0)) {
            LatLng here = new LatLng(currentLat, currentLong);
            rescuerMap.addMarker(new MarkerOptions()
                    .position(here)
                    .title("You are here")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            boundsBuilder.include(here);
            hasAnyPoint[0] = true;
        }

        // Query Firestore hospitals (all)
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .get()
                .addOnSuccessListener(query -> {
                    boolean hasHospitals = false;
                    int totalDocs = query.size();
                    Log.d(TAG, "Hospitals query returned: " + totalDocs + " documents");
                    final boolean[] infoWindowShown = new boolean[]{false};
                    for (QueryDocumentSnapshot doc : query) {
                        // Prefer explicit fields to avoid model mismatch
                        com.google.firebase.firestore.GeoPoint geo = doc.getGeoPoint("currentLocation");
                        if (geo == null) geo = doc.getGeoPoint("location");
                        if (geo == null) geo = doc.getGeoPoint("hospitalLocation");
                        String name = doc.getString("name");
                        if (name == null) name = doc.getString("hospitalName");
                        String address = doc.getString("address");

                        if (geo != null) {
                            LatLng pos = new LatLng(geo.getLatitude(), geo.getLongitude());
                            com.google.android.gms.maps.model.Marker marker = rescuerMap.addMarker(new MarkerOptions()
                                    .position(pos)
                                    .title(name != null ? name : "Hospital")
                                    .snippet(address != null ? address : "")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            );
                            if (!infoWindowShown[0] && marker != null) {
                                marker.showInfoWindow();
                                infoWindowShown[0] = true;
                            }
                            boundsBuilder.include(pos);
                            hasHospitals = true;
                            hasAnyPoint[0] = true;
                        } else {
                            Log.w(TAG, "Hospital doc missing location: " + doc.getId());
                        }
                    }
                    if (hasAnyPoint[0]) {
                        try {
                            LatLngBounds bounds = boundsBuilder.build();
                            rescuerMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80));
                        } catch (IllegalStateException ignored) {}
                    }
                    if (!hasHospitals) {
                        Log.w(TAG, "No hospitals found to display on map");
                        android.widget.Toast.makeText(this, getString(R.string.no_hospitals_found_or_missing_locations), android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load hospitals", e));
    }

    private void showRouteToHospital(Marker hospitalMarker) {
        if (currentLat == 0.0 || currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.current_location_not_available_wait_for_update), Toast.LENGTH_SHORT).show();
            return;
        }

        LatLng hospitalLocation = hospitalMarker.getPosition();
        LatLng currentLocation = new LatLng(currentLat, currentLong);
        
        // Clear existing route
        if (currentRoute != null) {
            currentRoute.remove();
            currentRoute = null;
        }

        // Show route options dialog
        showRouteOptionsDialog(hospitalMarker, hospitalLocation, currentLocation);
    }

    private void showRouteOptionsDialog(Marker hospitalMarker, LatLng hospitalLocation, LatLng currentLocation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.format(getString(R.string.route_to_hospital), hospitalMarker.getTitle()));
        builder.setMessage(getString(R.string.choose_how_to_navigate_hospital));
        
        builder.setPositiveButton(getString(R.string.show_route_on_map), (dialog, which) -> {
            // Get directions using Google Directions API
            String directionsUrl = buildDirectionsUrl(currentLocation, hospitalLocation);
            executeDirectionsRequest(directionsUrl);
            Toast.makeText(this, String.format(getString(R.string.getting_route_to), hospitalMarker.getTitle()), Toast.LENGTH_SHORT).show();
        });
        
        builder.setNeutralButton(getString(R.string.open_google_maps), (dialog, which) -> {
            openGoogleMapsNavigation(hospitalLocation, hospitalMarker.getTitle());
        });
        
        builder.setNegativeButton(getString(R.string.center_on_hospital), (dialog, which) -> {
            rescuerMap.animateCamera(CameraUpdateFactory.newLatLngZoom(hospitalLocation, 16f));
            Toast.makeText(this, String.format(getString(R.string.centered_on), hospitalMarker.getTitle()), Toast.LENGTH_SHORT).show();
        });
        
        // Add a fourth option for route info only
        builder.setNeutralButton(getString(R.string.route_info_only), (dialog, which) -> {
            getRouteInfoOnly(currentLocation, hospitalLocation, hospitalMarker.getTitle());
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openGoogleMapsNavigation(LatLng destination, String hospitalName) {
        try {
            // Create Google Maps navigation intent
            String navigationUri = String.format(Locale.getDefault(), "google.navigation:q=%f,%f&mode=d", 
                destination.latitude, destination.longitude);
            Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
            navigationIntent.setPackage("com.google.android.apps.maps");
            
            if (navigationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navigationIntent);
                Toast.makeText(this, "🚗 Opening Google Maps navigation to " + hospitalName, Toast.LENGTH_LONG).show();
            } else {
                // Fallback to web-based Google Maps
                String webMapsUri = String.format(Locale.getDefault(), "https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving", 
                    destination.latitude, destination.longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webMapsUri));
                startActivity(webIntent);
                Toast.makeText(this, "🌐 Opening web-based navigation to " + hospitalName, Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Maps navigation", e);
            Toast.makeText(this, getString(R.string.error_opening_navigation), Toast.LENGTH_SHORT).show();
        }
    }

    private void getRouteInfoOnly(LatLng origin, LatLng destination, String hospitalName) {
        String directionsUrl = buildDirectionsUrl(origin, destination);
        executorService.execute(() -> {
            try {
                String jsonResponse = makeDirectionsRequest(directionsUrl);
                if (jsonResponse != null) {
                    runOnUiThread(() -> parseRouteInfoOnly(jsonResponse, hospitalName));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting route info", e);
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_getting_route_information), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void parseRouteInfoOnly(String jsonResponse, String hospitalName) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routes = jsonObject.getJSONArray("routes");
            
            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                JSONArray legs = route.getJSONArray("legs");
                if (legs.length() > 0) {
                    JSONObject leg = legs.getJSONObject(0);
                    JSONObject distance = leg.getJSONObject("distance");
                    JSONObject duration = leg.getJSONObject("duration");
                    
                    String distanceText = distance.getString("text");
                    String durationText = duration.getString("text");
                    showRouteInfoDialog(hospitalName, distanceText, durationText);
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing route info", e);
            Toast.makeText(this, getString(R.string.error_parsing_route_information), Toast.LENGTH_SHORT).show();
        }
    }

    private void showRouteInfoDialog(String hospitalName, String distance, String duration) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.route_information_title));
        builder.setMessage(String.format(getString(R.string.route_information_details), 
                          hospitalName, distance, duration));
        
        builder.setPositiveButton(getString(R.string.show_route_button), (dialog, which) -> {
            // This will trigger the route display
            // We need to store the hospital location for this
            Toast.makeText(this, getString(R.string.route_will_be_displayed), Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(getString(R.string.close_button), (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
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
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.error_getting_route), Toast.LENGTH_SHORT).show());
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
                
                // Get polyline points
                JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                String encodedPolyline = overviewPolyline.getString("points");
                
                // Decode and display the route
                List<LatLng> routePoints = decodePolyline(encodedPolyline);
                displayRoute(routePoints);
                
                // Get distance and duration for display
                JSONArray legs = route.getJSONArray("legs");
                if (legs.length() > 0) {
                    JSONObject leg = legs.getJSONObject(0);
                    JSONObject distance = leg.getJSONObject("distance");
                    JSONObject duration = leg.getJSONObject("duration");
                    
                    String distanceText = distance.getString("text");
                    String durationText = duration.getString("text");
                    
                    // Update route info text
                    if (routeInfoText != null) {
                        routeInfoText.setText("📍 " + distanceText + " • ⏱️ " + durationText);
                    }
                    
                    Toast.makeText(this, String.format(getString(R.string.route_distance_duration), distanceText, durationText), Toast.LENGTH_LONG).show();
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing directions response", e);
            Toast.makeText(this, getString(R.string.error_parsing_route_data), Toast.LENGTH_SHORT).show();
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
        if (rescuerMap == null || routePoints == null || routePoints.isEmpty()) {
            return;
        }

        // Store route points for persistence
        currentRoutePoints = new ArrayList<>(routePoints);

        // Clear existing route
        if (currentRoute != null) {
            currentRoute.remove();
        }

        // Add new route polyline
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .width(8)
                .color(0xFF1976D2) // Blue color
                .geodesic(true);

        currentRoute = rescuerMap.addPolyline(polylineOptions);

        // Show route control panel
        if (routeControlPanel != null) {
            routeControlPanel.setVisibility(View.VISIBLE);
        }
        if (routeInfoText != null) {
            routeInfoText.setText("🗺️ Route active - " + routePoints.size() + " waypoints");
        }

        Log.d(TAG, "Route displayed with " + routePoints.size() + " points");
    }

    private void redrawRoute(List<LatLng> routePoints) {
        if (rescuerMap == null || routePoints == null || routePoints.isEmpty()) {
            return;
        }

        // Add route polyline
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .width(8)
                .color(0xFF1976D2) // Blue color
                .geodesic(true);

        currentRoute = rescuerMap.addPolyline(polylineOptions);

        // Show route control panel
        if (routeControlPanel != null) {
            routeControlPanel.setVisibility(View.VISIBLE);
        }
        if (routeInfoText != null) {
            routeInfoText.setText("🗺️ Route active - " + routePoints.size() + " waypoints");
        }

        Log.d(TAG, "Route redrawn with " + routePoints.size() + " points");
    }

    private void clearRoute() {
        if (currentRoute != null) {
            currentRoute.remove();
            currentRoute = null;
        }

        // Clear stored route points
        currentRoutePoints = null;
        
        // Hide route control panel
        if (routeControlPanel != null) {
            routeControlPanel.setVisibility(View.GONE);
        }
    }
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;
    private static final String PREF_NAME = "SagipAppPrefs";
    
    // Track if emergency dialog is currently showing to prevent duplicates
    private static boolean isEmergencyDialogShowing = false;
    private static final Object dialogLock = new Object(); // Synchronization lock for dialog state
    
    /**
     * Safely resets the emergency dialog state
     */
    private static void resetEmergencyDialogState() {
        synchronized (dialogLock) {
            isEmergencyDialogShowing = false;
            Log.d(TAG, "🔍 [RESET_STATE] Emergency dialog state reset");
        }
    }
    
    /**
     * Safely checks if emergency dialog is showing
     */
    private static boolean isEmergencyDialogCurrentlyShowing() {
        synchronized (dialogLock) {
            return isEmergencyDialogShowing;
        }
    }
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_PHONE = "userPhone";
    private static final String KEY_CACHED_DISPLAY_NAME = "cachedDisplayName";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView brgyName;
    private TextView currentLocationText;
    private LinearLayout routeControlPanel;
    private TextView routeInfoText;
    private Button btnClearRoute;

    private long lastTapTime = 0;
    private String userType = "rescuer";
    private String userId;
    private SharedPreferences sharedPreferences;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private double currentLat = 0.0;
    private double currentLong = 0.0;

	// Map
	private GoogleMap rescuerMap;
	
	// Route functionality
	private Polyline currentRoute = null;
	private List<LatLng> currentRoutePoints = null;
	private ExecutorService executorService;
	private static final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";


    // Emergency notification system variables
    private ListenerRegistration emergencyListener;
    private ListenerRegistration emergencySOSListener; // Track emergency SOS listener
    private long lastLoginTime; // Track when rescuer logged in
    private AlertDialog currentEmergencyDialog; // Track current emergency popup
    private String currentEmergencyRequestId; // Track which emergency the dialog is showing
    
    // SOS Emergency List - REMOVED
    private androidx.recyclerview.widget.RecyclerView sosEmergencyRecyclerView;
    private SOSEmergencyAdapter sosEmergencyAdapter;
    private TextView noSOSEmergenciesText;
    private android.os.Handler emergencyListUpdateHandler;
    private Runnable emergencyListUpdateRunnable;
    
    // FIFO Emergency queue system for handling multiple simultaneous emergencies
    private Queue<EmergencyItem> emergencyQueue = new LinkedList<>(); // FIFO implementation
    private boolean isProcessingEmergency = false;
    private int totalActiveEmergencies = 0;
    private long queueStartTime = 0; // Track when first emergency was added
    
    /**
     * Queues an emergency for processing to prevent conflicts
     */
    private void queueEmergencyForProcessing(String seniorName, String seniorPhone, String locationAddress, 
                                           Long timestamp, String requestId, Double seniorLat, Double seniorLng) {
        // Create emergency item with proper parameters for the constructor
        String title = "🚨 EMERGENCY HELP REQUEST";
        String message = seniorName + " needs immediate assistance!";
        EmergencyItem emergency = new EmergencyItem(title, message, seniorName, seniorPhone, locationAddress, 
                                                   seniorLat, seniorLng, requestId, requestId, 1, 1, 0.0);
        
        synchronized (emergencyQueue) {
            emergencyQueue.offer(emergency);
            totalActiveEmergencies++;
            
            if (queueStartTime == 0) {
                queueStartTime = System.currentTimeMillis();
            }
            
            Log.d(TAG, "🚨 [QUEUE] Added emergency to queue. Queue size: " + emergencyQueue.size() + 
                  ", Total active: " + totalActiveEmergencies);
        }
        
        // Process queue if not already processing
        if (!isProcessingEmergency) {
            processEmergencyQueue();
        }
    }
    
    /**
     * Processes the emergency queue one by one
     */
    private void processEmergencyQueue() {
        if (isProcessingEmergency) {
            return; // Already processing
        }
        
        isProcessingEmergency = true;
        
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            EmergencyItem emergency;
            
            synchronized (emergencyQueue) {
                emergency = emergencyQueue.poll();
            }
            
            if (emergency != null) {
                Log.d(TAG, "🚨 [PROCESS] Processing emergency from queue: " + emergency.seniorName);
                
                // Show the emergency dialog
                if (emergency.latitude != null && emergency.longitude != null) {
                    showEmergencySOSAlertWithLocation(emergency.seniorName, emergency.seniorPhone, 
                                                    emergency.locationAddress, emergency.timestamp, 
                                                    emergency.helpRequestId, emergency.latitude, emergency.longitude);
                } else {
                    showEmergencySOSAlert(emergency.seniorName, emergency.seniorPhone, 
                                        emergency.locationAddress, emergency.timestamp, emergency.helpRequestId);
                }
                
                // Process next emergency after a delay
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    isProcessingEmergency = false;
                    processEmergencyQueue(); // Process next emergency
                }, 2000); // 2 second delay between emergencies
                
            } else {
                isProcessingEmergency = false;
                Log.d(TAG, "🚨 [QUEUE] No more emergencies to process");
            }
        });
    }
    
    /**
     * Emergency item class for queue management
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // STOP ALL EMERGENCY SOUNDS AND DISMISS NOTIFICATIONS IMMEDIATELY
        Intent intent = getIntent();
        if (intent != null) {
            boolean isFromNotification = intent.getBooleanExtra("emergency_sos_clicked", false) || 
                                       intent.getBooleanExtra("from_emergency_notification", false);
            if (isFromNotification) {
                // Cancel ALL notifications immediately to stop the notification sound
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.cancelAll();
                    Log.d(TAG, "🔕 [ON_CREATE] Canceled all notifications to stop sound");
                }
                
                // Stop MediaPlayer sounds
                stopEmergencySound();
                EmergencySOSBackgroundService.dismissAllEmergencyNotifications();
                Log.d(TAG, "🔇 [ON_CREATE] All emergency sounds stopped immediately in onCreate");
            }
        }
        
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_rescuer_dashboard);

		SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.rescuerMapContainer);
		if (mapFragment == null) {
			mapFragment = SupportMapFragment.newInstance();
			getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.rescuerMapContainer, mapFragment)
				.commit();
		}
		mapFragment.getMapAsync(this);
		// Initialize location services

		// Optionally preload last known location to center map faster
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {
			// Ensure fusedLocationClient is initialized before use
			if (fusedLocationClient == null) {
				fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
			}
			fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
				if (location != null) {
					currentLat = location.getLatitude();
					currentLong = location.getLongitude();
					if (rescuerMap != null) {
						rescuerMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLong), 14f));
					}
					// Save initial location to Firestore
				}
			});
		}

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        // Set login time to current time
        lastLoginTime = System.currentTimeMillis();

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        brgyName = findViewById(R.id.barangayStaffName);
        currentLocationText = findViewById(R.id.currentLocationValue);
        
        // Initialize route control panel
        routeControlPanel = findViewById(R.id.routeControlPanel);
        routeInfoText = findViewById(R.id.routeInfoText);
        btnClearRoute = findViewById(R.id.btnClearRoute);
        
        // Set up clear route button
        btnClearRoute.setOnClickListener(v -> clearRoute());
        
        // Initialize SOS Emergency List - REMOVED
        // sosEmergencyRecyclerView = findViewById(R.id.sosEmergencyRecyclerView);
        // noSOSEmergenciesText = findViewById(R.id.noSOSEmergenciesText);
        // setupSOSEmergencyList();
        // startEmergencyListUpdates();
        
        // Initialize executor service for route requests
        executorService = Executors.newSingleThreadExecutor();
        
        // Initialize emergency queue manager
        EmergencyQueueManager.getInstance(this).loadActiveEmergenciesFromDatabase();
        
        // Cleanup old assigned emergencies to prevent duplicate SOS notifications
        Log.d(TAG, "🧹 Starting automatic cleanup of old emergencies...");
        EmergencyQueueManager.getInstance(this).cleanupOldAssignedEmergencies();
        
        // Handle emergency notification if app was opened from notification
        handleEmergencyNotificationIntent();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize location components immediately in onCreate
        createLocationRequest();
        createLocationCallback();

        // Initialize emergency notification components
        initializeEmergencyNotificationComponents();
        checkLocationPermission();

        // Check authentication state
        checkAuthState();

        // Initialize FCM token for notifications
        initializeFCMToken();
        
        // Setup test SMS button (for debugging)
        setupTestSMSButton();

        createNotificationChannel();
        
        // CRITICAL: Request notification permission for Android 13+ (API 33+)
        checkAndRequestNotificationPermission();
        
        // Check if notification channel is enabled and prompt user if not
        checkNotificationChannelEnabled();

        // Clear any old emergency notifications on startup
        clearOldEmergencyNotifications();
        
        // Setup bottom navigation
        setupBottomNavigation();
    }

    private void clearOldEmergencyNotifications() {
        // Clear any system notifications that might be from old sessions
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "=== ON_NEW_INTENT CALLED ===");
        setIntent(intent);
        
        // STOP ALL EMERGENCY SOUNDS IMMEDIATELY when notification is clicked
        stopEmergencySound(); // Stop dashboard sound (with volume muting + buffer clearing)
        EmergencySOSBackgroundService.dismissAllEmergencyNotifications(); // Stop background service sound AND dismiss notifications
        cancelAllSystemNotifications(); // Cancel all system notifications to stop notification channel sounds
        Log.d(TAG, "🔇 [ON_NEW_INTENT] All emergency sounds stopped immediately (dashboard + background + notifications)");
        
        handleNotificationClick();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Load cached display name immediately when returning to dashboard
        loadCachedDisplayName();

        // Check if opened from notification and stop sounds immediately
        Intent intent = getIntent();
        if (intent != null) {
            boolean isFromNotification = intent.getBooleanExtra("emergency_sos_clicked", false) || 
                                       intent.getBooleanExtra("from_emergency_notification", false);
            if (isFromNotification) {
                // STOP ALL EMERGENCY SOUNDS IMMEDIATELY when opened from notification
                stopEmergencySound(); // Stop dashboard sound (with volume muting + buffer clearing)
                EmergencySOSBackgroundService.dismissAllEmergencyNotifications(); // Stop background service sound AND dismiss notifications
                cancelAllSystemNotifications(); // Cancel all system notifications to stop notification channel sounds
                Log.d(TAG, "🔇 [ON_RESUME] All emergency sounds stopped on resume from notification (dashboard + background + notifications)");
            }
        }

        // Handle notification click if this activity was opened from a notification
        handleNotificationClick();

        // Add safety check and ensure components are initialized
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
            
            // Save initial location if available
            if (currentLat != 0 && currentLong != 0) {
                saveLocationToFirestore(currentLat, currentLong);
            }
        }
        
        // Check battery optimization for rescuers
        if (userType != null && userType.equals("rescuer")) {
            Log.d(TAG, "🔋 Checking battery optimization for rescuer");
            BatteryOptimizationHelper.checkAndShowBatteryOptimization(this, userType);
        }

        // Check if user is still logged in before starting emergency listener
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (executorService != null) {
            executorService.shutdown();
        }

        // Remove emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Remove emergency SOS listener
        if (emergencySOSListener != null) {
            emergencySOSListener.remove();
            emergencySOSListener = null;
        }
        
        // Stop emergency list updates - REMOVED
        // stopEmergencyListUpdates();

        // Clear any pending emergency alerts
        clearPendingEmergencyAlerts();
        
        // NOTE: Do NOT stop notification services here - they should continue running when app is closed
        // The foreground services (RescuerForegroundService, WebSocketNotificationService, etc.) 
        // started in MainActivity will continue running to handle notifications when app is closed
        
        // Clear tracking status when app is destroyed
    }

    private void clearPendingEmergencyAlerts() {
        // Clear any system notifications related to emergencies
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Cancel all emergency notifications
            notificationManager.cancelAll();
        }
        
        // Dismiss any active emergency popup dialog
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            currentEmergencyDialog = null;
            Log.d(TAG, "Dismissed emergency popup dialog");
        }
        
        // Clear emergency queue
        synchronized (emergencyQueue) {
            emergencyQueue.clear();
            totalActiveEmergencies = 0;
            queueStartTime = 0;
            isProcessingEmergency = false;
            Log.d(TAG, "Cleared emergency queue");
        }
        
        // Reset dialog state
        resetEmergencyDialogState();
    }

    private void handleEmergencyNotificationIntent() {
        // Check if this activity was opened from an emergency SOS notification
        Intent intent = getIntent();
        if (intent != null) {
            boolean isEmergencySOS = intent.getBooleanExtra("emergency_sos_clicked", false) || 
                                   intent.getBooleanExtra("from_emergency_notification", false);
            
            if (isEmergencySOS) {
                String seniorName = intent.getStringExtra("senior_name");
                String seniorPhone = intent.getStringExtra("senior_phone");
                String locationAddress = intent.getStringExtra("location_address");
                
                Log.d(TAG, "🚨 App opened from emergency SOS notification in onCreate - Senior: " + seniorName);
                
                // STOP ALL EMERGENCY SOUNDS IMMEDIATELY when notification is clicked
                stopEmergencySound(); // Stop dashboard sound
                EmergencySOSBackgroundService.dismissAllEmergencyNotifications(); // Stop background service sound AND dismiss notifications
                Log.d(TAG, "🔇 [NOTIFICATION_CLICK] All emergency sounds stopped immediately on notification click");
                
                // Show emergency alert dialog immediately after a short delay to ensure UI is ready
                if (seniorName != null && locationAddress != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        // Double-check activity is still valid before showing dialog
                        if (!isFinishing() && !isDestroyed()) {
                            showEmergencySOSAlert(seniorName, seniorPhone, locationAddress, System.currentTimeMillis());
                        } else {
                            Log.w(TAG, "Activity no longer valid, cannot show emergency dialog");
                        }
                    }, 500); // Reduced delay to 500ms for faster response
                }
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("emergency_sos_clicked");
                intent.removeExtra("from_emergency_notification");
                intent.removeExtra("senior_name");
                intent.removeExtra("senior_phone");
                intent.removeExtra("location_address");
            }
        }
    }

    private void handleNotificationClick() {
        // Check if this activity was opened from a notification click
        Intent intent = getIntent();
        if (intent != null) {
            String notificationType = intent.getStringExtra("notification_type");
            Log.d(TAG, "🔍 [NOTIFICATION_CLICK] Activity opened from notification - Type: " + notificationType);
            Log.d(TAG, "🔍 [NOTIFICATION_CLICK] Intent extras: " + intent.getExtras());
            Log.d(TAG, "🔍 [NOTIFICATION_CLICK] Emergency SOS clicked: " + intent.getBooleanExtra("emergency_sos_clicked", false));
            Log.d(TAG, "🔍 [NOTIFICATION_CLICK] From emergency notification: " + intent.getBooleanExtra("from_emergency_notification", false));
            
            if ("hospital_update".equals(notificationType) || "hospital_status_update".equals(notificationType)) {
                // Handle hospital status update notification
                String hospitalName = intent.getStringExtra("hospital_name");
                String hospitalStatus = intent.getStringExtra("hospital_status");
                int availableBeds = intent.getIntExtra("available_beds", 0);
                int availableDoctors = intent.getIntExtra("available_doctors", 0);
                
                Log.d(TAG, "Hospital status update notification - Hospital: " + hospitalName + 
                    ", Status: " + hospitalStatus + ", Beds: " + availableBeds + ", Doctors: " + availableDoctors);
                
                // Show hospital status update info
                showHospitalStatusUpdateDialog(hospitalName, hospitalStatus, availableBeds, availableDoctors);
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("notification_type");
                intent.removeExtra("hospital_name");
                intent.removeExtra("hospital_status");
                intent.removeExtra("available_beds");
                intent.removeExtra("available_doctors");
                
            } else if (intent.getBooleanExtra("assignment_confirmed", false)) {
                // Handle assignment confirmation notification
                String seniorName = intent.getStringExtra("senior_name");
                String locationAddress = intent.getStringExtra("location_address");
                
                Log.d(TAG, "App opened from assignment confirmation - Senior: " + seniorName);
                
                if (seniorName != null && locationAddress != null) {
                    // Show assignment confirmation dialog
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        // Double-check activity is still valid before showing dialog
                        if (!isFinishing() && !isDestroyed()) {
                            showRescuerAssignmentPopup(seniorName, locationAddress, mAuth.getCurrentUser().getUid(), null);
                        } else {
                            Log.w(TAG, "Activity no longer valid, cannot show assignment popup");
                        }
                    }, 500); // Reduced delay for faster response
                }
                
                // Clear the intent extras
                intent.removeExtra("assignment_confirmed");
                intent.removeExtra("senior_name");
                intent.removeExtra("location_address");
                
            } else if (intent.getBooleanExtra("emergency_sos_clicked", false) || intent.getBooleanExtra("from_emergency_notification", false)) {
                // Handle emergency SOS notification - app opened from closed state
                String seniorName = intent.getStringExtra("senior_name");
                String seniorPhone = intent.getStringExtra("senior_phone");
                String locationAddress = intent.getStringExtra("location_address");
                String requestId = intent.getStringExtra("request_id");
                String notificationId = intent.getStringExtra("notification_id");
                Double seniorLat = intent.getDoubleExtra("senior_lat", 0.0);
                Double seniorLng = intent.getDoubleExtra("senior_lng", 0.0);
                
                Log.d(TAG, "🚨 App opened from emergency SOS notification - Senior: " + seniorName);
                Log.d(TAG, "📍 GPS coordinates from notification click: " + seniorLat + ", " + seniorLng);
                Log.d(TAG, "📋 RequestId: " + requestId + ", NotificationId: " + notificationId);
                
                // CRITICAL FIX: Reset dialog state and dismiss any existing dialog before showing new one
                synchronized (dialogLock) {
                    Log.d(TAG, "🔧 [NOTIFICATION_CLICK_FIX] Current dialog state: isEmergencyDialogShowing=" + isEmergencyDialogShowing);
                    if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
                        Log.d(TAG, "🔧 [NOTIFICATION_CLICK_FIX] Dismissing existing dialog");
                        currentEmergencyDialog.dismiss();
                    }
                    isEmergencyDialogShowing = false;
                    currentEmergencyRequestId = null;
                    currentEmergencyDialog = null;
                    Log.d(TAG, "🔧 [NOTIFICATION_CLICK_FIX] Dialog state reset - ready to show new dialog");
                }
                
                // If we have a notificationId, fetch fresh data from database
                if (notificationId != null && userId != null) {
                    Log.d(TAG, "🔧 [NOTIFICATION_CLICK_FIX] Fetching fresh notification data from database: " + notificationId);
                    db.collection("Sagip")
                        .document("users")
                        .collection("rescuer")
                        .document(userId)
                        .collection("emergencyNotifications")
                        .document(notificationId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                Log.d(TAG, "✅ [NOTIFICATION_CLICK_FIX] Fresh notification data retrieved");
                                String freshSeniorName = documentSnapshot.getString("seniorName");
                                String freshSeniorPhone = documentSnapshot.getString("seniorPhone");
                                String freshLocationAddress = documentSnapshot.getString("locationAddress");
                                String freshRequestId = documentSnapshot.getString("requestId");
                                Double freshSeniorLat = documentSnapshot.getDouble("seniorLat");
                                Double freshSeniorLng = documentSnapshot.getDouble("seniorLng");
                                Long timestamp = documentSnapshot.getLong("timestamp");
                                
                                // Show dialog with fresh data
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    if (!isFinishing() && !isDestroyed()) {
                                        Log.d(TAG, "🔧 [NOTIFICATION_CLICK_FIX] Showing dialog with fresh data");
                                        if (freshSeniorLat != null && freshSeniorLng != null && freshSeniorLat != 0.0 && freshSeniorLng != 0.0) {
                                            showEmergencySOSAlertWithLocation(freshSeniorName, freshSeniorPhone, freshLocationAddress, 
                                                                            timestamp != null ? timestamp : System.currentTimeMillis(), 
                                                                            freshRequestId, freshSeniorLat, freshSeniorLng);
                                        } else {
                                            showEmergencySOSAlert(freshSeniorName, freshSeniorPhone, freshLocationAddress, 
                                                                timestamp != null ? timestamp : System.currentTimeMillis(), freshRequestId);
                                        }
                                    }
                                }, 300);
                            } else {
                                Log.w(TAG, "⚠️ [NOTIFICATION_CLICK_FIX] Notification document not found, using intent data");
                                // Fallback to intent data
                                showDialogFromIntentData(seniorName, seniorPhone, locationAddress, requestId, seniorLat, seniorLng);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ [NOTIFICATION_CLICK_FIX] Error fetching notification: " + e.getMessage());
                            // Fallback to intent data
                            showDialogFromIntentData(seniorName, seniorPhone, locationAddress, requestId, seniorLat, seniorLng);
                        });
                } else {
                    // No notificationId, use intent data directly
                    Log.d(TAG, "🔧 [NOTIFICATION_CLICK_FIX] No notificationId, using intent data");
                    showDialogFromIntentData(seniorName, seniorPhone, locationAddress, requestId, seniorLat, seniorLng);
                }
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("emergency_sos_clicked");
                intent.removeExtra("from_emergency_notification");
                intent.removeExtra("senior_name");
                intent.removeExtra("senior_phone");
                intent.removeExtra("location_address");
                intent.removeExtra("notification_id");
                intent.removeExtra("request_id");
                
            } else if (intent.getBooleanExtra("notification_clicked", false)) {
                // Handle emergency notification
                String helpRequestId = intent.getStringExtra("helpRequestId");
                Log.d(TAG, "Activity opened from emergency notification click for helpRequestId: " + helpRequestId);
                
                // Clear the specific notification
                if (helpRequestId != null) {
                    clearEmergencyNotification(helpRequestId);
                    Log.d(TAG, "Cleared notification for helpRequestId: " + helpRequestId);
                }
                
                // Show a toast to confirm
                Toast.makeText(this, getString(R.string.emergency_notification_cleared), Toast.LENGTH_SHORT).show();
                
                // Clear the intent extras to prevent repeated handling
                intent.removeExtra("notification_clicked");
                intent.removeExtra("helpRequestId");
            }
        }
    }
    
    /**
     * Helper method to show dialog from intent data
     * Used as fallback when notification document is not found in database
     */
    private void showDialogFromIntentData(String seniorName, String seniorPhone, String locationAddress, 
                                         String requestId, Double seniorLat, Double seniorLng) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Log.d(TAG, "🔧 [NOTIFICATION_CLICK_FIX] Showing dialog from intent data");
                if (seniorName != null && locationAddress != null) {
                    if (seniorLat != null && seniorLng != null && seniorLat != 0.0 && seniorLng != 0.0) {
                        showEmergencySOSAlertWithLocation(seniorName, seniorPhone, locationAddress, 
                                                        System.currentTimeMillis(), requestId, seniorLat, seniorLng);
                    } else {
                        showEmergencySOSAlert(seniorName, seniorPhone, locationAddress, System.currentTimeMillis(), requestId);
                    }
                } else {
                    Log.w(TAG, "⚠️ [NOTIFICATION_CLICK_FIX] Cannot show dialog - missing seniorName or locationAddress");
                }
            }
        }, 300);
    }
    
    private void showHospitalStatusUpdateDialog(String hospitalName, String hospitalStatus, int availableBeds, int availableDoctors) {
        String statusEmoji = getStatusEmoji(hospitalStatus);
        String message = "🏥 " + hospitalName + "\n\n" +
                        "Status: " + statusEmoji + " " + hospitalStatus.toUpperCase() + "\n" +
                        "Available Beds: " + availableBeds + "\n" +
                        "Available Doctors: " + availableDoctors + "\n\n" +
                        "This information will help with emergency response planning.";
        
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_hospital_status_update))
                .setMessage(message)
                .setPositiveButton("View Hospital List", (dialog, which) -> {
                    // Navigate to hospital list with highlighting
                    Intent intent = new Intent(this, Rescuer_List.class);
                    intent.putExtra("highlight_hospital", hospitalName);
                    intent.putExtra("notification_type", "hospital_status_update");
                    intent.putExtra("hospital_status", hospitalStatus);
                    intent.putExtra("available_beds", availableBeds);
                    intent.putExtra("available_doctors", availableDoctors);
                    startActivity(intent);
                })
                .setNeutralButton("View Dashboard", (dialog, which) -> {
                    // Stay on dashboard but scroll to hospital section if available
                    Toast.makeText(this, getString(R.string.text_hospital_status_updated, hospitalName, statusEmoji, hospitalStatus.toUpperCase()), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Dismiss", (dialog, which) -> {
                    // Just dismiss the dialog
                    dialog.dismiss();
                })
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
    
    private String getStatusEmoji(String status) {
        if (status == null) return "❓";
        
        switch (status.toLowerCase()) {
            case "operational":
                return "🟢";
            case "busy":
                return "🟡";
            case "overcrowded":
                return "🟠";
            case "closed":
                return "🔴";
            case "emergency_only":
                return "🚨";
            default:
                return "❓";
        }
    }

    // Method to handle logout and clear emergency state
    private void handleLogout() {
        // Remove emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
        
        // Stop background notification service
        // Stop all notification services when logging out
        stopAllNotificationServices();

        // Clear stored credentials
        clearStoredCredentials();

        // Navigate to login
        navigateToLogin();
    }

    // =============== EMERGENCY NOTIFICATION SYSTEM ===============

    /**
     * Test method to verify emergency notification system is working
     */
    private void testEmergencyNotificationSystem() {
        Log.d(TAG, "🧪 Testing emergency notification system...");
        
        // Check if emergency listener is active
        if (emergencyListener != null) {
            Log.d(TAG, "✅ Emergency listener is active");
        } else {
            Log.e(TAG, "❌ Emergency listener is NOT active");
        }
        
        // Check if user is logged in
        if (userId != null && !userId.isEmpty()) {
            Log.d(TAG, "✅ User ID available: " + userId);
        } else {
            Log.e(TAG, "❌ User ID not available");
        }
        
        // Check if user type is rescuer
        if (userType != null && userType.equals("rescuer")) {
            Log.d(TAG, "✅ User type is rescuer");
        } else {
            Log.e(TAG, "❌ User type is not rescuer: " + userType);
        }
        
        // Check location
        if (currentLat != 0.0 && currentLong != 0.0) {
            Log.d(TAG, "✅ Location available: " + currentLat + ", " + currentLong);
        } else {
            Log.w(TAG, "⚠️ Location not available yet");
        }
    }
    
    private void checkForExistingEmergencyNotifications() {
        Log.d(TAG, "🔍 Checking for existing emergency notifications...");
        
        if (userId == null) {
            Log.w(TAG, "Cannot check notifications - userId is null");
            return;
        }
        
        // Check for unread emergency notifications
        db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .document(userId)
          .collection("emergencyNotifications")
          .whereEqualTo("isRead", false)
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .limit(5)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              Log.d(TAG, "Found " + querySnapshot.size() + " unread emergency notifications");
              for (QueryDocumentSnapshot document : querySnapshot) {
                  String type = document.getString("type");
                  String seniorName = document.getString("seniorName");
                  Long timestamp = document.getLong("timestamp");
                  Log.d(TAG, "Unread notification: " + type + " from " + seniorName + " at " + timestamp);
              }
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Error checking existing notifications: " + e.getMessage());
          });
    }

    /**
     * Set up the SOS Emergency List RecyclerView - REMOVED
     */
    /* private void setupSOSEmergencyList() {
        Log.d(TAG, "📋 Setting up SOS Emergency List");
        
        // Initialize adapter with empty list
        sosEmergencyAdapter = new SOSEmergencyAdapter(new ArrayList<>(), new SOSEmergencyAdapter.OnEmergencyClickListener() {
            @Override
            public void onEmergencyClick(EmergencyQueueManager.EmergencyRequest emergency) {
                // Show emergency details dialog
                if (emergency.requestId != null) {
                    showEmergencySOSAlertWithLocation(
                        emergency.seniorName,
                        emergency.seniorPhone,
                        emergency.locationAddress,
                        emergency.timestamp,
                        emergency.requestId,
                        emergency.location != null ? emergency.location.getLatitude() : null,
                        emergency.location != null ? emergency.location.getLongitude() : null
                    );
                }
            }

            @Override
            public void onRespondClick(EmergencyQueueManager.EmergencyRequest emergency) {
                // Respond to emergency
                if (emergency.requestId != null) {
                    Log.d(TAG, "🚑 Respond button clicked for: " + emergency.seniorName);
                    assignRescuerToEmergencyById(emergency.requestId);
                }
            }

            @Override
            public void onNavigateClick(EmergencyQueueManager.EmergencyRequest emergency) {
                // Navigate to emergency location
                if (emergency.requestId != null) {
                    Log.d(TAG, "🗺️ Navigate button clicked for: " + emergency.seniorName);
                    // Get emergency and launch navigation
                    EmergencyQueueManager.EmergencyRequest fullEmergency = 
                        EmergencyQueueManager.getInstance(Rescuer_Dashboard.this).getEmergencyById(emergency.requestId);
                    if (fullEmergency != null) {
                        Intent intent = new Intent(Rescuer_Dashboard.this, RescuerNavigationActivity.class);
                        intent.putExtra("helpRequestId", fullEmergency.requestId);
                        intent.putExtra("seniorName", fullEmergency.seniorName);
                        intent.putExtra("seniorPhone", fullEmergency.seniorPhone);
                        if (fullEmergency.location != null) {
                            intent.putExtra("latitude", fullEmergency.location.getLatitude());
                            intent.putExtra("longitude", fullEmergency.location.getLongitude());
                        }
                        intent.putExtra("locationAddress", fullEmergency.locationAddress);
                        startActivity(intent);
                    }
                }
            }
        });
        
        // Set up RecyclerView
        sosEmergencyRecyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        sosEmergencyRecyclerView.setAdapter(sosEmergencyAdapter);
        
        // Initial update
        updateSOSEmergencyList();
    }
   
    private void startEmergencyListUpdates() {
        emergencyListUpdateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        emergencyListUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateSOSEmergencyList();
                // Schedule next update in 5 seconds
                emergencyListUpdateHandler.postDelayed(this, 5000);
            }
        };
        // Start updates immediately and then every 5 seconds
        emergencyListUpdateHandler.post(emergencyListUpdateRunnable);
    }
    
    /**
     * Stop periodic updates for the emergency list - REMOVED
     */
    private void stopEmergencyListUpdates() {
        if (emergencyListUpdateHandler != null && emergencyListUpdateRunnable != null) {
            emergencyListUpdateHandler.removeCallbacks(emergencyListUpdateRunnable);
            emergencyListUpdateHandler = null;
            emergencyListUpdateRunnable = null;
        }
    }
    
    /**
     * Update the SOS Emergency List with current active emergencies - REMOVED
     */
    private void updateSOSEmergencyList() {
        List<EmergencyQueueManager.EmergencyRequest> activeEmergencies = 
            EmergencyQueueManager.getInstance(this).getPendingEmergencies(); // Get only pending (unassigned) emergencies
        
        Log.d(TAG, "📋 Updating SOS Emergency List - Found " + activeEmergencies.size() + " pending emergencies");
        
        // Update adapter
        if (sosEmergencyAdapter != null) {
            sosEmergencyAdapter.updateEmergencyList(activeEmergencies);
        }
        
        // Show/hide empty message
        if (noSOSEmergenciesText != null && sosEmergencyRecyclerView != null) {
            if (activeEmergencies.isEmpty()) {
                noSOSEmergenciesText.setVisibility(android.view.View.VISIBLE);
                sosEmergencyRecyclerView.setVisibility(android.view.View.GONE);
            } else {
                noSOSEmergenciesText.setVisibility(android.view.View.GONE);
                sosEmergencyRecyclerView.setVisibility(android.view.View.VISIBLE);
            }
        }
    }
    
    private void initializeEmergencyNotificationComponents() {
        Log.d(TAG, "🚨 Emergency notification components initialized");
        Log.d(TAG, "🚨 This system is INDEPENDENT from hospital notifications");
        Log.d(TAG, "🚨 Emergency alerts use Firestore real-time listeners");
        Log.d(TAG, "🚨 Hospital notifications use FCM (separate system)");
        
        // Don't start background service here - it causes crashes
        // Background service will start when app goes to background
        // Dashboard listener handles notifications when app is active
    }

    private void startEmergencyListener() {
        Log.d(TAG, "🚨 Starting emergency listener...");

        // Prevent duplicate listeners
        if (emergencyListener != null) {
            Log.w(TAG, "Emergency listener already exists, removing old one first");
            emergencyListener.remove();
            emergencyListener = null;
        }

        // Clean up old emergencies first (older than 1 hour)
        cleanupOldEmergencies();

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

                    Log.d(TAG, "🚨 Emergency listener triggered - snapshots: " + (snapshots != null ? snapshots.size() : "null"));

                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            Log.d(TAG, "🚨 Document change type: " + dc.getType() + " for document: " + dc.getDocument().getId());
                            
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // New emergency detected!
                                DocumentSnapshot emergency = dc.getDocument();
                                Log.d(TAG, "🚨 NEW EMERGENCY DETECTED: " + emergency.getId());
                                handleNewEmergency(emergency);
                            } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                                // Emergency was modified (likely responded to by another rescuer)
                                DocumentSnapshot emergency = dc.getDocument();
                                Boolean isActive = emergency.getBoolean("isActive");
                                Log.d(TAG, "🚨 Emergency modified - isActive: " + isActive);
                                
                                if (isActive != null && !isActive) {
                                    // Emergency was deactivated, clear the notification
                                    String helpRequestId = emergency.getString("helpRequestId");
                                    String respondedBy = emergency.getString("respondedBy");
                                    if (helpRequestId != null) {
                                        clearEmergencyNotification(helpRequestId);
                                        Log.d(TAG, "🚨 Emergency was responded to by another rescuer, clearing notification");
                                        
                                        // Show toast to inform user that another rescuer responded
                                        if (respondedBy != null && !respondedBy.equals(userId)) {
                                            Toast.makeText(Rescuer_Dashboard.this, 
                                                "✅ Another rescuer has responded to this emergency", 
                                                Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "🚨 No active emergencies found");
                    }
                });

        Log.d(TAG, "🚨 Emergency listener started successfully");
    }

    private void cleanupOldEmergencies() {
        // Clean up emergencies older than 1 hour
        long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);

        db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereLessThan("timestamp", oneHourAgo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        // Mark old emergencies as inactive
                        document.getReference().update("isActive", false)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Cleaned up old emergency: " + document.getId()))
                                .addOnFailureListener(e -> Log.e(TAG, "Error cleaning up old emergency", e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error querying old emergencies", e));
    }

    private void handleNewEmergency(DocumentSnapshot emergency) {
        String title = emergency.getString("title");
        String message = emergency.getString("message");
        String seniorName = emergency.getString("seniorName");
        String seniorPhone = emergency.getString("seniorPhone");
        String locationAddress = emergency.getString("locationAddress");
        Double latitude = emergency.getDouble("latitude");
        Double longitude = emergency.getDouble("longitude");
        String helpRequestId = emergency.getString("helpRequestId");

        Log.d(TAG, "�� NEW EMERGENCY: " + seniorName + " at " + locationAddress);

        // Check if this is a truly new emergency (created within the last 5 minutes)
        // This prevents old emergencies from triggering sounds when rescuer logs in
        Long timestamp = emergency.getLong("timestamp");
        boolean isNewEmergency = false;
        if (timestamp != null) {
            long currentTime = System.currentTimeMillis();
            long emergencyAge = currentTime - timestamp;
            long fiveMinutesInMs = 5 * 60 * 1000; // 5 minutes in milliseconds
            
            if (emergencyAge <= fiveMinutesInMs) {
                isNewEmergency = true;
                Log.d(TAG, "✅ Emergency is new (age: " + (emergencyAge / 1000) + " seconds)");
            } else {
                Log.d(TAG, "⚠️ Emergency is old (age: " + (emergencyAge / 1000) + " seconds), skipping sound");
            }
        } else {
            // If no timestamp, assume it's new to be safe
            isNewEmergency = true;
            Log.w(TAG, "⚠️ No timestamp found for emergency, treating as new");
        }

        // Only play notification sound for truly new emergencies
        if (isNewEmergency) {
            playNotificationSound();
        }

        // Check if this rescuer has already responded to this emergency
        String respondedBy = emergency.getString("respondedBy");
        if (respondedBy != null && respondedBy.equals(userId)) {
            Log.d(TAG, "Current rescuer already responded to this emergency, skipping notification for: " + helpRequestId);
            return;
        }

        // Check if emergency is within 5km radius
        if (latitude != null && longitude != null) {
            if (!isWithinRadius(latitude, longitude)) {
                Log.d(TAG, "Emergency is outside 5km radius, skipping notification for: " + helpRequestId);
                return;
            }
        } else {
            Log.w(TAG, "Emergency location data missing, allowing notification for: " + helpRequestId);
        }

        // Additional safety check: verify help request status in database
        if (helpRequestId != null && !helpRequestId.isEmpty()) {
            db.collection("Sagip")
                .document("helpRequests")
                .collection("activeRequests")
                .document(helpRequestId)
                .get()
                .addOnSuccessListener(helpRequestDoc -> {
                    if (helpRequestDoc.exists()) {
                        String status = helpRequestDoc.getString("status");
                        String helpRequestRespondedBy = helpRequestDoc.getString("respondedBy");
                        
                        // If already responded by this rescuer, skip notification
                        if ("responded".equals(status) && userId.equals(helpRequestRespondedBy)) {
                            Log.d(TAG, "Help request already responded by current rescuer, skipping notification for: " + helpRequestId);
                            return;
                        }
                        
                        // If responded by someone else, also skip (other rescuer is handling it)
                        if ("responded".equals(status) && helpRequestRespondedBy != null && !userId.equals(helpRequestRespondedBy)) {
                            Log.d(TAG, "Help request already responded by another rescuer, skipping notification for: " + helpRequestId);
                            return;
                        }
                        
                        // If we reach here, it's safe to show the notification
                        showEmergencyNotification(emergency, title, message, seniorName, seniorPhone, 
                                locationAddress, latitude, longitude, helpRequestId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking help request status, showing notification anyway", e);
                    // If we can't check the status, show the notification to be safe
                    showEmergencyNotification(emergency, title, message, seniorName, seniorPhone, 
                            locationAddress, latitude, longitude, helpRequestId);
                });
            return; // Exit early since we're handling the notification asynchronously
        }

        // If no helpRequestId, show notification directly (fallback)
        showEmergencyNotification(emergency, title, message, seniorName, seniorPhone, 
                locationAddress, latitude, longitude, helpRequestId);
    }

    private void showEmergencyNotification(DocumentSnapshot emergency, String title, String message, 
            String seniorName, String seniorPhone, String locationAddress, Double latitude, 
            Double longitude, String helpRequestId) {
        
        // FIFO: Add emergency to the END of the queue (FIFO - First In, First Out)
        int queuePosition = totalActiveEmergencies + 1;
        double distance = calculateDistance(currentLat, currentLong, latitude, longitude);
        EmergencyItem emergencyItem = new EmergencyItem(title, message, seniorName, seniorPhone,
                locationAddress, latitude, longitude, helpRequestId, emergency.getId(), 1, queuePosition, distance);
        
        // FIFO operation: offer() adds to the end of the queue
        emergencyQueue.offer(emergencyItem);
        totalActiveEmergencies++;
        
        // Track queue start time for first emergency
        if (queueStartTime == 0) {
            queueStartTime = System.currentTimeMillis();
        }
        
        Log.d(TAG, "FIFO: Emergency #" + queuePosition + " added to queue. Total active emergencies: " + totalActiveEmergencies);
        
        // Check if this is a truly new emergency (created within the last 5 minutes)
        // This prevents old emergencies from triggering sounds when rescuer logs in
        Long timestamp = emergency.getLong("timestamp");
        boolean isNewEmergency = false;
        if (timestamp != null) {
            long currentTime = System.currentTimeMillis();
            long emergencyAge = currentTime - timestamp;
            long fiveMinutesInMs = 5 * 60 * 1000; // 5 minutes in milliseconds
            
            if (emergencyAge <= fiveMinutesInMs) {
                isNewEmergency = true;
                Log.d(TAG, "✅ Emergency is new (age: " + (emergencyAge / 1000) + " seconds)");
            } else {
                Log.d(TAG, "⚠️ Emergency is old (age: " + (emergencyAge / 1000) + " seconds), skipping sound");
            }
        } else {
            // If no timestamp, assume it's new to be safe
            isNewEmergency = true;
            Log.w(TAG, "⚠️ No timestamp found for emergency, treating as new");
        }

        // Only play sound for truly new emergencies
        if (isNewEmergency) {
            playNotificationSound();
        }
        
        // Show system notification with FIFO position
        String fifoMessage = message + " - " + locationAddress + " (Queue #" + queuePosition + ")";
        showSystemNotification(title, fifoMessage, helpRequestId);
        
        // Process the queue using FIFO
        processEmergencyQueueFIFO();
    }

    private void processEmergencyQueueFIFO() {
        // If already processing an emergency or queue is empty, return
        if (isProcessingEmergency || emergencyQueue.isEmpty()) {
            return;
        }
        
        // FIFO operation: poll() removes and returns the FIRST item from the queue
        EmergencyItem nextEmergency = emergencyQueue.poll();
        if (nextEmergency != null) {
            isProcessingEmergency = true;
            
            Log.d(TAG, "FIFO: Processing emergency #" + nextEmergency.queuePosition + 
                      " - " + nextEmergency.seniorName + " (Time in queue: " + 
                      (nextEmergency.getTimeInQueue() / 1000) + "s)");
            
            // Show emergency alert dialog using new system
            showEmergencySOSAlert(
                nextEmergency.seniorName, 
                nextEmergency.seniorPhone, 
                nextEmergency.locationAddress, 
                nextEmergency.timestamp,
                nextEmergency.helpRequestId
            );
        }
    }
    
    // Legacy method for backward compatibility


    /**
     * Check and request notification permission (required for Android 13+ / API 33+)
     */
    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+ (API 33+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "📱 Requesting notification permission for Android 13+");
                // Request permission directly (automatic like Senior)
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                Log.d(TAG, "✅ Notification permission already granted");
            }
        } else {
            Log.d(TAG, "ℹ️ Android version < 13, notification permission not required");
        }
    }
    
    /**
     * Check if emergency notification channel is enabled and prompt user to enable if needed
     */
    private void checkNotificationChannelEnabled() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        
        // Check if notifications are enabled for the app
        boolean notificationsEnabled = notificationManager.areNotificationsEnabled();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Check specific channel for Android 8.0+
            String channelId = "emergency_sos_channel";
            NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
            
            if (channel != null) {
                int importance = channel.getImportance();
                Log.d(TAG, "🔔 Emergency SOS channel importance: " + importance + " (0=NONE, 2=LOW, 3=DEFAULT, 4=HIGH, 5=MAX)");
                
                // Check if channel is disabled or set to low importance
                if (importance == NotificationManager.IMPORTANCE_NONE || importance == NotificationManager.IMPORTANCE_LOW) {
                    Log.w(TAG, "⚠️ Emergency notification channel is disabled or set to low importance");
                    showNotificationSetupDialog();
                } else if (!notificationsEnabled) {
                    Log.w(TAG, "⚠️ All notifications are disabled for the app");
                    showNotificationSetupDialog();
                } else {
                    Log.d(TAG, "✅ Emergency notification channel is properly enabled");
                }
            }
        } else {
            // For Android 7.1 and below, just check if notifications are enabled
            if (!notificationsEnabled) {
                Log.w(TAG, "⚠️ Notifications are disabled for the app");
                showNotificationSetupDialog();
            } else {
                Log.d(TAG, "✅ Notifications are enabled");
            }
        }
    }
    
    /**
     * Show dialog prompting user to enable notifications
     */
    private void showNotificationSetupDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🚨 Enable Emergency Notifications")
            .setMessage("Emergency notifications are currently disabled or set to low priority.\n\n" +
                      "To receive critical SOS alerts from seniors, you need to:\n\n" +
                      "1. Enable 'Emergency SOS Alerts' channel\n" +
                      "2. Set importance to 'High' or 'Urgent'\n" +
                      "3. Enable sound and vibration\n\n" +
                      "Would you like to open notification settings now?")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                try {
                    // Open notification settings for this app
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Android 8.0+ - Open channel settings directly
                        intent.setAction(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                        intent.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "emergency_sos_channel");
                    } else {
                        // Older Android - Open app notification settings
                        intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                    }
                    startActivity(intent);
                    Log.d(TAG, "📱 Opened notification settings");
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error opening notification settings: " + e.getMessage());
                    Toast.makeText(this, getString(R.string.please_enable_notifications_in_settings), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(getString(R.string.later_button), (dialog, which) -> {
                Toast.makeText(this, getString(R.string.wont_receive_emergency_alerts), Toast.LENGTH_LONG).show();
            })
            .setCancelable(false)
            .show();
    }
    
    private void playNotificationSound() {
        try {
            Uri notification = getCustomAlarmSound();
            MediaPlayer mp = MediaPlayer.create(getApplicationContext(), notification);
            if (mp != null) {
                mp.start();
                // Stop sound after 5 seconds
                mp.setOnCompletionListener(MediaPlayer::release);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing notification sound", e);
        }
    }
    
    /**
     * Play emergency sound with proper audio configuration
     */
    private void playEmergencySound() {
        Log.d(TAG, "🔊 Playing emergency sound...");
        try {
            // Stop any currently playing emergency sound
            stopEmergencySound();
            
            // Initialize AudioManager
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            
            // Check current ringer mode and log it
            int ringerMode = audioManager.getRingerMode();
            Log.d(TAG, "🔊 Current ringer mode: " + ringerMode + " (0=SILENT, 1=VIBRATE, 2=NORMAL)");
            
            // Ensure alarm volume is at maximum for emergency
            int maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int currentAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            Log.d(TAG, "🔊 Current alarm volume: " + currentAlarmVolume + "/" + maxAlarmVolume);
            
            // Set alarm volume to maximum
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0);
            Log.d(TAG, "🔊 Set alarm volume to maximum: " + maxAlarmVolume);
            
            // Get the alarm sound URI
            Uri soundUri = getCustomAlarmSound();
            Log.d(TAG, "🔊 Using alarm sound URI: " + soundUri.toString());
            
            // Create MediaPlayer with the alarm sound
            currentEmergencySoundPlayer = MediaPlayer.create(this, soundUri);
            if (currentEmergencySoundPlayer != null) {
                Log.d(TAG, "🔊 MediaPlayer created successfully");
                
                // Set audio attributes for emergency sound
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();
                    currentEmergencySoundPlayer.setAudioAttributes(audioAttributes);
                    Log.d(TAG, "🔊 Audio attributes set for API " + Build.VERSION.SDK_INT);
                } else {
                    currentEmergencySoundPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                    Log.d(TAG, "🔊 Audio stream type set to ALARM for API " + Build.VERSION.SDK_INT);
                }
                
                // Set volume to maximum
                currentEmergencySoundPlayer.setVolume(1.0f, 1.0f);
                Log.d(TAG, "🔊 MediaPlayer volume set to maximum");
                
                currentEmergencySoundPlayer.setOnPreparedListener(mp -> {
                    Log.d(TAG, "🔊 Emergency MediaPlayer prepared, starting playback");
                    mp.start();
                    Log.d(TAG, "🔊 Emergency sound started successfully");
                });
                
                currentEmergencySoundPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "❌ Emergency MediaPlayer error: what=" + what + ", extra=" + extra);
                    mp.release();
                    currentEmergencySoundPlayer = null;
                    return true;
                });
                
                currentEmergencySoundPlayer.setOnCompletionListener(mp -> {
                    Log.d(TAG, "🔊 Emergency sound playback completed");
                    mp.release();
                    currentEmergencySoundPlayer = null;
                });
            } else {
                Log.e(TAG, "❌ Failed to create emergency MediaPlayer");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error playing emergency sound: " + e.getMessage(), e);
        }
    }
    
    /**
     * Stop emergency sound from playing
     * Uses aggressive stopping to ensure sound stops immediately
     */
    private void stopEmergencySound() {
        Log.d(TAG, "🔇 Stopping emergency sound...");
        if (currentEmergencySoundPlayer != null) {
            try {
                // Set volume to 0 immediately to mute any buffered audio
                currentEmergencySoundPlayer.setVolume(0.0f, 0.0f);
                Log.d(TAG, "🔇 Volume set to 0 (muted)");
                
                if (currentEmergencySoundPlayer.isPlaying()) {
                    currentEmergencySoundPlayer.stop();
                    Log.d(TAG, "🔇 Emergency sound stopped successfully");
                }
                
                // Reset before releasing to clear any buffered audio
                currentEmergencySoundPlayer.reset();
                Log.d(TAG, "🔇 MediaPlayer reset (cleared buffers)");
                
                currentEmergencySoundPlayer.release();
                currentEmergencySoundPlayer = null;
                Log.d(TAG, "🔇 MediaPlayer released and cleared");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error stopping emergency sound: " + e.getMessage(), e);
                try {
                    if (currentEmergencySoundPlayer != null) {
                        currentEmergencySoundPlayer.release();
                    }
                } catch (Exception e2) {
                    Log.e(TAG, "❌ Error releasing MediaPlayer: " + e2.getMessage());
                }
                currentEmergencySoundPlayer = null;
            }
        } else {
            Log.d(TAG, "🔇 No emergency sound currently playing");
        }
    }
    
    /**
     * Cancel all system notifications to stop notification channel sounds immediately
     * This ensures that notification sounds stop when the rescuer responds
     */
    private void cancelAllSystemNotifications() {
        Log.d(TAG, "🔕 Canceling all system notifications to stop sounds...");
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                // Cancel all notifications from this app
                notificationManager.cancelAll();
                Log.d(TAG, "✅ All system notifications canceled successfully");
            } else {
                Log.e(TAG, "❌ NotificationManager is null, cannot cancel notifications");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error canceling system notifications: " + e.getMessage(), e);
        }
    }
    
    /**
     * Show Android system notification for emergency
     */
    private void showEmergencySystemNotification(String seniorName, String seniorPhone, String locationAddress, String requestId) {
        Log.d(TAG, "📱 Creating Android system notification for emergency: " + seniorName);
        
        // CRITICAL: Check notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ NOTIFICATION PERMISSION DENIED - Cannot show notifications!");
                Log.e(TAG, "❌ User must grant notification permission in app settings");
                Log.e(TAG, "❌ Sound and dialog will still work, but no notification drawer alert");
                return; // Exit early - can't show notification without permission
            } else {
                Log.d(TAG, "✅ Notification permission granted - showing notification");
            }
        }
        
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) {
                Log.e(TAG, "❌ NotificationManager is null");
                return;
            }
            
            // Create notification channel for Android 8.0+
            String channelId = "emergency_sos_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Emergency SOS Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Critical emergency notifications from seniors");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                
                // Use alarm sound for notification
                Uri soundUri = getCustomAlarmSound();
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                channel.setSound(soundUri, audioAttributes);
                
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Notification channel created");
            }
            
            // Create intent to open app when notification is tapped
            Intent intent = new Intent(this, Rescuer_Dashboard.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            // Add all emergency info so dialog can show when notification is tapped
            intent.putExtra("emergency_sos_clicked", true);
            intent.putExtra("from_emergency_notification", true);
            intent.putExtra("senior_name", seniorName);
            intent.putExtra("senior_phone", seniorPhone);
            intent.putExtra("location_address", locationAddress);
            intent.putExtra("request_id", requestId);
            intent.putExtra("requestId", requestId); // Keep for backward compatibility
            intent.putExtra("from_notification", true); // Keep for backward compatibility
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification) // Make sure you have this icon
                .setContentTitle("🚨 EMERGENCY ALERT 🚨")
                .setContentText(seniorName + " needs immediate help!")
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText("🚨 EMERGENCY ALERT\n\n" +
                            "👤 Senior: " + seniorName + "\n" +
                            "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not available") + "\n" +
                            "📍 Location: " + (locationAddress != null ? locationAddress : "Unknown") + "\n\n" +
                            "⚠️ TAP TO RESPOND IMMEDIATELY"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false) // Don't dismiss when tapped
                .setOngoing(true) // Keep notification until emergency is handled
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 500); // Red flashing light
            
            // Add alarm sound for pre-Oreo devices
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Uri soundUri = getCustomAlarmSound();
                builder.setSound(soundUri, AudioManager.STREAM_ALARM);
            }
            
            // Show notification
            int notificationId = requestId.hashCode();
            notificationManager.notify(notificationId, builder.build());
            
            Log.d(TAG, "✅ Android system notification shown with ID: " + notificationId);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error showing system notification: " + e.getMessage(), e);
        }
    }
    
    /**
     * Test emergency sound playback for debugging - call this method manually
     */
    public void testEmergencySoundPlayback() {
        Log.d(TAG, "🔊 Testing emergency sound playback from Rescuer_Dashboard...");
        try {
            Uri soundUri = getCustomAlarmSound();
            Log.d(TAG, "🔊 Testing with sound URI: " + soundUri.toString());
            
            // Initialize AudioManager
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            
            // Check current ringer mode and log it
            int ringerMode = audioManager.getRingerMode();
            Log.d(TAG, "🔊 Current ringer mode: " + ringerMode + " (0=SILENT, 1=VIBRATE, 2=NORMAL)");
            
            // Ensure alarm volume is at maximum for emergency
            int maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int currentAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            Log.d(TAG, "🔊 Current alarm volume: " + currentAlarmVolume + "/" + maxAlarmVolume);
            
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0);
            Log.d(TAG, "🔊 Set alarm volume to maximum: " + maxAlarmVolume);
            
            MediaPlayer testPlayer = MediaPlayer.create(this, soundUri);
            if (testPlayer != null) {
                Log.d(TAG, "🔊 Test MediaPlayer created successfully");
                
                // Set audio attributes for emergency sound
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();
                    testPlayer.setAudioAttributes(audioAttributes);
                    Log.d(TAG, "🔊 Audio attributes set for API " + Build.VERSION.SDK_INT);
                } else {
                    testPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                    Log.d(TAG, "🔊 Audio stream type set to ALARM for API " + Build.VERSION.SDK_INT);
                }
                
                // Set volume to maximum
                testPlayer.setVolume(1.0f, 1.0f);
                Log.d(TAG, "🔊 MediaPlayer volume set to maximum");
                
                testPlayer.setOnPreparedListener(mp -> {
                    Log.d(TAG, "🔊 Test MediaPlayer prepared, starting playback");
                    mp.start();
                    Log.d(TAG, "🔊 Test MediaPlayer started successfully");
                });
                
                testPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "❌ Test MediaPlayer error: what=" + what + ", extra=" + extra);
                    mp.release();
                    return true;
                });
                
                testPlayer.setOnCompletionListener(mp -> {
                    Log.d(TAG, "🔊 Test sound playback completed");
                    mp.release();
                });
            } else {
                Log.e(TAG, "❌ Failed to create test MediaPlayer");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error testing emergency sound: " + e.getMessage(), e);
        }
    }

    private void handleEmergencyDialogDismissed() {
        // Mark that we're no longer processing an emergency
        isProcessingEmergency = false;
        totalActiveEmergencies--;
        
        // Clear the current dialog reference
        currentEmergencyDialog = null;
        
        Log.d(TAG, "FIFO: Emergency dialog dismissed. Remaining emergencies: " + totalActiveEmergencies);
        
        // Reset queue start time if queue is empty
        if (emergencyQueue.isEmpty()) {
            queueStartTime = 0;
            Log.d(TAG, "FIFO: Queue is now empty, resetting queue start time");
        }
        
        // Process the next emergency in FIFO queue if any
        if (!emergencyQueue.isEmpty()) {
            // Small delay to allow UI to update
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                processEmergencyQueueFIFO();
            }, 500);
        }
    }
    
    // Method to clear the entire FIFO queue (useful for testing or emergency situations)
    private void clearFIFOQueue() {
        emergencyQueue.clear();
        totalActiveEmergencies = 0;
        queueStartTime = 0;
        isProcessingEmergency = false;
        currentEmergencyDialog = null;
        Log.d(TAG, "FIFO: Queue cleared completely");
    }
    
    // Method to get FIFO queue statistics
    private String getFIFOQueueStats() {
        if (emergencyQueue.isEmpty()) {
            return "FIFO Queue: Empty";
        }
        
        long totalQueueTime = System.currentTimeMillis() - queueStartTime;
        return String.format("FIFO Queue: %d emergencies, %s total time", 
                totalActiveEmergencies, getTimeInQueueText(totalQueueTime));
    }
    
    // Calculate distance between two coordinates using Haversine formula
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c; // convert to kilometers
        
        return distance;
    }
    
    // Check if emergency is within 5km radius
    private boolean isWithinRadius(double emergencyLat, double emergencyLon) {
        if (currentLat == 0.0 || currentLong == 0.0) {
            Log.w(TAG, "Rescuer location not available, allowing emergency notification");
            return true; // Allow notification if rescuer location is not available
        }
        
        double distance = calculateDistance(currentLat, currentLong, emergencyLat, emergencyLon);
        boolean withinRadius = distance <= 5.0; // 5km radius
        
        Log.d(TAG, String.format("Distance to emergency: %.2f km, Within 5km radius: %s", 
                distance, withinRadius));
        
        return withinRadius;
    }
    
    // Get formatted distance text
    private String getDistanceText(double emergencyLat, double emergencyLon) {
        if (currentLat == 0.0 || currentLong == 0.0) {
            return "Distance: Unknown";
        }
        
        double distance = calculateDistance(currentLat, currentLong, emergencyLat, emergencyLon);
        if (distance < 1.0) {
            return String.format("Distance: %.0f m", distance * 1000);
        } else {
            return String.format("Distance: %.1f km", distance);
        }
    }

    private void openEmergencyListActivity() {
        Intent intent = new Intent(this, EmergencyListActivity.class);
        startActivity(intent);
    }
    
    
    
    
    
    
    
    
    

    private String getTimeInQueueText(long timeInQueueMs) {
        long seconds = timeInQueueMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return hours + " hour" + (hours != 1 ? "s" : "") + " " + (minutes % 60) + " min";
        }
    }

    private void showEmergencySummaryFIFO() {
        if (emergencyQueue.isEmpty()) {
            return;
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("🔄 FIFO EMERGENCY QUEUE (").append(totalActiveEmergencies).append(")\n");
        summary.append("First In, First Out Processing\n\n");
        
        int index = 1;
        for (EmergencyItem emergency : emergencyQueue) {
            summary.append("📍 POSITION #").append(emergency.queuePosition)
                   .append(" - ").append(emergency.seniorName)
                   .append("\n   📍 ").append(emergency.locationAddress)
                   .append("\n   📏 Distance: ").append(emergency.getDistanceText())
                   .append("\n   📞 ").append(emergency.seniorPhone != null ? emergency.seniorPhone : "No phone")
                   .append("\n   ⏰ In Queue: ").append(getTimeInQueueText(emergency.getTimeInQueue()))
                   .append("\n   🕐 Reported: ").append(getTimeAgo(emergency.timestamp))
                   .append("\n\n");
            index++;
        }
        
        // Add FIFO explanation
        summary.append("📋 FIFO ALGORITHM:\n");
        summary.append("• First emergency reported = First to be processed\n");
        summary.append("• Queue position determines processing order\n");
        summary.append("• No emergency can 'cut in line'\n");
        summary.append("• Fair and predictable processing");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.text_fifo_emergency_queue));
        builder.setMessage(summary.toString());
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        builder.setPositiveButton("🚑 PROCESS FIRST", (dialog, which) -> {
            // Process the first emergency in FIFO queue
            processEmergencyQueueFIFO();
        });
        
        builder.setNegativeButton("❌ CLOSE", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showEmergencySummary() {
        if (emergencyQueue.isEmpty()) {
            return;
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("🚨 ACTIVE EMERGENCIES (").append(totalActiveEmergencies).append(")\n\n");
        
        int index = 1;
        for (EmergencyItem emergency : emergencyQueue) {
            summary.append(index).append(". ").append(emergency.seniorName)
                   .append(" - ").append(emergency.locationAddress)
                   .append("\n   📞 ").append(emergency.seniorPhone != null ? emergency.seniorPhone : "No phone")
                   .append("\n   ⏰ ").append(getTimeAgo(emergency.timestamp))
                   .append("\n\n");
            index++;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.text_emergency_summary));
        builder.setMessage(summary.toString());
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        builder.setPositiveButton("🚑 RESPOND TO FIRST", (dialog, which) -> {
            // Process the first emergency in queue
            processEmergencyQueue();
        });
        
        builder.setNegativeButton("❌ CLOSE", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        
        if (minutes < 1) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else {
            long hours = minutes / 60;
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        }
    }

    // OLD SYSTEM REMOVED - Now using EmergencyQueueManager only
    
    // OLD SYSTEM REMOVED - Notifications now handled by EmergencyQueueManager
    
    private String getCurrentRescuerName() {
        // Get rescuer name from current user data
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        }
        
        // Fallback to user ID if no display name
        return userId != null ? "Rescuer " + userId.substring(0, Math.min(8, userId.length())) : "Unknown Rescuer";
    }
    
    private String getCurrentRescuerPhone() {
        // Get rescuer phone from current user data
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String phoneNumber = currentUser.getPhoneNumber();
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                return phoneNumber;
            }
        }
        
        // Fallback to a default phone number or get from user profile
        return "Not available";
    }
    
    private String getCurrentRescuerTeam() {
        // Get rescuer team from current user data
        // This would typically come from the user's profile in Firestore
        return "Emergency Response Team";
    }

    // Method to clear emergency notification
    private void clearEmergencyNotification(String helpRequestId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Cancel the specific emergency notification
            notificationManager.cancel(helpRequestId.hashCode());
            Log.d(TAG, "✅ Cleared emergency notification for: " + helpRequestId);
            
            // Also clear any related notifications with similar IDs
            notificationManager.cancel(helpRequestId.hashCode() + 1);
            notificationManager.cancel(helpRequestId.hashCode() - 1);
            
            // Show a brief toast to confirm notification was cleared
            Toast.makeText(this, getString(R.string.emergency_accepted_notification_cleared), Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "❌ NotificationManager is null, cannot clear notification");
        }
    }

    // Method to clear all emergency notifications
    private void clearAllEmergencyNotifications() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Cancel all emergency notifications
            notificationManager.cancelAll();
            Log.d(TAG, "Cleared all emergency notifications");
        }
        
        // Dismiss any active emergency popup dialog
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            currentEmergencyDialog = null;
            Log.d(TAG, "Dismissed emergency popup dialog when returning to dashboard");
        }
    }
    
    // Method to clear notifications for other rescuers when one rescuer accepts
    private void clearNotificationsForOtherRescuers(String helpRequestId, String emergencyId) {
        Log.d(TAG, "🔄 Clearing emergency notifications for other rescuers - Help Request: " + helpRequestId + ", Emergency: " + emergencyId);
        
        // Get all rescuers and clear their notifications for this specific emergency
        db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .get()
          .addOnSuccessListener(querySnapshot -> {
              Log.d(TAG, "📋 Found " + querySnapshot.size() + " rescuers to notify about emergency acceptance");
              
              for (QueryDocumentSnapshot rescuerDoc : querySnapshot) {
                  String rescuerId = rescuerDoc.getId();
                  
                  // Skip the current rescuer (the one who accepted)
                  if (rescuerId.equals(userId)) {
                      continue;
                  }
                  
                  // Clear the specific emergency notification from this rescuer's collection
                  db.collection("Sagip")
                    .document("users")
                    .collection("rescuer")
                    .document(rescuerId)
                    .collection("emergencyNotifications")
                    .whereEqualTo("helpRequestId", helpRequestId)
                    .get()
                    .addOnSuccessListener(notificationSnapshot -> {
                        for (QueryDocumentSnapshot notificationDoc : notificationSnapshot) {
                            // Mark the notification as cleared/responded
                            notificationDoc.getReference().update(
                                "isActive", false,
                                "respondedBy", userId,
                                "respondedAt", System.currentTimeMillis(),
                                "status", "responded_by_other"
                            ).addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "✅ Cleared emergency notification for rescuer: " + rescuerId);
                            }).addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to clear notification for rescuer " + rescuerId, e);
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Error getting notifications for rescuer " + rescuerId, e);
                    });
              }
              
              Log.d(TAG, "✅ Finished clearing notifications for other rescuers");
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "❌ Error getting rescuers list for notification clearing", e);
          });
    }
    
    private void startEmergencySOSListener() {
        if (userId == null) {
            Log.w(TAG, "Cannot start emergency SOS listener - userId is null");
            return;
        }
        
        // Check if user has logged out
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedOut = prefs.getBoolean("user_logged_out", false);
        if (isLoggedOut) {
            Log.w(TAG, "⚠️ User has logged out, not starting emergency SOS listener");
            return;
        }
        
        // Clean up existing listener to prevent duplicates
        if (emergencySOSListener != null) {
            Log.d(TAG, "🧹 Cleaning up existing emergency SOS listener before creating new one");
            emergencySOSListener.remove();
            emergencySOSListener = null;
        }
        
        // Update login time to current time when starting listener
        // This ensures only NEW emergencies (after this moment) will trigger alerts
        lastLoginTime = System.currentTimeMillis();
        
        Log.d(TAG, "🚨 Starting emergency SOS listener for rescuer: " + userId);
        Log.d(TAG, "✅ [IN-APP_ALERTS] Dashboard listener ENABLED for in-app alerts when app is open");
        Log.d(TAG, "⏰ Listener start time (for filtering): " + lastLoginTime);
        Log.d(TAG, "🔍 Listener path: Sagip/users/rescuer/" + userId + "/emergencyNotifications");
        
        // Listen for emergency SOS notifications in real-time
        // This shows IN-APP ALERTS when the app is open
        // The background service handles SYSTEM NOTIFICATIONS when app is closed
        // CRITICAL: Only listen for notifications created AFTER listener start time (REALTIME ONLY)
        // Note: Using whereGreaterThan to filter old notifications, but will catch all new ones
        String listenerPath = "Sagip/users/rescuer/" + userId + "/emergencyNotifications";
        Log.d(TAG, "📡 Setting up Firestore listener on: " + listenerPath);
        
        emergencySOSListener = db.collection("Sagip")
          .document("users")
          .collection("rescuer")
          .document(userId)
          .collection("emergencyNotifications")
          .whereGreaterThan("timestamp", lastLoginTime - 60000)  // Allow 1 minute buffer for timing issues
          .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
          .addSnapshotListener((querySnapshot, error) -> {
              if (error != null) {
                  Log.e(TAG, "❌ Error listening to emergency SOS notifications: " + error.getMessage(), error);
                  if (error instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
                      Log.e(TAG, "❌ Error code: " + ((com.google.firebase.firestore.FirebaseFirestoreException) error).getCode());
                  } else {
                      Log.e(TAG, "❌ Error type: " + error.getClass().getSimpleName());
                  }
                  Log.e(TAG, "❌ Listener path: " + listenerPath);
                  return;
              }
              
              Log.d(TAG, "📡 Listener triggered - snapshot size: " + (querySnapshot != null ? querySnapshot.size() : "null"));
              
              if (querySnapshot != null) {
                  // Track document changes to handle new notifications and deletions
                  for (DocumentChange dc : querySnapshot.getDocumentChanges()) {
                      switch (dc.getType()) {
                          case ADDED:
                          case MODIFIED:
                              Log.d(TAG, "📱 [DASHBOARD_LISTENER] Processing notification in active app");
                              handleEmergencySOSNotification(dc.getDocument());
                              break;
                          case REMOVED:
                              String removedRequestId = dc.getDocument().getString("requestId");
                              Log.d(TAG, "🗑️ [DASHBOARD_LISTENER] Notification removed: " + dc.getDocument().getId() + " (RequestID: " + removedRequestId + ")");
                              
                              // Remove from emergency queue if present
                              synchronized (emergencyQueue) {
                                  emergencyQueue.removeIf(item -> {
                                      boolean matches = item.helpRequestId != null && item.helpRequestId.equals(removedRequestId);
                                      if (matches) {
                                          Log.d(TAG, "🗑️ [DASHBOARD_LISTENER] Removed emergency from queue: " + removedRequestId);
                                      }
                                      return matches;
                                  });
                              }
                              
                              // Dismiss dialog if it's showing this specific emergency
                              synchronized (dialogLock) {
                                  if (removedRequestId != null && removedRequestId.equals(currentEmergencyRequestId)) {
                                      Log.d(TAG, "🗑️ [DASHBOARD_LISTENER] RequestId matches current dialog: " + removedRequestId);
                                      
                                      // Dismiss dialog if it exists, regardless of isShowing() state
                                      if (currentEmergencyDialog != null) {
                                          try {
                                              if (currentEmergencyDialog.isShowing()) {
                                                  Log.d(TAG, "🗑️ [DASHBOARD_LISTENER] Dialog is showing, dismissing now");
                                                  currentEmergencyDialog.dismiss();
                                              } else {
                                                  Log.d(TAG, "🗑️ [DASHBOARD_LISTENER] Dialog exists but isShowing()=false, dismissing anyway");
                                                  currentEmergencyDialog.dismiss();
                                              }
                                          } catch (Exception e) {
                                              Log.e(TAG, "🗑️ [DASHBOARD_LISTENER] Error dismissing dialog: " + e.getMessage());
                                          }
                                          currentEmergencyDialog = null;
                                      } else {
                                          // Dialog might not be created yet - wait a bit and try again
                                          Log.d(TAG, "🗑️ [DASHBOARD_LISTENER] Dialog not created yet, will retry dismissal in 500ms");
                                          new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                              synchronized (dialogLock) {
                                                  if (currentEmergencyDialog != null && removedRequestId.equals(currentEmergencyRequestId)) {
                                                      try {
                                                          Log.d(TAG, "🗑️ [DASHBOARD_LISTENER] Retry: Dismissing dialog after delay");
                                                          currentEmergencyDialog.dismiss();
                                                          currentEmergencyDialog = null;
                                                      } catch (Exception e) {
                                                          Log.e(TAG, "🗑️ [DASHBOARD_LISTENER] Retry: Error dismissing dialog: " + e.getMessage());
                                                      }
                                                  }
                                                  // Clear tracking regardless
                                                  if (removedRequestId.equals(currentEmergencyRequestId)) {
                                                      currentEmergencyRequestId = null;
                                                      isEmergencyDialogShowing = false;
                                                      Log.d(TAG, "✅ [DASHBOARD_LISTENER] Retry: Tracking cleared");
                                                  }
                                              }
                                          }, 500);
                                      }
                                      
                                      // Clear tracking
                                      currentEmergencyRequestId = null;
                                      isEmergencyDialogShowing = false;
                                      
                                      // Stop emergency sound
                                      stopEmergencySound();
                                      
                                      Log.d(TAG, "✅ [DASHBOARD_LISTENER] Emergency dialog dismissed and tracking cleared");
                                  }
                              }
                              break;
                      }
                  }
              }
          });
    }
    
    private void handleEmergencySOSNotification(QueryDocumentSnapshot document) {
        try {
            String type = document.getString("type");
            String title = document.getString("title");
            String message = document.getString("message");
            String seniorName = document.getString("seniorName");
            String seniorPhone = document.getString("seniorPhone");
            String locationAddress = document.getString("locationAddress");
            String requestId = document.getString("requestId");
            Long timestamp = document.getLong("timestamp");
            Boolean isRead = document.getBoolean("isRead");
            String notificationStatus = document.getString("notificationStatus");
            
            // Read GPS coordinates from notification data
            Double seniorLat = document.getDouble("seniorLat");
            Double seniorLng = document.getDouble("seniorLng");
            
            Log.d(TAG, "📱 [DASHBOARD_HANDLER] Processing notification - Type: " + type + ", IsRead: " + isRead + ", Status: " + notificationStatus);
            
            // Process emergency SOS notifications
            // Note: We rely on the isRead flag to prevent duplicate processing
            // Old notifications should already be marked as read
            if ("EMERGENCY_SOS".equals(type) && (isRead == null || !isRead)) {
                // Only process unread emergency SOS notifications that are NOT assigned
                Log.d(TAG, "🚨 [DASHBOARD] Received emergency SOS notification: " + seniorName + " (Request ID: " + requestId + ")");
                
                if (seniorLat != null && seniorLng != null) {
                    Log.d(TAG, "📍 GPS coordinates from notification: " + seniorLat + ", " + seniorLng);
                } else {
                    Log.w(TAG, "⚠️ No GPS coordinates in notification data");
                }
                
                // Play emergency sound IMMEDIATELY (don't wait for database update)
                playEmergencySound();
                
                // Show Android system notification IMMEDIATELY
                showEmergencySystemNotification(seniorName, seniorPhone, locationAddress, requestId);
                
                // Mark as read to prevent race condition with background service
                // Use atomic update to ensure only one process marks it as read
                document.getReference().update("isRead", true, "processedBy", "dashboard")
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ [DASHBOARD] Marked notification as read and processing");
                        
                        // Queue emergency for processing to prevent conflicts
                        // This will show the IN-APP ALERT DIALOG
                        queueEmergencyForProcessing(seniorName, seniorPhone, locationAddress, timestamp, requestId, seniorLat, seniorLng);
                        
                        Log.d(TAG, "✅ [DASHBOARD] In-app alert will be shown for: " + seniorName);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "⚠️ [DASHBOARD] Failed to mark as read (might already be processed by background service): " + e.getMessage());
                        // Stop sound if we failed to claim this notification
                        stopEmergencySound();
                    });
            } else if ("EMERGENCY_SOS".equals(type) && isRead != null && isRead) {
                Log.d(TAG, "🔇 [DASHBOARD] Notification already read, skipping (likely processed by background service)");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling emergency SOS notification: " + e.getMessage(), e);
        }
    }
    
    private void showEmergencySOSAlert(String seniorName, String seniorPhone, String locationAddress, Long timestamp) {
        showEmergencySOSAlert(seniorName, seniorPhone, locationAddress, timestamp, null);
    }
    
    private void showEmergencySOSAlertWithLocation(String seniorName, String seniorPhone, String locationAddress, Long timestamp, String requestId, Double seniorLat, Double seniorLng) {
        Log.d(TAG, "🔍 [SHOW_DIALOG] showEmergencySOSAlertWithLocation called for: " + seniorName);
        Log.d(TAG, "🔍 [SHOW_DIALOG] RequestId: " + requestId);
        Log.d(TAG, "🔍 [SHOW_DIALOG] GPS coordinates: " + seniorLat + ", " + seniorLng);
        Log.d(TAG, "🔍 [SHOW_DIALOG] isEmergencyDialogShowing: " + isEmergencyDialogShowing);
        
        // Enhanced activity state check
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show emergency alert dialog - activity is not in valid state (finishing: " + isFinishing() + ", destroyed: " + isDestroyed() + ")");
            // Stop sound since we can't show the dialog
            stopEmergencySound();
            return;
        }
        
        // Synchronized check to prevent race conditions
        synchronized (dialogLock) {
            // Check if dialog is already showing for the SAME requestId (prevent duplicates)
            // BUT allow different requestIds to show - multiple SOS alerts from different seniors should all be shown
            if (isEmergencyDialogShowing && requestId != null && requestId.equals(currentEmergencyRequestId)) {
                Log.w(TAG, "⚠️ [SHOW_DIALOG] Emergency dialog already showing for this requestId: " + requestId + ", ignoring duplicate call");
                return;
            }
            
            // If a different requestId wants to show while dialog is showing, allow it
            // This handles the case where 2 seniors send SOS - both should be shown sequentially via queue
            if (isEmergencyDialogShowing && requestId != null && !requestId.equals(currentEmergencyRequestId)) {
                Log.d(TAG, "🔄 [SHOW_DIALOG] Different emergency (requestId: " + requestId + ") wants to show while dialog is showing (requestId: " + currentEmergencyRequestId + ")");
                Log.d(TAG, "🔄 [SHOW_DIALOG] Dismissing current dialog to show new emergency");
                // Dismiss current dialog to show new one
                if (currentEmergencyDialog != null) {
                    try {
                        currentEmergencyDialog.dismiss();
                    } catch (Exception e) {
                        Log.e(TAG, "Error dismissing current dialog: " + e.getMessage());
                    }
                    currentEmergencyDialog = null;
                }
            }
            
            // Double-check activity state after acquiring lock
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Cannot show emergency alert dialog - activity state changed during lock acquisition");
                // Stop sound since we can't show the dialog
                stopEmergencySound();
                return;
            }
            
            // Mark dialog as showing and track which emergency
            isEmergencyDialogShowing = true;
            currentEmergencyRequestId = requestId;
            Log.d(TAG, "🔍 [SHOW_DIALOG] Setting isEmergencyDialogShowing = true, requestId = " + requestId);
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_emergency_sos_alert));
        
        String timeStr = "Unknown time";
        if (timestamp != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            timeStr = sdf.format(new java.util.Date(timestamp));
        }
        
        String message = "🚨 URGENT: Senior needs immediate help!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + seniorPhone + "\n" +
                        "📍 Location: " + locationAddress + "\n" +
                        "⏰ Time: " + timeStr + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        
        // Respond to emergency button
        builder.setPositiveButton(getString(R.string.button_respond_now), (dialog, which) -> {
            Log.d(TAG, "🚨🚨🚨 RESPOND NOW BUTTON CLICKED 🚨🚨🚨");
            Log.d(TAG, "🔍 [RESPOND_NOW] Emergency: " + requestId);
            Log.d(TAG, "🔍 [RESPOND_NOW] Senior: " + seniorName);
            Log.d(TAG, "🔍 [RESPOND_NOW] GPS coordinates: " + seniorLat + ", " + seniorLng);
            
            // FIRST: Validate that the emergency is still available
            if (requestId != null) {
                Log.d(TAG, "🔍 [RESPOND_NOW] Validating emergency is still available...");
                db.collection("Sagip").document("emergencyRequests").collection("activeRequests")
                    .document(requestId)
                    .get()
                    .addOnSuccessListener(requestDoc -> {
                        if (!requestDoc.exists()) {
                            Log.w(TAG, "⚠️ [RESPOND_NOW] Emergency no longer exists!");
                            Toast.makeText(this, getString(R.string.another_rescuer_responded), Toast.LENGTH_LONG).show();
                            stopEmergencySound();
                            EmergencySOSBackgroundService.dismissAllEmergencyNotifications();
                            cancelAllSystemNotifications();
                            synchronized (dialogLock) {
                                isEmergencyDialogShowing = false;
                                currentEmergencyRequestId = null;
                            }
                            dialog.dismiss();
                            return;
                        }
                        
                        String status = requestDoc.getString("status");
                        String assignedTo = requestDoc.getString("assignedRescuerId");
                        
                        if ("assigned".equals(status) && assignedTo != null && !assignedTo.equals(userId)) {
                            Log.w(TAG, "⚠️ [RESPOND_NOW] Emergency already assigned to another rescuer!");
                            Toast.makeText(this, getString(R.string.another_rescuer_responded), Toast.LENGTH_LONG).show();
                            stopEmergencySound();
                            EmergencySOSBackgroundService.dismissAllEmergencyNotifications();
                            cancelAllSystemNotifications();
                            synchronized (dialogLock) {
                                isEmergencyDialogShowing = false;
                                currentEmergencyRequestId = null;
                            }
                            dialog.dismiss();
                            return;
                        }
                        
                        // Emergency is still available, proceed with assignment
                        Log.d(TAG, "✅ [RESPOND_NOW] Emergency is available, proceeding...");
                        
                        // Stop ALL emergency sounds AND dismiss notifications immediately when rescuer responds
                        stopEmergencySound(); // Stop dashboard sound
                        EmergencySOSBackgroundService.dismissAllEmergencyNotifications(); // Stop background service sound AND dismiss notifications
                        cancelAllSystemNotifications(); // Cancel all system notifications to stop notification channel sounds
                        Log.d(TAG, "🔇 [RESPOND_NOW] All emergency sounds stopped and notifications dismissed");
                        
                        // Reset dialog flag safely
                        synchronized (dialogLock) {
                            isEmergencyDialogShowing = false;
                            currentEmergencyRequestId = null;
                        }
                        
                        // Clear all emergency notifications and dialogs
                        clearAllEmergencyNotifications();
                        Log.d(TAG, "🔍 [RESPOND_NOW] Cleared all emergency notifications and dialogs");
                        
                        // Dismiss dialog immediately
                        dialog.dismiss();
                        Log.d(TAG, "🔍 [RESPOND_NOW] Dialog dismissed successfully");
                        
                        // Assign this rescuer to the emergency (this will send notification to senior)
                        Log.d(TAG, "🔍 [RESPOND_NOW] Calling assignRescuerToEmergencyById with requestId: " + requestId);
                        assignRescuerToEmergencyById(requestId);
                        
                        // Update emergency list to reflect the change - REMOVED
                        // updateSOSEmergencyList();
                        
                        // Show confirmation to rescuer
                        Toast.makeText(this, getString(R.string.toast_assigned_to_emergency), Toast.LENGTH_LONG).show();
                        Log.d(TAG, "🔍 [RESPOND_NOW] Toast shown to rescuer");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ [RESPOND_NOW] Error validating emergency: " + e.getMessage());
                        Toast.makeText(this, getString(R.string.error_checking_emergency_status), Toast.LENGTH_SHORT).show();
                        synchronized (dialogLock) {
                            isEmergencyDialogShowing = false;
                            currentEmergencyRequestId = null;
                        }
                        dialog.dismiss();
                    });
            } else {
                Log.w(TAG, "⚠️ No request ID available, using fallback method");
                
                // Stop sounds and reset for fallback
                stopEmergencySound();
                EmergencySOSBackgroundService.dismissAllEmergencyNotifications();
                cancelAllSystemNotifications();
                synchronized (dialogLock) {
                    isEmergencyDialogShowing = false;
                    currentEmergencyRequestId = null;
                }
                dialog.dismiss();
                
                // For fallback, we still need to assign the rescuer
                assignRescuerToEmergency(seniorName, locationAddress, System.currentTimeMillis());
                Toast.makeText(this, getString(R.string.toast_assigned_to_emergency), Toast.LENGTH_LONG).show();
            }
        });
        
        // Show the dialog
        try {
            currentEmergencyDialog = builder.create();
            currentEmergencyDialog.show();
            Log.d(TAG, "✅ Emergency SOS alert dialog shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error showing emergency alert dialog: " + e.getMessage(), e);
            synchronized (dialogLock) {
                isEmergencyDialogShowing = false;
                currentEmergencyRequestId = null;
                currentEmergencyDialog = null;
            }
        }
    }
    
    private void showEmergencySOSAlert(String seniorName, String seniorPhone, String locationAddress, Long timestamp, String requestId) {
        Log.d(TAG, "🔍 [SHOW_DIALOG] showEmergencySOSAlert called for: " + seniorName);
        Log.d(TAG, "🔍 [SHOW_DIALOG] RequestId: " + requestId);
        Log.d(TAG, "🔍 [SHOW_DIALOG] isEmergencyDialogShowing: " + isEmergencyDialogShowing);
        
        // Enhanced activity state check
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show emergency alert dialog - activity is not in valid state (finishing: " + isFinishing() + ", destroyed: " + isDestroyed() + ")");
            // Stop sound since we can't show the dialog
            stopEmergencySound();
            return;
        }
        
        // Synchronized check to prevent race conditions
        synchronized (dialogLock) {
            // Check if dialog is already showing for the SAME requestId (prevent duplicates)
            // BUT allow different requestIds to show - multiple SOS alerts from different seniors should all be shown
            if (isEmergencyDialogShowing && requestId != null && requestId.equals(currentEmergencyRequestId)) {
                Log.w(TAG, "⚠️ [SHOW_DIALOG] Emergency dialog already showing for this requestId: " + requestId + ", ignoring duplicate call");
                return;
            }
            
            // If a different requestId wants to show while dialog is showing, allow it
            // This handles the case where 2 seniors send SOS - both should be shown sequentially via queue
            if (isEmergencyDialogShowing && requestId != null && !requestId.equals(currentEmergencyRequestId)) {
                Log.d(TAG, "🔄 [SHOW_DIALOG] Different emergency (requestId: " + requestId + ") wants to show while dialog is showing (requestId: " + currentEmergencyRequestId + ")");
                Log.d(TAG, "🔄 [SHOW_DIALOG] Dismissing current dialog to show new emergency");
                // Dismiss current dialog to show new one
                if (currentEmergencyDialog != null) {
                    try {
                        currentEmergencyDialog.dismiss();
                    } catch (Exception e) {
                        Log.e(TAG, "Error dismissing current dialog: " + e.getMessage());
                    }
                    currentEmergencyDialog = null;
                }
            }
            
            // Double-check activity state after acquiring lock
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "Cannot show emergency alert dialog - activity state changed during lock acquisition");
                // Stop sound since we can't show the dialog
                stopEmergencySound();
                return;
            }
            
            // Mark dialog as showing and track which emergency
            isEmergencyDialogShowing = true;
            currentEmergencyRequestId = requestId;
            Log.d(TAG, "🔍 [SHOW_DIALOG] Setting isEmergencyDialogShowing = true, requestId = " + requestId);
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_emergency_sos_alert));
        
        String timeStr = "Unknown time";
        if (timestamp != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            timeStr = sdf.format(new java.util.Date(timestamp));
        }
        
        String message = "🚨 URGENT: Senior needs immediate help!\n\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + seniorPhone + "\n" +
                        "📍 Location: " + locationAddress + "\n" +
                        "⏰ Time: " + timeStr + "\n\n" +
                        "⚠️ Please respond immediately!";
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(false);
        
        // Respond to emergency button
        builder.setPositiveButton(getString(R.string.button_respond_now), (dialog, which) -> {
            Log.d(TAG, "🚨🚨🚨 RESPOND NOW BUTTON CLICKED 🚨🚨🚨");
            Log.d(TAG, "🔍 [RESPOND_NOW] Emergency: " + requestId);
            Log.d(TAG, "🔍 [RESPOND_NOW] Senior: " + seniorName);
            Log.d(TAG, "🔍 [RESPOND_NOW] Location: " + locationAddress);
            Log.d(TAG, "🔍 [RESPOND_NOW] Timestamp: " + timestamp);
            Log.d(TAG, "🔍 [RESPOND_NOW] Dialog dismissed: " + (dialog instanceof AlertDialog ? ((AlertDialog) dialog).isShowing() : "unknown"));
            
            // Stop ALL emergency sounds AND dismiss notifications immediately when rescuer responds
            stopEmergencySound(); // Stop dashboard sound
            EmergencySOSBackgroundService.dismissAllEmergencyNotifications(); // Stop background service sound AND dismiss notifications
            cancelAllSystemNotifications(); // Cancel all system notifications to stop notification channel sounds
            Log.d(TAG, "🔇 [RESPOND_NOW] All emergency sounds stopped and notifications dismissed");
            
            // Reset dialog flag safely
            synchronized (dialogLock) {
                isEmergencyDialogShowing = false;
                currentEmergencyRequestId = null;
            }
            Log.d(TAG, "🔍 [RESPOND_NOW] Reset isEmergencyDialogShowing = false and cleared requestId");
            
            // Clear all emergency notifications and dialogs
            clearAllEmergencyNotifications();
            Log.d(TAG, "🔍 [RESPOND_NOW] Cleared all emergency notifications and dialogs");
            
            // Dismiss dialog immediately
            dialog.dismiss();
            Log.d(TAG, "🔍 [RESPOND_NOW] Dialog dismissed successfully");
            
            // Assign this rescuer to the emergency (this will launch Emergency Assignment Activity)
            if (requestId != null) {
                Log.d(TAG, "🔍 [RESPOND_NOW] Calling assignRescuerToEmergencyById with requestId: " + requestId);
                assignRescuerToEmergencyById(requestId);
            } else {
                Log.d(TAG, "🔍 [RESPOND_NOW] Calling assignRescuerToEmergency with seniorName: " + seniorName);
                assignRescuerToEmergency(seniorName, locationAddress, timestamp);
            }
            
            // Show confirmation to rescuer
            Toast.makeText(this, getString(R.string.toast_assigned_to_emergency), Toast.LENGTH_LONG).show();
            Log.d(TAG, "🔍 [RESPOND_NOW] Toast shown to rescuer");
        });
        
        // Dismiss any existing emergency dialog before showing new one
        if (currentEmergencyDialog != null && currentEmergencyDialog.isShowing()) {
            currentEmergencyDialog.dismiss();
            Log.d(TAG, "Dismissed existing emergency dialog before showing new one");
        }
        
        AlertDialog dialog = builder.create();
        
        // Add dismiss listener to reset flag if dialog is dismissed by other means
        dialog.setOnDismissListener(dialogInterface -> {
            synchronized (dialogLock) {
                isEmergencyDialogShowing = false;
                currentEmergencyRequestId = null;
            }
            Log.d(TAG, "🔍 [DIALOG_DISMISSED] Reset isEmergencyDialogShowing = false and cleared requestId");
        });
        
        dialog.show();
        
        // Store reference to current emergency dialog
        currentEmergencyDialog = dialog;
        
        // Set white background for better readability
        dialog.getWindow().getDecorView().setBackgroundColor(0xFFFFFFFF); // White background
    }
    
    /**
     * Show informational alert dialog when emergency is already assigned to another rescuer
     */
    private void showEmergencyAlreadyAssignedDialog(String seniorName, String seniorPhone, String locationAddress,
                                                   String assignedRescuerName, String assignedRescuerTeam,
                                                   Long assignedAt, String requestId) {
        Log.d(TAG, "ℹ️ [ASSIGNED_DIALOG] Showing emergency already assigned dialog");
        
        // Check if activity is in valid state
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show assigned dialog - activity is not in valid state");
            return;
        }
        
        // Format timestamp
        String timeStr = "Just now";
        if (assignedAt != null) {
            long timeDiff = System.currentTimeMillis() - assignedAt;
            if (timeDiff < 60000) { // Less than 1 minute
                timeStr = "Just now";
            } else if (timeDiff < 3600000) { // Less than 1 hour
                timeStr = (timeDiff / 60000) + " minutes ago";
            } else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
                timeStr = "at " + sdf.format(new java.util.Date(assignedAt));
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.emergency_already_assigned_title));
        
        String message = "This emergency has been assigned to another rescuer.\n\n" +
                        "📋 Emergency Details:\n" +
                        "👤 Senior: " + seniorName + "\n" +
                        "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not available") + "\n" +
                        "📍 Location: " + locationAddress + "\n\n" +
                        "✅ Assigned To:\n" +
                        "👨‍⚕️ Rescuer: " + assignedRescuerName + "\n" +
                        "🏢 Team: " + (assignedRescuerTeam != null ? assignedRescuerTeam : "Emergency Response Team") + "\n" +
                        "⏰ Assigned: " + timeStr + "\n\n" +
                        "This emergency is being handled. You can focus on other emergencies.";
        
        builder.setMessage(message);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setCancelable(true);
        
        // OK button
        builder.setPositiveButton("OK, Got It", (dialog, which) -> {
            Log.d(TAG, "ℹ️ [ASSIGNED_DIALOG] User acknowledged assigned emergency");
            dialog.dismiss();
        });
        
        // Optional: Call senior button (in case they need to coordinate)
        builder.setNeutralButton("📞 Call Senior", (dialog, which) -> {
            Log.d(TAG, "ℹ️ [ASSIGNED_DIALOG] User chose to call senior for coordination");
            if (seniorPhone != null && !seniorPhone.isEmpty()) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL);
                callIntent.setData(Uri.parse("tel:" + seniorPhone));
                startActivity(callIntent);
            } else {
                Toast.makeText(this, getString(R.string.senior_phone_not_available), Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        
        // Create and show the dialog
        try {
            AlertDialog dialog = builder.create();
            dialog.show();
            Log.d(TAG, "✅ [ASSIGNED_DIALOG] Emergency already assigned dialog shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "❌ [ASSIGNED_DIALOG] Error showing dialog: " + e.getMessage(), e);
        }
    }
    
    private void showEmergencySOSSystemNotification(String seniorName, String locationAddress, String notificationId) {
        Log.d(TAG, "🔔 Creating emergency SOS system notification for: " + seniorName);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Create intent for when notification is tapped
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationIntent.putExtra("emergency_sos_clicked", true);
        notificationIntent.putExtra("senior_name", seniorName);
        notificationIntent.putExtra("location_address", locationAddress);
        
        // Create pending intent
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, 
                notificationId.hashCode(), 
                notificationIntent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "emergency_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.text_emergency_sos_title, seniorName))
                .setContentText(getString(R.string.text_emergency_sos_content, locationAddress))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000}) // Longer vibration pattern
                .setLights(0xFFFF0000, 1000, 1000) // Red light blinking
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        notificationManager.notify(notificationId.hashCode(), builder.build());
        Log.d(TAG, "Emergency SOS system notification sent for: " + seniorName);
    }
    
    
    private void openNavigationToSenior(String seniorName, String locationAddress) {
        try {
            // For now, open general navigation to the area
            // In a real implementation, you'd get the exact coordinates
            String destination = "Angeles City, Pampanga"; // Default to Angeles City
            
            Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + 
                Uri.encode(destination) + "&travelmode=driving");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                Toast.makeText(this, getString(R.string.toast_opening_navigation_to, seniorName), Toast.LENGTH_LONG).show();
            } else {
                // Fallback to web browser
                intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening navigation to senior: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.toast_error_opening_navigation, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void startEmergencySOSBackgroundService() {
        if (userType != null && userType.equals("rescuer")) {
            Log.d(TAG, "Starting EmergencySOSBackgroundService for rescuer");
            
            Intent serviceIntent = new Intent(this, EmergencySOSBackgroundService.class);
            serviceIntent.putExtra("action", "start_monitoring");
            
            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }
    
    
    private Uri getCustomAlarmSound() {
        try {
            // Try to use custom alarm sound
            Uri customSound = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.emergency_alarm);
            Log.d(TAG, "Custom alarm sound URI: " + customSound.toString());
            Log.d(TAG, "Package name: " + getPackageName());
            Log.d(TAG, "Resource ID: " + R.raw.emergency_alarm);
            
            // Test if the resource exists
            try {
                android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.emergency_alarm);
                if (afd != null) {
                    afd.close();
                    Log.d(TAG, "✅ Custom alarm sound file exists and is accessible");
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Custom alarm sound file not accessible: " + e.getMessage());
            }
            
            return customSound;
        } catch (Exception e) {
            // Fallback to system alarm sound if custom file doesn't exist
            Log.w(TAG, "Custom alarm sound not found, using system alarm sound. Error: " + e.getMessage());
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
    }
    

    private void openLocationInInternalMap(Double latitude, Double longitude, String address,
                                           String seniorName, String seniorPhone, String helpRequestId) {
        if (latitude != null && longitude != null) {
            Intent mapIntent = new Intent(this, RescuerNavigationActivity.class);

            // Pass emergency data to the dedicated navigation activity
            mapIntent.putExtra("latitude", latitude);
            mapIntent.putExtra("longitude", longitude);
            mapIntent.putExtra("locationAddress", address);
            mapIntent.putExtra("seniorName", seniorName);
            mapIntent.putExtra("seniorPhone", seniorPhone != null ? seniorPhone : "");
            mapIntent.putExtra("helpRequestId", helpRequestId);

            startActivity(mapIntent);
            
            // Show toast to guide rescuer
            Toast.makeText(this, getString(R.string.text_opening_dedicated_navigation, seniorName), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.emergency_location_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGoogleNavigation(Double latitude, Double longitude, String address,
                                     String seniorName, String seniorPhone, String helpRequestId) {
        Log.d("Rescuer_Dashboard", "openGoogleNavigation called with: " + latitude + ", " + longitude);
        
        if (latitude != null && longitude != null) {
            try {
                Intent navigationIntent = new Intent(this, RescuerNavigationSDKActivity.class);

                // Pass emergency data to the Rescuer Navigation activity
                navigationIntent.putExtra("latitude", latitude);
                navigationIntent.putExtra("longitude", longitude);
                navigationIntent.putExtra("locationAddress", address);
                navigationIntent.putExtra("seniorName", seniorName);
                navigationIntent.putExtra("seniorPhone", seniorPhone != null ? seniorPhone : "");
                navigationIntent.putExtra("helpRequestId", helpRequestId);

                Log.d("Rescuer_Dashboard", "Starting RescuerNavigationActivity");
                startActivity(navigationIntent);
                
                // Show toast to guide rescuer
                Toast.makeText(this, getString(R.string.text_opening_google_maps_to, seniorName), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e("Rescuer_Dashboard", "Error starting RescuerNavigationActivity", e);
                Toast.makeText(this, getString(R.string.text_error_opening_navigation_long, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e("Rescuer_Dashboard", "Invalid coordinates: " + latitude + ", " + longitude);
            Toast.makeText(this, getString(R.string.emergency_location_not_available), Toast.LENGTH_SHORT).show();
        }
    }

    private void callSenior(String phoneNumber) {
        Intent callIntent = new Intent(Intent.ACTION_DIAL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        startActivity(callIntent);
    }

    private void openGoogleMapsNavigation(Double latitude, Double longitude, String destinationAddress) {
        if (latitude == null || longitude == null) {
            Toast.makeText(this, getString(R.string.destination_location_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.current_location_not_available_wait), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Create Google Maps navigation intent
            String destination = latitude + "," + longitude;
            String source = currentLat + "," + currentLong;
            
            // Use Google Maps navigation URL
            String navigationUrl = "https://www.google.com/maps/dir/" + source + "/" + destination;
            
            Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUrl));
            navigationIntent.setPackage("com.google.android.apps.maps");
            navigationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Check if Google Maps is installed
            if (navigationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navigationIntent);
                Toast.makeText(this, getString(R.string.opening_google_maps_navigation, destinationAddress), Toast.LENGTH_SHORT).show();
            } else {
                // Fallback to web browser if Google Maps app is not installed
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUrl));
                startActivity(webIntent);
                Toast.makeText(this, getString(R.string.opening_browser_navigation, destinationAddress), Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Maps navigation", e);
            Toast.makeText(this, getString(R.string.error_opening_navigation), Toast.LENGTH_SHORT).show();
        }
    }

    private void openExternalGoogleMapsNavigation(Double latitude, Double longitude, String destinationAddress, String seniorName, String seniorPhone, String helpRequestId) {
        if (latitude == null || longitude == null) {
            Toast.makeText(this, getString(R.string.text_destination_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.text_current_location_wait_long), Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Create Google Maps navigation intent with turn-by-turn directions
            String navigationUri = String.format("google.navigation:q=%f,%f&mode=d", latitude, longitude);
            Intent navigationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(navigationUri));
            navigationIntent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps is installed
            if (navigationIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(navigationIntent);
                Toast.makeText(this, getString(R.string.opening_google_maps_to_senior, seniorName), Toast.LENGTH_LONG).show();
                
                // Also show a dialog with emergency details
                showEmergencyDetailsDialog(seniorName, seniorPhone, destinationAddress, helpRequestId);
            } else {
                // Fallback to web-based Google Maps
                String webMapsUri = String.format("https://www.google.com/maps/dir/?api=1&destination=%f,%f&travelmode=driving", 
                    latitude, longitude);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webMapsUri));
                startActivity(webIntent);
                Toast.makeText(this, getString(R.string.opening_web_navigation_to_senior, seniorName), Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Maps navigation", e);
            Toast.makeText(this, getString(R.string.error_opening_navigation), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEmergencyDetailsDialog(String seniorName, String seniorPhone, String destinationAddress, String helpRequestId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.text_emergency_response_details));
        builder.setMessage(String.format(
            "Senior: %s\n" +
            "Phone: %s\n" +
            "Address: %s\n" +
            "Help Request ID: %s\n\n" +
            "Google Maps navigation is now active. " +
            "You can return to this app to call the senior or view more details.",
            seniorName != null ? seniorName : "Unknown",
            seniorPhone != null ? seniorPhone : "Not available",
            destinationAddress != null ? destinationAddress : "Location only",
            helpRequestId
        ));
        
        builder.setPositiveButton("📞 Call Senior", (dialog, which) -> {
            if (seniorPhone != null && !seniorPhone.isEmpty()) {
                callSenior(seniorPhone);
            } else {
                Toast.makeText(this, getString(R.string.phone_number_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showSystemNotification(String title, String message, String helpRequestId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create intent for when notification is tapped
        Intent notificationIntent = new Intent(this, Rescuer_Dashboard.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        notificationIntent.putExtra("notification_clicked", true);
        notificationIntent.putExtra("helpRequestId", helpRequestId);

        // Create pending intent
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, 
                helpRequestId.hashCode(), 
                notificationIntent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "emergency_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent) // Set the pending intent
                .setSound(getCustomAlarmSound())
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setLights(0xFFFF0000, 1000, 1000); // Red light blinking

        notificationManager.notify(helpRequestId.hashCode(), builder.build());
        Log.d(TAG, "System notification sent with ID: " + helpRequestId.hashCode());
    }

    private void checkNotificationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                // Request notification permission for Android 13+
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    /**
     * Request SMS permission for emergency notifications
     */
    private void requestSMSPermissionForEmergencyNotifications() {
        Log.d(TAG, "📱 Checking SMS permission for emergency notifications...");
        
        if (!PermissionManager.hasSMSPermission(this)) {
            Log.d(TAG, "📱 SMS permission not granted, showing permission dialog...");
            
            // Show explanation dialog first
            new AlertDialog.Builder(this)
                .setTitle("📱 SMS Permission Required")
                .setMessage("This app needs SMS permission to send emergency notifications to seniors' emergency contacts when you respond to SOS calls.\n\n" +
                           "This helps ensure that family members are immediately informed when you're responding to an emergency.\n\n" +
                           "Would you like to grant SMS permission?")
                .setPositiveButton(getString(R.string.grant_permission_button), (dialog, which) -> {
                    Log.d(TAG, "📱 User agreed to grant SMS permission, requesting...");
                    PermissionManager.requestSMSPermission(this);
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    Log.d(TAG, "📱 User declined SMS permission");
                    Toast.makeText(this, getString(R.string.sms_permission_declined), Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
        } else {
            Log.d(TAG, "📱 SMS permission already granted");
            // Test SMS functionality
            testSMSSending();
        }
    }

    /**
     * Test SMS sending functionality
     */
    private void testSMSSending() {
        Log.d(TAG, "🧪 Testing SMS functionality...");
        
        // Show test dialog
        new AlertDialog.Builder(this)
            .setTitle("🧪 Test SMS Functionality")
            .setMessage("SMS permission is granted. Would you like to test SMS sending?\n\n" +
                       "This will send a test SMS to verify the functionality works.")
            .setPositiveButton("Test SMS", (dialog, which) -> {
                Log.d(TAG, "🧪 User wants to test SMS, sending test message...");
                
                // Send test SMS
                EmergencyContactSMSService smsService = EmergencyContactSMSService.getInstance(this);
                smsService.sendTestSMS("+639123456789", "🧪 Test SMS from SAGIP Emergency System - SMS functionality is working!");
                
                Toast.makeText(this, getString(R.string.test_sms_sent), Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Skip Test", (dialog, which) -> {
                Log.d(TAG, "🧪 User skipped SMS test");
            })
            .setCancelable(true)
            .show();
    }

    /**
     * Setup test SMS button for debugging
     */
    private void setupTestSMSButton() {
        // Button testSMSButton = findViewById(R.id.btnTestSMS); // Button not in layout
        // Test SMS button functionality commented out - button not present in layout
        Log.d(TAG, "Test SMS button setup skipped - button not in layout");
        
        // Add debug emergency contacts button
        // Button debugContactsButton = findViewById(R.id.btnDebugContacts); // Button not in layout
        // Debug contacts and test emergency flow buttons commented out - buttons not in layout
        Log.d(TAG, "Debug buttons setup skipped - buttons not in layout");
    }

    /**
     * Test the complete emergency SMS flow
     */
    private void testEmergencySMSFlow() {
        Log.d(TAG, "🚨 Testing complete emergency SMS flow...");
        
        // Check SMS permission first
        if (!PermissionManager.hasSMSPermission(this)) {
            Log.e(TAG, "❌ SMS permission not granted for emergency flow test");
            Toast.makeText(this, getString(R.string.sms_permission_not_granted), Toast.LENGTH_LONG).show();
            return;
        }
        
        // Get current user info
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "❌ No user logged in for emergency flow test");
            Toast.makeText(this, getString(R.string.no_user_logged_in), Toast.LENGTH_SHORT).show();
            return;
        }
        
        String uid = currentUser.getUid();
        Log.d(TAG, "🚨 Testing emergency flow for UID: " + uid);
        
        // Test with mock emergency data
        EmergencyContactSMSService smsService = EmergencyContactSMSService.getInstance(this);
        smsService.sendEmergencySMSNotifications(
            uid, // seniorUid
            "Test Senior", // seniorName
            "Test Rescuer", // rescuerName
            "+639123456789", // rescuerPhone
            "Test Team", // rescuerTeam
            "Test Location, Test City", // locationAddress
            "Medical Emergency" // emergencyType
        );
        
        Toast.makeText(this, getString(R.string.emergency_sms_flow_test_initiated), Toast.LENGTH_LONG).show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "emergency_channel",
                    "Emergency Notifications",
                    NotificationManager.IMPORTANCE_MAX
            );
            channel.setDescription("Emergency help requests from seniors");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500, 1000});
            
            // Set custom alarm sound with proper audio attributes
            Uri alarmSound = getCustomAlarmSound();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // Ensure sound plays even in silent mode
                    .build();
            channel.setSound(alarmSound, audioAttributes);
            channel.enableLights(true);
            channel.setLightColor(0xFFFF0000); // Red light
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // =============== HOSPITAL NAVIGATION ===============


    private void requestLocationAndStartTestNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLong = location.getLongitude();
                        
                        // Get hospital location from database instead of hardcoded coordinates
                        getHospitalLocationAndStartNavigation("Christ in You Heale Parish");
                    } else {
                        Toast.makeText(this, getString(R.string.text_could_not_get_location), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, getString(R.string.text_error_getting_location, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, getString(R.string.text_location_permission_required), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void getHospitalLocationAndStartNavigation(String hospitalName) {
        // Query hospitals from database to get the actual location
        db.collection("Sagip")
          .document("users")
          .collection("hospital")
          .whereEqualTo("hospitalName", hospitalName)
          .whereEqualTo("isOperational", true)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              if (!queryDocumentSnapshots.isEmpty()) {
                  // Get the first matching hospital
                  QueryDocumentSnapshot document = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                  Hospital hospital = document.toObject(Hospital.class);
                  hospital.hospitalId = document.getId();
                  
                  if (hospital.location != null) {
                      // Use the hospital's actual location from database
                      openGoogleNavigation(
                          hospital.location.getLatitude(), 
                          hospital.location.getLongitude(),
                          hospital.address != null ? hospital.address : hospitalName,
                          "Test Senior", 
                          "09123456789", 
                          "test123"
                      );
                      Toast.makeText(this, getString(R.string.text_testing_navigation_to, hospitalName), Toast.LENGTH_LONG).show();
                  } else {
                      Toast.makeText(this, getString(R.string.text_hospital_location_not_found), Toast.LENGTH_SHORT).show();
                  }
              } else {
                  Toast.makeText(this, getString(R.string.text_hospital_not_found, hospitalName), Toast.LENGTH_SHORT).show();
              }
          })
          .addOnFailureListener(e -> {
              Log.e("Rescuer_Dashboard", "Error retrieving hospital location from database", e);
              Toast.makeText(this, getString(R.string.text_error_retrieving_hospital, e.getMessage()), Toast.LENGTH_SHORT).show();
          });
    }
    
    private void navigateToNearestHospital() {
        if (currentLat == 0.0 && currentLong == 0.0) {
            Toast.makeText(this, getString(R.string.current_location_not_available_permissions),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent mapIntent = new Intent(this, MyGoogleMAp.class);

        // Use consistent extra names that match MyGoogleMAp expectations
        mapIntent.putExtra("latitude", currentLat);
        mapIntent.putExtra("longitude", currentLong);
        mapIntent.putExtra("locationAddress", "Navigate to nearest hospital");
        mapIntent.putExtra("isEmergencyMode", false);
        mapIntent.putExtra("isRescuerMode", false);

        startActivity(mapIntent);
    }

    // =============== AUTHENTICATION & USER MANAGEMENT ===============

    private void checkAuthState() {
        Log.d(TAG, "🔐 Checking authentication state...");

        // Always check Firebase Auth first to ensure user is still authenticated
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Log.d(TAG, "Firebase currentUser: " + (currentUser != null ? currentUser.getUid() : "null"));

        if (currentUser != null) {
            // User is authenticated in Firebase
            userId = currentUser.getUid();
            String phoneNumber = currentUser.getPhoneNumber();
            
            // Debug information
            Log.d(TAG, "✅ User authenticated in Firebase");
            Log.d(TAG, "User ID: " + userId);
            Log.d(TAG, "Phone Number: " + phoneNumber);
            Log.d(TAG, "Email: " + currentUser.getEmail());

            // Always detect user type from database for consistency across devices
            // This ensures the same behavior regardless of SharedPreferences state
            Log.d(TAG, "Detecting user type from database for consistency...");
            detectAndLoadUserType(userId, phoneNumber);
        } else {
            // No Firebase user, check if we have any stored credentials to clear
            boolean wasLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
            if (wasLoggedIn) {
                Log.d(TAG, "User was logged in but Firebase session expired, clearing data...");
                clearStoredCredentials();
            }

            Log.d(TAG, "No authenticated user found, redirecting to login...");
            navigateToLogin();
        }
    }

    private void saveUserToPreferences(String userId, String userType, String phoneNumber) {
        Log.d(TAG, "Saving user to SharedPreferences: " + userId + ", " + userType);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_TYPE, userType);
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        editor.apply();
    }

    private void checkUserStatusAndRedirect() {
        if (userId == null) {
            Log.w(TAG, "userId is null, cannot check status");
            return;
        }

        Log.d(TAG, "Checking user status for userId: " + userId);

        // Add timeout handling
        android.os.Handler timeoutHandler = new android.os.Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            Log.w(TAG, "Database query timeout, showing retry dialog");
            showRetryDialog("Request timed out. Please check your internet connection and try again.");
        };
        
        // Set 10-second timeout
        timeoutHandler.postDelayed(timeoutRunnable, 10000);

        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    // Cancel timeout since we got a response
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    
                    if (documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        Log.d(TAG, "User status: " + status);
                        Log.d(TAG, "Document data: " + documentSnapshot.getData());
                        
                        if ("new".equals(status)) {
                            Log.d(TAG, "User status is 'new', redirecting to registration");
                            // User status is "new", redirect to registration
                            Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else if (status == null || status.isEmpty()) {
                            Log.w(TAG, "User status is null or empty, treating as registered");
                            // Status is null/empty, treat as registered and proceed
                            loadUserData(userId);
                        } else {
                            Log.d(TAG, "User status is not 'new', proceeding to dashboard");
                            // User is registered, proceed with dashboard initialization
                            loadUserData(userId);
                        }
                    } else {
                        Log.w(TAG, "User document does not exist, redirecting to registration");
                        // User document doesn't exist, redirect to registration
                        Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    // Cancel timeout since we got an error
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    Log.e(TAG, "Error checking user status: " + e.getMessage(), e);
                    // Instead of immediately redirecting on error, show a retry dialog
                    showRetryDialog("Unable to verify your registration status. Please check your internet connection and try again.");
                });
    }

    private void showRetryDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.connection_error_title))
                .setMessage(message)
                .setPositiveButton("Retry", (dialog, which) -> {
                    // Retry the status check
                    checkUserStatusAndRedirect();
                })
                .setNegativeButton("Go to Registration", (dialog, which) -> {
                    // User chooses to go to registration
                    Intent intent = new Intent(Rescuer_Dashboard.this, Rescuer_Registration.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void navigateToLogin() {
        Log.d(TAG, "Navigating to login screen...");
        Intent intent = new Intent(Rescuer_Dashboard.this, MainActivity.class);
        // Clear the back stack so user can't press back to return after logging out
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void loadUserData(String uid) {
        Log.d(TAG, "Loading user data for: " + uid + " in collection: " + userType);

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                Log.d(TAG, "User document found, loading data...");
                                loadUserDataFromDocument(document);

                                // Ensure user credentials are saved
                                FirebaseUser currentUser = mAuth.getCurrentUser();
                                if (currentUser != null) {
                                    saveUserToPreferences(uid, userType, currentUser.getPhoneNumber());
                                }

                                // Request SMS permission for emergency notifications
                                requestSMSPermissionForEmergencyNotifications();

                                // Emergency listener will be started in onResume()
                            } else {
                                Log.e(TAG, "User document does not exist for UID: " + uid + " in collection: " + userType);

                                // Document doesn't exist, try to detect correct user type
                                FirebaseUser currentUser = mAuth.getCurrentUser();
                                if (currentUser != null) {
                                    detectAndLoadUserType(uid, currentUser.getPhoneNumber());
                                } else {
                                    Toast.makeText(Rescuer_Dashboard.this,
                                            "User profile not found. Please login again.",
                                            Toast.LENGTH_LONG).show();
                                    clearStoredCredentials();
                                    navigateToLogin();
                                }
                            }
                        } else {
                            Log.e(TAG, "Error loading user data: " + task.getException().getMessage());
                            Toast.makeText(Rescuer_Dashboard.this,
                                    "Error loading user data. Please check your connection and try again.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void detectAndLoadUserType(String uid, String phoneNumber) {
        Log.d(TAG, "🔍 Starting user type detection...");
        Log.d(TAG, "UID: " + uid);
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "Firebase User: " + (mAuth.getCurrentUser() != null ? "Available" : "Null"));

        // Always check UID-based first for rescuer users to ensure consistency
        // This prevents device-specific behavior based on phone number availability
        // Use consistent order: rescuer first, then others
        String[] uidUserTypes = {"rescuer", "hospital", "barangay", "seniors"};
        Log.d(TAG, "Checking UID-based collections first for consistency...");
        Log.d(TAG, "Collections to check: " + java.util.Arrays.toString(uidUserTypes));
        checkUIDBasedUserTypes(uid, uidUserTypes, 0);
    }

    private void checkPhoneBasedUserTypes(String uid, String phoneNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.d(TAG, "Phone-based user not found, all search methods exhausted");
            showUserNotFoundError();
            return;
        }

        String currentUserType = userTypes[index];
        Log.d(TAG, "Checking phone-based user type: " + currentUserType);

        // Try both with and without +63 prefix
        String searchNumber = phoneNumber;
        if (phoneNumber.startsWith("+63")) {
            searchNumber = phoneNumber.substring(3); // Remove +63 prefix
        }
        
        db.collection("Sagip")
                .document("users")
                .collection(currentUserType)
                .whereEqualTo("mobileNumber", searchNumber)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Log.d(TAG, "User found in phone-based collection: " + currentUserType);
                        this.userType = currentUserType;
                        saveUserToPreferences(uid, currentUserType, phoneNumber);
                        loadUserData(uid);
                    } else {
                        // Try next user type
                        checkPhoneBasedUserTypes(uid, phoneNumber, userTypes, index + 1);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking phone-based user type " + currentUserType + ": " + e.getMessage());
                    // Try next user type
                    checkPhoneBasedUserTypes(uid, phoneNumber, userTypes, index + 1);
                });
    }

    private void checkUIDBasedUserTypes(String uid, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User not found in any collection after checking all types");
            Log.e(TAG, "UID: " + uid + ", Collections checked: " + java.util.Arrays.toString(userTypes));
            
            // Try alternative search methods before giving up
            Log.d(TAG, "Trying alternative search methods...");
            tryAlternativeUserSearch(uid);
            return;
        }

        String currentUserType = userTypes[index];
        Log.d(TAG, "Checking UID-based user type: " + currentUserType + " for UID: " + uid);

        db.collection("Sagip")
                .document("users")
                .collection(currentUserType)
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        Log.d(TAG, "✅ User found in UID-based collection: " + currentUserType);
                        Log.d(TAG, "Document data: " + document.getData());
                        this.userType = currentUserType;
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        String phoneNumber = currentUser != null ? currentUser.getPhoneNumber() : null;
                        saveUserToPreferences(uid, currentUserType, phoneNumber);
                        loadUserDataFromDocument(document);

                        // Emergency listener will be started in onResume()
                    } else {
                        Log.d(TAG, "❌ User not found in collection: " + currentUserType);
                        // Try next user type
                        checkUIDBasedUserTypes(uid, userTypes, index + 1);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking UID-based user type " + currentUserType + ": " + e.getMessage());
                    // Try next user type
                    checkUIDBasedUserTypes(uid, userTypes, index + 1);
                });
    }

    private void loadUserDataFromDocument(DocumentSnapshot document) {
        // Load cached name immediately for instant display
        loadCachedDisplayName();

        // Check for different name fields based on user type
        String displayName = null;

        if (userType.equals("rescuer")) {
            displayName = document.getString("rescuegroup");
        }

        if (displayName == null) {
            displayName = document.getString("firstName");
        }

        if (displayName == null) {
            displayName = document.getString("name");
        }

        if (displayName != null) {
            brgyName.setText(displayName);
            // Cache the name for future instant loading
            cacheDisplayName(displayName);
        } else {
            brgyName.setText(getString(R.string.user_name_not_available));
        }

        // Check if there's stored location data
        GeoPoint geoPoint = document.getGeoPoint("currentLocation");
        if (geoPoint != null) {
            currentLat = geoPoint.getLatitude();
            currentLong = geoPoint.getLongitude();
            updateLocationDisplay(currentLat, currentLong);
        }
        
        // Check for new hospital status update notifications
        if (userType.equals("rescuer") && userId != null) {
            Log.d(TAG, "=== CHECKING FOR HOSPITAL STATUS UPDATE NOTIFICATIONS IN LOADUSERDATA ===");
            Log.d(TAG, "User Type: " + userType);
            Log.d(TAG, "User ID: " + userId);
            
            // Stop background service since app is now active (prevents double notifications)
            // Get and store FCM token for real-time notifications
            getAndStoreFCMToken(userId, userType);
            
            // Check for notifications locally since app is active
            HospitalStatusUpdateNotificationService.checkAndDisplayNotificationsForRescuer(this, userId);
        } else {
            Log.d(TAG, "Skipping notification check in loadUserData - User Type: " + userType + ", User ID: " + userId);
        }
        
        // Add a test button to manually check notifications (for debugging)
    }
    /**
     * Starts the background notification service for rescuers
     */
    private void startRescuerBackgroundNotificationService() {
        Log.d(TAG, "Starting RescuerBackgroundNotificationService");
        
        // Check if service is already running
        if (isServiceRunning(RescuerBackgroundNotificationService.class)) {
            Log.d(TAG, "RescuerBackgroundNotificationService is already running");
            return;
        }
        
        // Request battery optimization exemption for better background execution
        requestBatteryOptimizationExemption();
        
        Intent serviceIntent = new Intent(this, RescuerBackgroundNotificationService.class);
        serviceIntent.putExtra("action", "start_monitoring");
        startService(serviceIntent);
    }
    
    /**
     * Stops the background notification service for rescuers
     */
    private void stopRescuerBackgroundNotificationService() {
        Log.d(TAG, "Stopping RescuerBackgroundNotificationService");
        
        Intent serviceIntent = new Intent(this, RescuerBackgroundNotificationService.class);
        serviceIntent.putExtra("action", "stop_monitoring");
        startService(serviceIntent);
    }
    
    /**
     * Tests the hospital status update notification system
     * This method can be called to verify notifications work when app is closed
     * DISABLED FOR PRODUCTION
     */
    public void testHospitalStatusNotification() {
        Log.d(TAG, "Test hospital status notification - DISABLED FOR PRODUCTION");
        Toast.makeText(this, getString(R.string.test_notifications_disabled), Toast.LENGTH_SHORT).show();
        
        // Uncomment below for testing:
        // NativeNotificationSender.sendHospitalUpdateNotificationToRescuers("Test Hospital", "Open", 5, 3);
    }
    
    /**
     * Stops all notification services when user logs out
     * This should only be called during logout, not when app is closing
     */
    private void stopAllNotificationServices() {
        Log.d(TAG, "Stopping all notification services due to logout");
        
        try {
            // Stop WorkManager
            NotificationWorkManager.stopNotificationMonitoring(this);
            
            // Stop AlternativeNotificationManager
            AlternativeNotificationManager.getInstance(this).stopMonitoring();
            
            // Stop WebSocketNotificationService
            Intent webSocketIntent = new Intent(this, WebSocketNotificationService.class);
            webSocketIntent.putExtra("action", "stop_monitoring");
            startService(webSocketIntent);
            
            // Stop RescuerForegroundService
            Intent rescuerIntent = new Intent(this, RescuerForegroundService.class);
            rescuerIntent.putExtra("action", "stop");
            startService(rescuerIntent);
            
            // Stop BackgroundNotificationService
            Intent backgroundIntent = new Intent(this, BackgroundNotificationService.class);
            backgroundIntent.putExtra("action", "stop");
            startService(backgroundIntent);
            
            // Stop HospitalStatusNotificationService
            Intent hospitalStatusIntent = new Intent(this, HospitalStatusNotificationService.class);
            hospitalStatusIntent.putExtra("action", "stop_monitoring");
            startService(hospitalStatusIntent);
            
            Log.d(TAG, "All notification services stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping notification services: " + e.getMessage());
        }
    }
    
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent();
                String packageName = getPackageName();
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    Log.d(TAG, "Requesting battery optimization exemption");
                    intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + packageName));
                    startActivity(intent);
                } else {
                    Log.d(TAG, "Battery optimization already disabled for this app");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to request battery optimization exemption: " + e.getMessage());
            }
        }
    }
    
    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Initialize FCM token for notifications when app starts
     */
    private void initializeFCMToken() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        
        if (userId != null && userType != null && "rescuer".equals(userType)) {
            Log.d(TAG, "Initializing FCM token for rescuer: " + userId);
            getAndStoreFCMToken(userId, userType);
        }
    }


    /**
     * Gets and stores FCM token for real-time notifications
     */
    private void getAndStoreFCMToken(String userId, String userType) {
        Log.d(TAG, "Getting FCM token for user: " + userId);
        
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Get new FCM registration token
                        String token = task.getResult();
                        Log.d(TAG, "FCM Registration Token: " + token);

                        // Store token in database
                        FCMNotificationSender.updateUserFCMToken(userId, userType, token);
                    }
                });
    }


    /**
     * Creates a test hospital status notification in Firestore
     */
    private void createTestHospitalStatusNotification() {
        if (userId == null || !"rescuer".equals(userType)) {
            Toast.makeText(this, getString(R.string.not_a_rescuer_user), Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Creating test hospital status notification for rescuer: " + userId);
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "hospital_status_update");
        notificationData.put("title", "🏥 Test Hospital Status Update");
        notificationData.put("message", "Test Hospital is now OPEN (Beds: 5, Doctors: 2)");
        notificationData.put("hospitalName", "Test Hospital");
        notificationData.put("hospitalStatus", "open");
        notificationData.put("availableBeds", 5);
        notificationData.put("availableDoctors", 2);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("read", false);
        
        db.collection("Sagip")
            .document("users")
            .collection("rescuer")
            .document(userId)
            .collection("notifications")
            .add(notificationData)
            .addOnSuccessListener(documentReference -> {
                Log.d(TAG, "✅ Test notification created: " + documentReference.getId());
                Toast.makeText(this, getString(R.string.test_notification_created), Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Failed to create test notification", e);
                Toast.makeText(this, getString(R.string.failed_to_create_test_notification), Toast.LENGTH_SHORT).show();
            });
    }

    private void tryAlternativeUserSearch(String uid) {
        Log.d(TAG, "Trying alternative user search for UID: " + uid);
        
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No Firebase user available for alternative search");
            showUserNotFoundError();
            return;
        }
        
        String phoneNumber = currentUser.getPhoneNumber();
        if (phoneNumber != null) {
            Log.d(TAG, "Trying phone-based search with number: " + phoneNumber);
            // Try phone-based search as fallback
            String[] phoneUserTypes = {"rescuer", "seniors", "barangay", "hospital"};
            checkPhoneBasedUserTypes(uid, phoneNumber, phoneUserTypes, 0);
        } else {
            Log.e(TAG, "No phone number available for alternative search");
            showUserNotFoundError();
        }
    }
    
    private void showUserNotFoundError() {
        Log.e(TAG, "User not found after all search methods");
        Toast.makeText(this, getString(R.string.user_profile_not_found), Toast.LENGTH_LONG).show();
        clearStoredCredentials();
        mAuth.signOut();
        navigateToLogin();
    }

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials...");
        
        // Stop ALL background services to prevent notifications to wrong user
        BackgroundServiceManager.stopAllBackgroundServices(this);
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        editor.apply();
        Log.d(TAG, "All stored credentials cleared");
    }

    // =============== LOCATION SERVICES ===============

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission already granted, start location updates
            startLocationUpdates();
            
            // Also check notification permissions since location is already granted
            checkNotificationPermissions();

            if (rescuerMap != null) {
                try {
                    rescuerMap.setMyLocationEnabled(true);
                } catch (SecurityException ignored) {}
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                startLocationUpdates();
                
                // Now request notification permission immediately after location permission is granted
                checkNotificationPermissions();
            } else {
                // Permission denied, show a message
                Toast.makeText(this, getString(R.string.location_permission_denied),
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Notification permission granted
                Log.d(TAG, "✅ Notification permission granted by user");
                Toast.makeText(this, getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show();
            } else {
                // Notification permission denied
                Log.w(TAG, "❌ Notification permission denied by user");
                Toast.makeText(this, getString(R.string.notification_permission_denied_wont_receive), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == PermissionManager.SMS_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "📱 [PERMISSION_RESULT] SMS permission result: " + granted);
            
            if (granted) {
                Log.d(TAG, "📱 [PERMISSION_RESULT] SMS permission granted!");
                Toast.makeText(this, getString(R.string.sms_permission_granted_contacts_notified), Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "⚠️ [PERMISSION_RESULT] SMS permission denied");
                Toast.makeText(this, getString(R.string.sms_permission_denied_contacts_not_notified), Toast.LENGTH_LONG).show();
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

                    // Refresh hospitals on map when location updates
                    loadAndRenderNearbyHospitals();
                }
            }
        };
    }

    private void startLocationUpdates() {
        // Ensure locationCallback is initialized
        if (locationCallback == null) {
            Log.w(TAG, "LocationCallback is null, creating callback...");
            createLocationCallback();
        }

        // Ensure locationRequest is initialized
        if (locationRequest == null) {
            Log.w(TAG, "LocationRequest is null, creating request...");
            createLocationRequest();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            try {
                fusedLocationClient.requestLocationUpdates(locationRequest,
                        locationCallback,
                        Looper.getMainLooper());
                Log.d(TAG, "Location updates started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error starting location updates: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_starting_location_updates), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                Log.d(TAG, "Location updates stopped successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping location updates: " + e.getMessage());
            }
        }
    }

    private void updateLocationDisplay(double latitude, double longitude) {
        String locationText = getAddressFromLocation(latitude, longitude);
        if (locationText != null) {
            currentLocationText.setText(locationText);
        } else {
            // Fallback to coordinates if address can't be determined
            currentLocationText.setText(String.format(Locale.getDefault(),
                    "%.6f, %.6f", latitude, longitude));
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



    // =============== NAVIGATION SETUP ===============

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.rescuer_dashboard);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.rescuer_dashboard) {
                return true;
            } else if (itemId == R.id.rescuer_hospital) {
                startActivity(new Intent(getApplicationContext(), Rescuer_List.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.rescuer_profile) {
                startActivity(new Intent(getApplicationContext(), Rescuer_Profile.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
    
    // Request current location and start navigation
    private void requestLocationAndStartNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLong = location.getLongitude();
                        
                        // Get hospital location from database instead of hardcoded coordinates
                        getHospitalLocationAndStartNavigation("Christ in You Heale Parish");
                    } else {
                        Toast.makeText(this, getString(R.string.text_could_not_get_location), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, getString(R.string.text_error_getting_location, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, getString(R.string.text_location_permission_required), Toast.LENGTH_SHORT).show();
        }
    }
    
    // Open SOS Navigation using basic Google Maps
    private void openSOSNavigation() {
        Log.d("Rescuer_Dashboard", "openSOSNavigation called");
        
        try {
            Intent rescuerNavigationIntent = new Intent(this, RescuerNavigationActivity.class);
            
            // Pass SOS emergency data to the RescuerNavigationActivity
            rescuerNavigationIntent.putExtra("latitude", 15.22514); // Test SOS location
            rescuerNavigationIntent.putExtra("longitude", 120.62861);
            rescuerNavigationIntent.putExtra("locationAddress", "Emergency Location - Test SOS Call");
            rescuerNavigationIntent.putExtra("seniorName", "Test Senior");
            rescuerNavigationIntent.putExtra("seniorPhone", "09123456789");
            rescuerNavigationIntent.putExtra("helpRequestId", "test_sos_123");
            
            Log.d("Rescuer_Dashboard", "Starting RescuerNavigationActivity for SOS navigation");
            startActivity(rescuerNavigationIntent);
            
        } catch (Exception e) {
            Log.e("Rescuer_Dashboard", "Error starting RescuerNavigationActivity", e);
            Toast.makeText(this, String.format(getString(R.string.error_opening_sos_navigation), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
    
    // Request current location and start SOS navigation
    private void requestLocationAndStartSOSNavigation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLat = location.getLatitude();
                        currentLong = location.getLongitude();
                        
                        // Now start SOS navigation with current location
                        openSOSNavigation();
                        Toast.makeText(this, getString(R.string.sos_navigation_from_current_location), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, getString(R.string.text_could_not_get_location), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Rescuer_Dashboard", "Error getting current location", e);
                    Toast.makeText(this, getString(R.string.text_error_getting_location, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
        } else {
            Toast.makeText(this, getString(R.string.text_location_permission_required), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadCachedDisplayName() {
        String cachedName = sharedPreferences.getString(KEY_CACHED_DISPLAY_NAME, null);
        if (cachedName != null && !cachedName.isEmpty()) {
            brgyName.setText(cachedName);
            Log.d(TAG, "Loaded cached display name: " + cachedName);
        } else {
            brgyName.setText("Loading...");
            Log.d(TAG, "No cached display name found, showing loading...");
        }
    }

    private void cacheDisplayName(String displayName) {
        sharedPreferences.edit()
                .putString(KEY_CACHED_DISPLAY_NAME, displayName)
                .apply();
        Log.d(TAG, "Cached display name: " + displayName);
    }
    
    // Multiple Emergency Handling Methods
    private void showMultipleEmergenciesAlert(List<EmergencyQueueManager.EmergencyRequest> emergencies) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.multiple_emergencies_detected_title));
        
        StringBuilder message = new StringBuilder();
        message.append("🚨 ").append(emergencies.size()).append(" active emergencies detected!\n\n");
        message.append("📋 Emergency Queue (FIFO):\n\n");
        
        for (int i = 0; i < emergencies.size(); i++) {
            EmergencyQueueManager.EmergencyRequest emergency = emergencies.get(i);
            String status = emergency.status.equals("pending") ? "⏳ PENDING" : "👤 ASSIGNED";
            String timeStr = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new java.util.Date(emergency.timestamp));
            
            message.append((i + 1)).append(". ").append(emergency.seniorName)
                   .append(" - ").append(status).append(" (⏰ ").append(timeStr).append(")\n");
        }
        
        message.append("\n⚠️ Please respond to the first emergency in queue!");
        
        builder.setMessage(message.toString());
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        
        // View all emergencies button
        builder.setPositiveButton("📋 VIEW ALL", (dialog1, which) -> {
            showEmergencyListDialog(emergencies);
        });
        
        // Handle first in queue button
        if (!emergencies.isEmpty()) {
            EmergencyQueueManager.EmergencyRequest firstInQueue = emergencies.get(0);
            builder.setNeutralButton("🚨 HANDLE #1", (dialog1, which) -> {
                showEmergencySOSAlert(
                    firstInQueue.seniorName,
                    firstInQueue.seniorPhone,
                    firstInQueue.locationAddress,
                    firstInQueue.timestamp
                );
            });
        }
        
        // Cancel button
        builder.setNegativeButton("CANCEL", (dialog1, which) -> {
            dialog1.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showEmergencyListDialog(List<EmergencyQueueManager.EmergencyRequest> emergencies) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.format(getString(R.string.emergency_queue_active), emergencies.size()));
        
        // Create list items
        String[] items = new String[emergencies.size()];
        for (int i = 0; i < emergencies.size(); i++) {
            EmergencyQueueManager.EmergencyRequest emergency = emergencies.get(i);
            String status = emergency.status.equals("pending") ? "PENDING" : "ASSIGNED";
            String timeStr = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new java.util.Date(emergency.timestamp));
            items[i] = (i + 1) + ". " + emergency.seniorName + " - " + status + " (⏰ " + timeStr + ")";
        }
        
        builder.setItems(items, (dialog, which) -> {
            EmergencyQueueManager.EmergencyRequest selected = emergencies.get(which);
            showEmergencySOSAlert(
                selected.seniorName,
                selected.seniorPhone,
                selected.locationAddress,
                selected.timestamp
            );
        });
        
        builder.setNegativeButton("CLOSE", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void assignRescuerToEmergency(String seniorName, String locationAddress, Long timestamp) {
        Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Starting assignRescuerToEmergency");
        Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] SeniorName: " + seniorName);
        Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] LocationAddress: " + locationAddress);
        Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Timestamp: " + timestamp);
        
        // Get current rescuer ID
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            return;
        }
        
        String rescuerId = currentUser.getUid();
        Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Looking for emergency to assign rescuer: " + rescuerId);
        Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Searching for: " + seniorName + " at " + locationAddress + " at " + timestamp);
        
        // Mark rescuer as on assignment - they will NOT receive new alerts until they complete this one
        setRescuerOnAssignmentStatus(rescuerId, true);
        
        // Find the emergency request by senior name and timestamp
        List<EmergencyQueueManager.EmergencyRequest> activeEmergencies = 
                EmergencyQueueManager.getInstance(this).getActiveEmergencies();
        
        Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Found " + activeEmergencies.size() + " active emergencies");
        
        boolean found = false;
        for (EmergencyQueueManager.EmergencyRequest emergency : activeEmergencies) {
            Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Checking emergency: " + emergency.seniorName + " at " + emergency.locationAddress + " at " + emergency.timestamp);
            Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Name match: " + emergency.seniorName.equals(seniorName));
            Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Location match: " + emergency.locationAddress.equals(locationAddress));
            Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Time diff: " + Math.abs(emergency.timestamp - timestamp) + " (threshold: 60000)");
            
            if (emergency.seniorName.equals(seniorName) && 
                emergency.locationAddress.equals(locationAddress) &&
                Math.abs(emergency.timestamp - timestamp) < 60000) { // Within 1 minute
                
                Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] Match found! Calling assignRescuer...");
                // Assign this rescuer to the emergency
                EmergencyQueueManager.getInstance(this).assignRescuer(emergency.requestId, rescuerId);
                Log.d(TAG, "🔍 [ASSIGN_BY_DETAILS] assignRescuer called successfully");
                
                // Show popup confirmation to rescuer
                showRescuerAssignmentPopup(seniorName, locationAddress, rescuerId, emergency.requestId);
                
                Log.d(TAG, "👤 [ASSIGN_BY_DETAILS] Rescuer " + rescuerId + " assigned to emergency: " + emergency.requestId);
                found = true;
                break;
            }
        }
        
        if (!found) {
            Log.w(TAG, "⚠️ [ASSIGN_BY_DETAILS] No matching emergency found for assignment");
            Toast.makeText(this, getString(R.string.emergency_not_found_in_queue), Toast.LENGTH_SHORT).show();
            // Clear assignment status since no emergency was found
            setRescuerOnAssignmentStatus(rescuerId, false);
        }
    }
    
    private void assignRescuerToEmergencyById(String requestId) {
        Log.d(TAG, "🔍 [ASSIGN_BY_ID] Starting assignRescuerToEmergencyById for requestId: " + requestId);
        
        // Get current rescuer ID
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            return;
        }
        
        String rescuerId = currentUser.getUid();
        Log.d(TAG, "🔍 [ASSIGN_BY_ID] Assigning rescuer " + rescuerId + " to emergency: " + requestId);
        
        // Mark rescuer as on assignment - they will NOT receive new alerts until they complete this one
        setRescuerOnAssignmentStatus(rescuerId, true);
        
        // First try to get the emergency from local EmergencyQueueManager
        EmergencyQueueManager.EmergencyRequest emergency = 
                EmergencyQueueManager.getInstance(this).getEmergencyById(requestId);
        
        if (emergency != null) {
            Log.d(TAG, "🔍 [ASSIGN_BY_ID] Emergency found in local queue, calling assignRescuer...");
            Log.d(TAG, "🚨🚨🚨 ABOUT TO CALL assignRescuer 🚨🚨🚨");
            Log.d(TAG, "🚨🚨🚨 RequestId: " + requestId + ", RescuerId: " + rescuerId + " 🚨🚨🚨");
            // Emergency found in local queue, assign rescuer (this will send notification)
            EmergencyQueueManager.getInstance(this).assignRescuer(requestId, rescuerId);
            Log.d(TAG, "🔍 [ASSIGN_BY_ID] assignRescuer called successfully");
            
            // Show popup confirmation to rescuer
            showRescuerAssignmentPopup(emergency.seniorName, emergency.locationAddress, rescuerId, requestId);
            
            Log.d(TAG, "👤 [ASSIGN_BY_ID] Rescuer " + rescuerId + " assigned to emergency: " + requestId);
        } else {
            // Emergency not found in local queue, try to load from database
            Log.d(TAG, "⚠️ [ASSIGN_BY_ID] Emergency not found in local queue, loading from database...");
            loadEmergencyFromDatabaseAndAssign(requestId, rescuerId);
        }
    }

    private void setRescuerOnAssignmentStatus(String rescuerId, boolean onAssignment) {
        Log.d(TAG, "📝 Updating rescuer assignment status: " + rescuerId + " | onAssignment: " + onAssignment);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("onAssignment", onAssignment);
        updates.put("onAssignmentUpdatedAt", System.currentTimeMillis());
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Rescuer assignment status updated: onAssignment = " + onAssignment);
                    if (onAssignment) {
                        Log.d(TAG, "🚫 Rescuer " + rescuerId + " will NOT receive new alerts while on assignment");
                    } else {
                        Log.d(TAG, "✅ Rescuer " + rescuerId + " will now receive new alerts");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to update rescuer assignment status: " + e.getMessage());
                });
    }
    
    private void loadEmergencyFromDatabaseAndAssign(String requestId, String rescuerId) {
        Log.d(TAG, "🔍 [LOAD_FROM_DB] Starting loadEmergencyFromDatabaseAndAssign for requestId: " + requestId);
        
        // Load emergency from database using EmergencyQueueManager
        EmergencyQueueManager.getInstance(this).loadEmergencyByIdFromDatabase(requestId, new EmergencyQueueManager.EmergencyLoadCallback() {
            @Override
            public void onEmergencyLoaded(EmergencyQueueManager.EmergencyRequest emergency) {
                Log.d(TAG, "🔍 [LOAD_FROM_DB] onEmergencyLoaded callback triggered");
                if (emergency != null) {
                    Log.d(TAG, "🔍 [LOAD_FROM_DB] Emergency loaded from database, calling assignRescuer...");
                    Log.d(TAG, "🚨🚨🚨 ABOUT TO CALL assignRescuer FROM DATABASE 🚨🚨🚨");
                    Log.d(TAG, "🚨🚨🚨 RequestId: " + requestId + ", RescuerId: " + rescuerId + " 🚨🚨🚨");
                    // Assign rescuer to emergency (this will send notification)
                    EmergencyQueueManager.getInstance(Rescuer_Dashboard.this).assignRescuer(requestId, rescuerId);
                    Log.d(TAG, "🔍 [LOAD_FROM_DB] assignRescuer called successfully from database callback");
                    
                    // Update emergency list to reflect the change
                    updateSOSEmergencyList();
                    
                    // Show popup confirmation to rescuer
                    showRescuerAssignmentPopup(emergency.seniorName, emergency.locationAddress, rescuerId, requestId);
                    
                    Log.d(TAG, "👤 [LOAD_FROM_DB] Rescuer " + rescuerId + " assigned to emergency from database: " + requestId);
                } else {
                    Log.w(TAG, "⚠️ [LOAD_FROM_DB] Emergency not found in database with ID: " + requestId);
                    Toast.makeText(Rescuer_Dashboard.this, getString(R.string.emergency_not_found_in_database), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    
    private void showRescuerAssignmentPopup(String seniorName, String locationAddress, String rescuerId, String requestId) {
        Log.d(TAG, "🎉 showRescuerAssignmentPopup called for: " + seniorName + " at " + locationAddress + " (requestId: " + requestId + ")");
        
        // Check if activity is still valid before showing dialog
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Cannot show assignment popup - activity is not in valid state");
            return;
        }
        
        // Launch the new Emergency Assignment Activity
        launchEmergencyAssignmentActivity(seniorName, locationAddress, rescuerId, requestId);
    }
    
    private void launchEmergencyAssignmentActivityWithLocation(String seniorName, String locationAddress, String rescuerId, String requestId, Double seniorLat, Double seniorLng) {
        Log.d(TAG, "🔍 [LAUNCH] launchEmergencyAssignmentActivityWithLocation called for: " + seniorName);
        Log.d(TAG, "🔍 [LAUNCH] GPS coordinates: " + seniorLat + ", " + seniorLng);
        
        Intent intent = new Intent(this, EmergencyAssignmentActivity.class);
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("senior_phone", "Not available");
        intent.putExtra("location_address", locationAddress);
        intent.putExtra("senior_lat", seniorLat != null ? seniorLat : 0.0);
        intent.putExtra("senior_lng", seniorLng != null ? seniorLng : 0.0);
        intent.putExtra("assignment_time", System.currentTimeMillis());
        intent.putExtra("request_id", requestId);
        
        // Generate emergency ID for tracking
        String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
        intent.putExtra("emergency_id", emergencyId);
        
        Log.d(TAG, "🔍 [LAUNCH] Starting EmergencyAssignmentActivity with GPS coordinates from notification...");
        startActivity(intent);
        Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + seniorName + " with GPS coordinates from notification");
    }
    
    private void launchEmergencyAssignmentActivity(String seniorName, String locationAddress, String rescuerId, String requestId) {
        Log.d(TAG, "🚀🚀🚀 LAUNCHING EmergencyAssignmentActivity 🚀🚀🚀");
        Log.d(TAG, "🔍 [LAUNCH] Senior: " + seniorName);
        Log.d(TAG, "🔍 [LAUNCH] Location: " + locationAddress);
        Log.d(TAG, "🔍 [LAUNCH] RescuerId: " + rescuerId);
        Log.d(TAG, "🔍 [LAUNCH] RequestId: " + requestId);
        
        Intent intent = new Intent(this, EmergencyAssignmentActivity.class);
        intent.putExtra("senior_name", seniorName);
        intent.putExtra("location_address", locationAddress);
        intent.putExtra("rescuer_id", rescuerId);
        intent.putExtra("assignment_time", System.currentTimeMillis());
        intent.putExtra("request_id", requestId);
        
        if (requestId != null) {
            // Get emergency data to extract senior's location and phone
            EmergencyQueueManager.EmergencyRequest emergency = EmergencyQueueManager.getInstance(this).getEmergencyById(requestId);
            
            if (emergency != null) {
                // Debug: Check emergency data
                Log.d(TAG, "🔍 Emergency data debug:");
                Log.d(TAG, "  - requestId: " + emergency.requestId);
                Log.d(TAG, "  - seniorName: " + emergency.seniorName);
                Log.d(TAG, "  - seniorPhone: " + emergency.seniorPhone);
                Log.d(TAG, "  - locationAddress: " + emergency.locationAddress);
                
                // Use phone number from emergency data first (same as senior dashboard)
                String emergencyPhone = emergency.seniorPhone != null ? emergency.seniorPhone : "Not available";
                intent.putExtra("senior_phone", emergencyPhone);
                Log.d(TAG, "📞 Using phone from emergency data: " + emergencyPhone);
                
                // Get senior's coordinates from database
                getSeniorLocationAndLaunch(intent, emergency, rescuerId);
            } else {
                // Fallback if emergency not found in local cache - try to get senior's current location
                Log.w(TAG, "⚠️ Emergency not found in local cache, attempting to fetch senior's current location");
                intent.putExtra("senior_phone", "Not available");
                
                // Try to extract senior UID from requestId and fetch current location
                String seniorUid = extractUserIdFromRequestId(requestId);
                if (seniorUid != null) {
                    fetchSeniorCurrentLocationAndLaunch(intent, seniorUid, seniorName, rescuerId);
                } else {
                    // No senior UID available, use default location
        intent.putExtra("senior_lat", 0.0);
        intent.putExtra("senior_lng", 0.0);
        
        // Generate emergency ID for tracking
        String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
        intent.putExtra("emergency_id", emergencyId);
                
                // Debug: Check intent before launching
                String phoneInIntent = intent.getStringExtra("senior_phone");
                Log.d(TAG, "🔍 Phone in intent before launch (fallback): " + phoneInIntent);
        
        Log.d(TAG, "🔍 [LAUNCH] Starting EmergencyAssignmentActivity...");
        startActivity(intent);
        Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + seniorName + " (fallback mode)");
                }
            }
        } else {
            // Old system - no requestId available
            intent.putExtra("senior_phone", "Not available");
            intent.putExtra("senior_lat", 0.0);
            intent.putExtra("senior_lng", 0.0);
            
            // Generate emergency ID for tracking
            String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
            intent.putExtra("emergency_id", emergencyId);
            
            // Debug: Check intent before launching
            String phoneInIntent = intent.getStringExtra("senior_phone");
            Log.d(TAG, "🔍 Phone in intent before launch (old system): " + phoneInIntent);
            
            Log.d(TAG, "🔍 [LAUNCH] Starting EmergencyAssignmentActivity (old system)...");
            startActivity(intent);
            Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + seniorName + " (old system)");
        }
    }
    
    private void getSeniorLocationAndLaunch(Intent intent, EmergencyQueueManager.EmergencyRequest emergency, String rescuerId) {
        Log.d(TAG, "🔍 [SENIOR_LOCATION] Fetching senior's current location from database");
        
        // Extract senior UID from the emergency request ID
        String seniorUid = extractUserIdFromRequestId(emergency.requestId);
        
        if (seniorUid != null) {
            // Fetch senior's current location from their profile
            db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(seniorUid)
                .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                // Get senior's current location from latitude/longitude fields (not currentLocation string)
                                Double seniorLat = documentSnapshot.getDouble("latitude");
                                Double seniorLng = documentSnapshot.getDouble("longitude");
                                
                                if (seniorLat != null && seniorLng != null && seniorLat != 0.0 && seniorLng != 0.0) {
                                    // Use the senior's current GPS coordinates
                                    intent.putExtra("senior_lat", seniorLat);
                                    intent.putExtra("senior_lng", seniorLng);
                                    Log.d(TAG, "📍 Using senior's current GPS location from database: " + seniorLat + ", " + seniorLng);
                                    
                                    // Also get the address for display with proper error handling
                                    String currentLocationAddress = null;
                                    try {
                                        com.google.firebase.firestore.GeoPoint currentLocationGeoPoint = documentSnapshot.getGeoPoint("currentLocation");
                                        if (currentLocationGeoPoint != null) {
                                            currentLocationAddress = currentLocationGeoPoint.getLatitude() + ", " + currentLocationGeoPoint.getLongitude();
                                            Log.d(TAG, "📍 Senior's current address: " + currentLocationAddress);
                                        }
                                    } catch (Exception e) {
                                        Log.w(TAG, "currentLocation field is not a GeoPoint, trying as String: " + e.getMessage());
                                        // Fallback: try to get as String
                                        try {
                                            currentLocationAddress = documentSnapshot.getString("currentLocation");
                                            if (currentLocationAddress != null) {
                                                Log.d(TAG, "📍 Senior's current address (String): " + currentLocationAddress);
                                            }
                                        } catch (Exception e2) {
                                            Log.w(TAG, "currentLocation field is neither GeoPoint nor String: " + e2.getMessage());
                                            currentLocationAddress = null;
                                        }
                                    }
                                } else {
                                    // Fallback to emergency location if current GPS location not available
                                    if (emergency.location != null) {
                                        double lat = emergency.location.getLatitude();
                                        double lng = emergency.location.getLongitude();
                                        intent.putExtra("senior_lat", lat);
                                        intent.putExtra("senior_lng", lng);
                                        Log.w(TAG, "⚠️ Senior current GPS location not available, using emergency location: " + lat + ", " + lng);
                                    } else {
                                        // Final fallback: Use default location
                                        double defaultLat = 14.5995;
                                        double defaultLng = 120.9842;
                                        intent.putExtra("senior_lat", defaultLat);
                                        intent.putExtra("senior_lng", defaultLng);
                                        Log.w(TAG, "⚠️ No location data available, using fallback: " + defaultLat + ", " + defaultLng);
                                    }
                                }
                        
                        // Generate emergency ID for tracking
                        String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
                        intent.putExtra("emergency_id", emergencyId);
                        
                        // Debug: Check intent before launching
                        String phoneInIntent = intent.getStringExtra("senior_phone");
                        Log.d(TAG, "🔍 Phone in intent before launch: " + phoneInIntent);
                        
                        Log.d(TAG, "🔍 [LAUNCH] Starting EmergencyAssignmentActivity (with senior's current location)...");
                        startActivity(intent);
                        Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + emergency.seniorName + " with current location data");
                    } else {
                        Log.w(TAG, "⚠️ Senior document not found, using emergency location");
                        useEmergencyLocationAsFallback(intent, emergency, rescuerId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error fetching senior's current location: " + e.getMessage());
                    Log.w(TAG, "⚠️ Using emergency location as fallback due to database error");
                    useEmergencyLocationAsFallback(intent, emergency, rescuerId);
                });
        } else {
            Log.w(TAG, "⚠️ Could not extract senior UID from request ID, using emergency location");
            useEmergencyLocationAsFallback(intent, emergency, rescuerId);
        }
    }
    
    private void useEmergencyLocationAsFallback(Intent intent, EmergencyQueueManager.EmergencyRequest emergency, String rescuerId) {
        if (emergency.location != null) {
            // Use the location coordinates from the emergency data
            double lat = emergency.location.getLatitude();
            double lng = emergency.location.getLongitude();
            intent.putExtra("senior_lat", lat);
            intent.putExtra("senior_lng", lng);
            Log.d(TAG, "📍 Using emergency location as fallback: " + lat + ", " + lng);
        } else {
            // Final fallback: Use a default location
            double defaultLat = 14.5995; // Manila coordinates as fallback
            double defaultLng = 120.9842;
            intent.putExtra("senior_lat", defaultLat);
            intent.putExtra("senior_lng", defaultLng);
            Log.w(TAG, "⚠️ No location data available, using default: " + defaultLat + ", " + defaultLng);
        }
        
        // Generate emergency ID for tracking
        String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
        intent.putExtra("emergency_id", emergencyId);
        
        // Debug: Check intent before launching
        String phoneInIntent = intent.getStringExtra("senior_phone");
        Log.d(TAG, "🔍 Phone in intent before launch (fallback): " + phoneInIntent);
        
        Log.d(TAG, "🔍 [LAUNCH] Starting EmergencyAssignmentActivity (with fallback location)...");
        startActivity(intent);
        Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + emergency.seniorName + " with fallback location data");
    }
    
    private void fetchSeniorCurrentLocationAndLaunch(Intent intent, String seniorUid, String seniorName, String rescuerId) {
        Log.d(TAG, "🔍 [SENIOR_LOCATION_FALLBACK] Fetching senior's current location from database for UID: " + seniorUid);
        
        // Fetch senior's current location from their profile
        db.collection("Sagip")
            .document("users")
            .collection("seniors")
            .document(seniorUid)
            .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // Get senior's current location from latitude/longitude fields (not currentLocation string)
                            Double seniorLat = documentSnapshot.getDouble("latitude");
                            Double seniorLng = documentSnapshot.getDouble("longitude");
                            
                            if (seniorLat != null && seniorLng != null && seniorLat != 0.0 && seniorLng != 0.0) {
                                // Use the senior's current GPS coordinates
                                intent.putExtra("senior_lat", seniorLat);
                                intent.putExtra("senior_lng", seniorLng);
                                Log.d(TAG, "📍 Using senior's current GPS location from database (fallback): " + seniorLat + ", " + seniorLng);
                                
                                // Also get the address for display with proper error handling
                                String currentLocationAddress = null;
                                try {
                                    com.google.firebase.firestore.GeoPoint currentLocationGeoPoint = documentSnapshot.getGeoPoint("currentLocation");
                                    if (currentLocationGeoPoint != null) {
                                        currentLocationAddress = currentLocationGeoPoint.getLatitude() + ", " + currentLocationGeoPoint.getLongitude();
                                        Log.d(TAG, "📍 Senior's current address (fallback): " + currentLocationAddress);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "currentLocation field is not a GeoPoint, trying as String: " + e.getMessage());
                                    // Fallback: try to get as String
                                    try {
                                        currentLocationAddress = documentSnapshot.getString("currentLocation");
                                        if (currentLocationAddress != null) {
                                            Log.d(TAG, "📍 Senior's current address (fallback, String): " + currentLocationAddress);
                                        }
                                    } catch (Exception e2) {
                                        Log.w(TAG, "currentLocation field is neither GeoPoint nor String: " + e2.getMessage());
                                        currentLocationAddress = null;
                                    }
                                }
                            } else {
                                // Fallback: Use default location
                                double defaultLat = 14.5995;
                                double defaultLng = 120.9842;
                                intent.putExtra("senior_lat", defaultLat);
                                intent.putExtra("senior_lng", defaultLng);
                                Log.w(TAG, "⚠️ Senior current GPS location not available, using default: " + defaultLat + ", " + defaultLng);
                            }
                } else {
                    // Senior document not found, use default location
                    double defaultLat = 14.5995;
                    double defaultLng = 120.9842;
                    intent.putExtra("senior_lat", defaultLat);
                    intent.putExtra("senior_lng", defaultLng);
                    Log.w(TAG, "⚠️ Senior document not found, using default location: " + defaultLat + ", " + defaultLng);
                }
                
                // Generate emergency ID for tracking
                String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
                intent.putExtra("emergency_id", emergencyId);
                
                // Debug: Check intent before launching
                String phoneInIntent = intent.getStringExtra("senior_phone");
                Log.d(TAG, "🔍 Phone in intent before launch (fallback): " + phoneInIntent);
                
                Log.d(TAG, "🔍 [LAUNCH] Starting EmergencyAssignmentActivity (with senior's current location fallback)...");
                startActivity(intent);
                Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + seniorName + " with current location data (fallback)");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Error fetching senior's current location (fallback): " + e.getMessage());
                
                // Use default location on error
                double defaultLat = 14.5995;
                double defaultLng = 120.9842;
                intent.putExtra("senior_lat", defaultLat);
                intent.putExtra("senior_lng", defaultLng);
                
                // Generate emergency ID for tracking
                String emergencyId = "EMERGENCY_" + System.currentTimeMillis() + "_" + rescuerId;
                intent.putExtra("emergency_id", emergencyId);
                
                Log.d(TAG, "🔍 [LAUNCH] Starting EmergencyAssignmentActivity (with default location due to error)...");
                startActivity(intent);
                Log.d(TAG, "🚀 Launched EmergencyAssignmentActivity for: " + seniorName + " with default location due to error");
            });
    }
    
    private String extractUserIdFromRequestId(String requestId) {
        // Request ID format: "SOS_timestamp_userId"
        String[] parts = requestId.split("_");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
    
    // OLD SYSTEM REMOVED - Assignment popup now handled by EmergencyQueueManager


    private void saveLocationToFirestore(double latitude, double longitude) {
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            
            // Create GeoPoint for Firestore
            com.google.firebase.firestore.GeoPoint geoPoint = new com.google.firebase.firestore.GeoPoint(latitude, longitude);
            
            // Save to rescuer's document
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("currentLocation", geoPoint);
            locationData.put("lastLocationUpdate", System.currentTimeMillis());
            
            db.collection("Sagip")
                    .document("users")
                    .collection("rescuer")
                    .document(userId)
                    .update(locationData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "✅ Rescuer location saved to Firestore: " + latitude + ", " + longitude);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Error saving rescuer location to Firestore: " + e.getMessage());
                    });
        }
    }
    
}