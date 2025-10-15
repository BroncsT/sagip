package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RescuerDetailsActivity extends AppCompatActivity {

    private static final String TAG = "RescuerDetailsActivity";
    private static final String GOOGLE_MAPS_API_KEY = "AIzaSyBkf_blEJ4wc5Q_CNxABKK6-LFxDF-gWv0"; // Use the actual API key from strings.xml
    private static final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json";

    // UI Elements
    private TextView tvRescuerName, tvRescuerTeam, tvRescuerPhone;
    private TextView tvETA, tvDistance, tvLastUpdate, tvStatus, tvETAStatus;
    private Button btnCallRescuer, btnBack, btnRefreshETA;
    private Button btnTestETA, btnCheckDatabase, btnForceETA;
    private ProgressBar loadingIndicator;
    

    // Data
    private String emergencyId;
    private String rescuerId;
    private String rescuerPhone;
    private double seniorLat, seniorLong;
    private double rescuerLat, rescuerLong;
    
    // Hospital data from intent
    private String hospitalId;
    private String hospitalName;
    private String hospitalAddress;
    private String hospitalPhone;
    private double hospitalLat, hospitalLng;

    // Services
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;
    private ExecutorService executorService;

    // Update handler
    private Handler updateHandler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescuer_details);

        // Stop the emergency alert sound when RescuerDetailsActivity is opened
        EmergencySOSBackgroundService.stopEmergencySound();
        Log.d(TAG, "🔇 Emergency alert sound stopped when RescuerDetailsActivity opened");

        // Initialize services
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newSingleThreadExecutor();
        updateHandler = new Handler(Looper.getMainLooper());

        // Get emergency ID from intent
        emergencyId = getIntent().getStringExtra("emergencyId");
        if (emergencyId == null) {
            Log.e(TAG, "No emergency ID provided");
            Toast.makeText(this, "Error: No emergency ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Get additional data from notification (if available)
        String rescuerName = getIntent().getStringExtra("rescuerName");
        String rescuerPhone = getIntent().getStringExtra("rescuerPhone");
        String rescuerTeam = getIntent().getStringExtra("rescuerTeam");
        String assignedRescuerId = getIntent().getStringExtra("assignedRescuerId");
        String emergencyStatus = getIntent().getStringExtra("emergencyStatus");
        
        // Get hospital data from intent (if available)
        hospitalId = getIntent().getStringExtra("hospitalId");
        hospitalName = getIntent().getStringExtra("hospitalName");
        hospitalAddress = getIntent().getStringExtra("hospitalAddress");
        hospitalPhone = getIntent().getStringExtra("hospitalPhone");
        hospitalLat = getIntent().getDoubleExtra("hospitalLat", 0.0);
        hospitalLng = getIntent().getDoubleExtra("hospitalLng", 0.0);
        
        // Get senior location from intent
        double intentSeniorLat = getIntent().getDoubleExtra("seniorLat", 0.0);
        double intentSeniorLong = getIntent().getDoubleExtra("seniorLong", 0.0);
        
        Log.d(TAG, "📱 Received data from notification:");
        Log.d(TAG, "   Rescuer Name: " + rescuerName);
        Log.d(TAG, "   Rescuer Phone: " + rescuerPhone);
        Log.d(TAG, "   Rescuer Team: " + rescuerTeam);
        Log.d(TAG, "   Assigned Rescuer ID: " + assignedRescuerId);
        Log.d(TAG, "   Emergency Status: " + emergencyStatus);
        Log.d(TAG, "   Senior Location from Intent: " + intentSeniorLat + ", " + intentSeniorLong);
        Log.d(TAG, "   Hospital ID: " + hospitalId);
        Log.d(TAG, "   Hospital Name: " + hospitalName);
        Log.d(TAG, "   Hospital Address: " + hospitalAddress);
        Log.d(TAG, "   Hospital Phone: " + hospitalPhone);
        Log.d(TAG, "   Hospital Location: " + hospitalLat + ", " + hospitalLng);
        
        // If we have rescuer data from notification, use it directly
        if (rescuerName != null && rescuerPhone != null) {
            updateRescuerInfoFromNotification(rescuerName, rescuerPhone, rescuerTeam, assignedRescuerId, emergencyStatus);
        }
        
        // If we have hospital data from intent, use it directly
        if (hospitalName != null && !hospitalName.isEmpty()) {
            updateHospitalInfoFromIntent();
        }

        initializeViews();
        
        // Test UI update immediately
        testUIUpdate();
        
        loadEmergencyDetails();
        setupUpdateRunnable();
        
        // Try to get current location for more accurate ETA
        getCurrentLocationForETA();
        
        // Force initial ETA calculation after a short delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "🔄 Force initial ETA calculation after delay");
            if (rescuerLat != 0 && rescuerLong != 0 && seniorLat != 0 && seniorLong != 0) {
                calculateETA();
            } else {
                Log.w(TAG, "⚠️ Missing location data - Rescuer: " + rescuerLat + ", " + rescuerLong + " Senior: " + seniorLat + ", " + seniorLong);
                // Try to calculate with whatever data we have
                calculateETAFromDatabase();
            }
        }, 2000); // 2 second delay
    }
    
    private void updateRescuerInfoFromNotification(String rescuerName, String rescuerPhone, 
                                                 String rescuerTeam, String assignedRescuerId, 
                                                 String emergencyStatus) {
        Log.d(TAG, "📱 Updating rescuer info from notification data");
        
        // Update UI with notification data
        runOnUiThread(() -> {
            if (tvRescuerName != null) {
                tvRescuerName.setText("Name: " + rescuerName);
            }
            if (tvRescuerPhone != null) {
                tvRescuerPhone.setText("Phone: " + rescuerPhone);
            }
            if (tvRescuerTeam != null) {
                tvRescuerTeam.setText("Team: " + (rescuerTeam != null ? rescuerTeam : "Emergency Response Team"));
            }
            if (tvStatus != null) {
                tvStatus.setText(emergencyStatus != null ? emergencyStatus : "Assigned");
            }
            
            // Update last updated time
            if (tvLastUpdate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                tvLastUpdate.setText("Last updated: " + sdf.format(new Date()));
            }
        });
        
        // Store the rescuer ID for future use
        if (assignedRescuerId != null && !assignedRescuerId.isEmpty()) {
            rescuerId = assignedRescuerId;
        }
        
        // Store phone number
        this.rescuerPhone = rescuerPhone;

        
        Log.d(TAG, "✅ Rescuer info updated from notification");
    }
    
    private void updateHospitalInfoFromIntent() {
        Log.d(TAG, "🏥 Updating hospital info from intent data");
        
        // Update UI with hospital data from intent
        runOnUiThread(() -> {
            // Note: The RescuerDetailsActivity doesn't have hospital UI elements
            // This method is here for future expansion or if hospital info needs to be displayed
            Log.d(TAG, "🏥 Hospital data from intent:");
            Log.d(TAG, "   Name: " + hospitalName);
            Log.d(TAG, "   Address: " + hospitalAddress);
            Log.d(TAG, "   Phone: " + hospitalPhone);
            Log.d(TAG, "   Location: " + hospitalLat + ", " + hospitalLng);
        });
        
        Log.d(TAG, "✅ Hospital info updated from intent");
    }

    private void initializeViews() {
        tvRescuerName = findViewById(R.id.tvRescuerName);
        tvRescuerTeam = findViewById(R.id.tvRescuerTeam);
        tvRescuerPhone = findViewById(R.id.tvRescuerPhone);
        tvETA = findViewById(R.id.tvETA);
        tvDistance = findViewById(R.id.tvDistance);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        tvStatus = findViewById(R.id.tvStatus);
        tvETAStatus = findViewById(R.id.tvETAStatus);
        btnCallRescuer = findViewById(R.id.btnCallRescuer);
        btnBack = findViewById(R.id.btnBack);
        btnRefreshETA = findViewById(R.id.btnRefreshETA);
        btnTestETA = findViewById(R.id.btnTestETA);
        btnCheckDatabase = findViewById(R.id.btnCheckDatabase);
        btnForceETA = findViewById(R.id.btnForceETA);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        
        
        // Debug UI element initialization
        Log.d(TAG, "🔍 UI Elements initialized:");
        Log.d(TAG, "   tvETA: " + (tvETA != null ? "✅ Found" : "❌ NULL"));
        Log.d(TAG, "   tvDistance: " + (tvDistance != null ? "✅ Found" : "❌ NULL"));
        Log.d(TAG, "   tvLastUpdate: " + (tvLastUpdate != null ? "✅ Found" : "❌ NULL"));
        Log.d(TAG, "   tvETAStatus: " + (tvETAStatus != null ? "✅ Found" : "❌ NULL"));

        // Set up click listeners
        btnBack.setOnClickListener(v -> finish());
        btnCallRescuer.setOnClickListener(v -> callRescuer());
        btnRefreshETA.setOnClickListener(v -> {
            Log.d(TAG, "🔄 Refresh ETA button clicked");
            getCurrentLocationForETA();
        });
        
        // Test buttons
        btnTestETA.setOnClickListener(v -> {
            Log.d(TAG, "🧪 Testing ETA calculation with sample data");
            testETACalculation();
        });
        
        btnCheckDatabase.setOnClickListener(v -> {
            Log.d(TAG, "🔍 Checking database for rescuer location");
            checkRescuerLocationInDatabase();
        });
        
        btnForceETA.setOnClickListener(v -> {
            Log.d(TAG, "🔄 Force ETA calculation with current data");
            Log.d(TAG, "Current rescuer location: " + rescuerLat + ", " + rescuerLong);
            Log.d(TAG, "Current senior location: " + seniorLat + ", " + seniorLong);
            
            // Force show some test data first
            runOnUiThread(() -> {
                tvETA.setText("15 min");
                tvDistance.setText("2.5 km");
                tvETAStatus.setText("Test data");
                tvLastUpdate.setText("Last updated: " + getCurrentTime() + " (Test)");
            });
            
            // Then try real calculation
            if (rescuerLat != 0 && rescuerLong != 0 && seniorLat != 0 && seniorLong != 0) {
                calculateETA();
            } else {
                calculateETAFromDatabase();
            }
        });
        
    }

    private void loadEmergencyDetails() {
        showLoading(true);
        
        // Load emergency details to get rescuer ID
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .document(emergencyId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Use correct field name for rescuer ID
                        rescuerId = documentSnapshot.getString("assignedRescuerId");
                        
                        // Check if we already have rescuer info from notification
                        if (rescuerId == null || rescuerId.isEmpty()) {
                            Log.d(TAG, "⚠️ No assigned rescuer ID found in emergency document");
                        } else {
                            Log.d(TAG, "✅ Found assigned rescuer ID: " + rescuerId);
                        }
                        
                        // Get senior location from intent first, then fallback to emergency document
                        double intentSeniorLat = getIntent().getDoubleExtra("seniorLat", 0.0);
                        double intentSeniorLong = getIntent().getDoubleExtra("seniorLong", 0.0);
                        
                        if (intentSeniorLat != 0.0 && intentSeniorLong != 0.0) {
                            seniorLat = intentSeniorLat;
                            seniorLong = intentSeniorLong;
                            Log.d(TAG, "📍 Using senior location from intent: " + seniorLat + ", " + seniorLong);
                        } else {
                            // Fallback to emergency document
                            GeoPoint seniorLocation = documentSnapshot.getGeoPoint("location");
                            Log.d(TAG, "🔍 Emergency document data: " + documentSnapshot.getData().toString());
                            Log.d(TAG, "🔍 Looking for 'location' field in emergency document");
                            
                            if (seniorLocation != null) {
                                seniorLat = seniorLocation.getLatitude();
                                seniorLong = seniorLocation.getLongitude();
                                Log.d(TAG, "📍 Senior location found in emergency document: " + seniorLat + ", " + seniorLong);
                            } else {
                                Log.w(TAG, "⚠️ No senior location found in emergency document");
                                Log.w(TAG, "⚠️ Available fields in emergency document: " + documentSnapshot.getData().keySet().toString());
                            }
                        }
                        
                        if (rescuerId != null) {
                            loadRescuerDetails();
                        } else {
                            showError("No rescuer assigned to this emergency");
                        }
                    } else {
                        showError("Emergency not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading emergency details", e);
                    showError("Failed to load emergency details");
                });
    }

    private void loadRescuerDetails() {
        if (rescuerId == null) {
            Log.w(TAG, "⚠️ No rescuer ID available for loading details");
            return;
        }

        Log.d(TAG, "🔍 Loading rescuer details for ID: " + rescuerId);
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("rescuegroup");
                        String team = documentSnapshot.getString("rescuegroup");
                        String phone = documentSnapshot.getString("mobileNumber");
                        
                        Log.d(TAG, "📋 Rescuer data from database:");
                        Log.d(TAG, "   Name: " + name);
                        Log.d(TAG, "   Team: " + team);
                        Log.d(TAG, "   Phone: " + phone);
                        
                        if (name == null || name.isEmpty()) {
                            name = "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length()));
                        }
                        if (team == null || team.isEmpty()) {
                            team = "Emergency Response Team";
                        }
                        if (phone == null || phone.isEmpty()) {
                            phone = "Not available";
                        }
                        
                        rescuerPhone = phone;
                        
                        // Update UI
                        tvRescuerName.setText(name);
                        tvRescuerTeam.setText(team);
                        tvRescuerPhone.setText(phone);
                        
                        
                        // Get rescuer location from database
                        GeoPoint rescuerLocation = documentSnapshot.getGeoPoint("currentLocation");
                        if (rescuerLocation != null) {
                            rescuerLat = rescuerLocation.getLatitude();
                            rescuerLong = rescuerLocation.getLongitude();
                            Log.d(TAG, "📍 Rescuer location found: " + rescuerLat + ", " + rescuerLong);
                        } else {
                            Log.w(TAG, "⚠️ No rescuer location found in database");
                            // Try alternative field names
                            rescuerLocation = documentSnapshot.getGeoPoint("location");
                            if (rescuerLocation != null) {
                                rescuerLat = rescuerLocation.getLatitude();
                                rescuerLong = rescuerLocation.getLongitude();
                                Log.d(TAG, "📍 Rescuer location found in 'location' field: " + rescuerLat + ", " + rescuerLong);
                            } else {
                                Log.w(TAG, "⚠️ No rescuer location found in any field");
                            }
                        }
                        
                        // Try Google Maps API first, then fallback to database calculation
                        Log.d(TAG, "🔄 Calculating ETA - Rescuer: " + rescuerLat + ", " + rescuerLong + " Senior: " + seniorLat + ", " + seniorLong);
                        calculateETA();
                    } else {
                        showError("Rescuer details not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading rescuer details", e);
                    showError("Failed to load rescuer details");
                });
    }



    private void calculateETAFromDatabase() {
        Log.d(TAG, "🔄 calculateETAFromDatabase() called with rescuer: " + rescuerLat + ", " + rescuerLong + " and senior: " + seniorLat + ", " + seniorLong);
        
        if (rescuerLat == 0 || rescuerLong == 0 || seniorLat == 0 || seniorLong == 0) {
            Log.w(TAG, "❌ Missing location data for ETA calculation - Rescuer: " + rescuerLat + ", " + rescuerLong + " Senior: " + seniorLat + ", " + seniorLong);
            runOnUiThread(() -> {
                if (tvETA != null) {
                    tvETA.setText("-- min");
                    Log.d(TAG, "✅ Set ETA to -- min");
                } else {
                    Log.e(TAG, "❌ tvETA is null in calculateETAFromDatabase!");
                }
                if (tvDistance != null) {
                    tvDistance.setText("-- km");
                    Log.d(TAG, "✅ Set distance to -- km");
                } else {
                    Log.e(TAG, "❌ tvDistance is null in calculateETAFromDatabase!");
                }
                if (tvLastUpdate != null) {
                    tvLastUpdate.setText("Last updated: " + getCurrentTime() + " (No location data)");
                    Log.d(TAG, "✅ Set last update text");
                } else {
                    Log.e(TAG, "❌ tvLastUpdate is null in calculateETAFromDatabase!");
                }
                if (tvETAStatus != null) {
                    tvETAStatus.setText("No location data available");
                    Log.d(TAG, "✅ Set ETA status text");
                } else {
                    Log.e(TAG, "❌ tvETAStatus is null in calculateETAFromDatabase!");
                }
            });
            return;
        }

        // Calculate straight-line distance
        double distance = calculateDistance(rescuerLat, rescuerLong, seniorLat, seniorLong);
        
        // Estimate travel time based on distance
        // Assuming average speed of 30 km/h in urban areas
        double estimatedTimeMinutes = (distance / 30.0) * 60;
        
        Log.d(TAG, "📍 Database ETA calculation - Distance: " + String.format("%.1f km", distance) + 
              ", Estimated time: " + String.format("%.0f min", estimatedTimeMinutes));
        
        runOnUiThread(() -> {
            Log.d(TAG, "🔄 Updating UI with ETA: " + String.format("%.0f min", estimatedTimeMinutes) + ", Distance: " + String.format("%.1f km", distance));
            
            if (tvETA != null) {
                tvETA.setText(String.format("%.0f min", estimatedTimeMinutes));
                Log.d(TAG, "✅ tvETA updated to: " + String.format("%.0f min", estimatedTimeMinutes));
            } else {
                Log.e(TAG, "❌ tvETA is null!");
            }
            
            if (tvDistance != null) {
                tvDistance.setText(String.format("%.1f km", distance));
                Log.d(TAG, "✅ tvDistance updated to: " + String.format("%.1f km", distance));
            } else {
                Log.e(TAG, "❌ tvDistance is null!");
            }
            
            if (tvLastUpdate != null) {
                tvLastUpdate.setText("Last updated: " + getCurrentTime());
                Log.d(TAG, "✅ tvLastUpdate updated");
            } else {
                Log.e(TAG, "❌ tvLastUpdate is null!");
            }
            
            if (tvETAStatus != null) {
                tvETAStatus.setText("Based on database locations");
                Log.d(TAG, "✅ tvETAStatus updated");
            } else {
                Log.e(TAG, "❌ tvETAStatus is null!");
            }
        });
    }

    private void calculateETA() {
        Log.d(TAG, "🔄 calculateETA() called with rescuer: " + rescuerLat + ", " + rescuerLong + " and senior: " + seniorLat + ", " + seniorLong);
        
        if (rescuerLat == 0 || rescuerLong == 0 || seniorLat == 0 || seniorLong == 0) {
            Log.w(TAG, "❌ Missing location data for ETA calculation - Rescuer: " + rescuerLat + ", " + rescuerLong + " Senior: " + seniorLat + ", " + seniorLong);
            runOnUiThread(() -> {
                tvETA.setText("-- min");
                tvDistance.setText("-- km");
                tvLastUpdate.setText("Last updated: " + getCurrentTime() + " (No location data)");
                if (tvETAStatus != null) {
                    tvETAStatus.setText("No location data available");
                }
            });
            return;
        }

        // Show loading state
        runOnUiThread(() -> {
            tvETA.setText("Calculating...");
            tvDistance.setText("Calculating...");
            if (tvETAStatus != null) {
                tvETAStatus.setText("Getting real-time traffic data...");
            }
        });

        // Check if executor service is still available
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(() -> {
                try {
                    String url = buildDirectionsUrl(rescuerLat, rescuerLong, seniorLat, seniorLong);
                    Log.d(TAG, "🌐 Making Google Directions API request: " + url);
                    String response = makeDirectionsRequest(url);
                    Log.d(TAG, "🌐 Received response length: " + response.length());
                    parseDirectionsResponse(response);
                } catch (Exception e) {
                    Log.e(TAG, "Error calculating ETA with Google Directions API, using fallback calculation", e);
                    // Fallback to straight-line distance calculation
                    calculateFallbackETA();
                }
            });
        } else {
            Log.w(TAG, "⚠️ Executor service is null or shutdown, using fallback calculation");
            calculateFallbackETA();
        }
    }

    private String buildDirectionsUrl(double originLat, double originLng, double destLat, double destLng) {
        return DIRECTIONS_API_URL + "?" +
                "origin=" + originLat + "," + originLng +
                "&destination=" + destLat + "," + destLng +
                "&key=" + GOOGLE_MAPS_API_KEY +
                "&mode=driving" +
                "&traffic_model=best_guess" +
                "&departure_time=now" +
                "&units=metric" +
                "&avoid=tolls";
    }

    private String makeDirectionsRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        connection.disconnect();
        return response.toString();
    }

    private void parseDirectionsResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            
            // Check for API errors
            String status = jsonResponse.getString("status");
            Log.d(TAG, "🌐 Google Directions API status: " + status);
            
            if (!"OK".equals(status)) {
                String errorMessage = jsonResponse.optString("error_message", "Unknown error");
                Log.e(TAG, "❌ Google Directions API error: " + status + " - " + errorMessage);
                runOnUiThread(() -> {
                    tvETA.setText("Error");
                    tvDistance.setText("Error");
                    tvLastUpdate.setText("Last updated: " + getCurrentTime() + " (API Error)");
                    if (tvETAStatus != null) {
                        tvETAStatus.setText("API Error: " + status);
                    }
                });
                return;
            }
            
            JSONArray routes = jsonResponse.getJSONArray("routes");

            if (routes.length() > 0) {
                JSONObject route = routes.getJSONObject(0);
                JSONArray legs = route.getJSONArray("legs");
                
                if (legs.length() > 0) {
                    JSONObject leg = legs.getJSONObject(0);
                    JSONObject duration = leg.getJSONObject("duration");
                    JSONObject distance = leg.getJSONObject("distance");
                    
                    int durationValue = duration.getInt("value");
                    int distanceValue = distance.getInt("value");
                    
                       // Convert to minutes and kilometers
                       int etaMinutes = durationValue / 60;
                       double distanceKm = distanceValue / 1000.0;

                       // Handle very close distances (less than 10 meters)
                       if (distanceKm < 0.01) {
                           etaMinutes = 1; // Minimum 1 minute
                           distanceKm = 0.01; // Show as 0.01 km (10 meters)
                           Log.d(TAG, "📍 Very close distance detected, adjusting to minimum values");
                       }

                       // Make variables final for lambda
                       final int finalEtaMinutes = etaMinutes;
                       final double finalDistanceKm = distanceKm;

                       Log.d(TAG, "✅ Google Directions API - ETA: " + finalEtaMinutes + " min, Distance: " + String.format("%.2f km", finalDistanceKm));

                       runOnUiThread(() -> {
                           tvETA.setText(finalEtaMinutes + " min");
                           tvDistance.setText(String.format("%.2f km", finalDistanceKm));
                           tvLastUpdate.setText("Last updated: " + getCurrentTime());
                           if (tvETAStatus != null) {
                               if (finalDistanceKm < 0.01) {
                                   tvETAStatus.setText("Very close - real-time data");
                               } else {
                                   tvETAStatus.setText("Real-time traffic data");
                               }
                           }
                           
                       });
                } else {
                    Log.w(TAG, "⚠️ No legs found in route");
                    calculateFallbackETA();
                }
            } else {
                Log.w(TAG, "⚠️ No routes found in response");
                calculateFallbackETA();
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing directions response", e);
            runOnUiThread(() -> {
                tvETA.setText("Error");
                tvDistance.setText("Error");
                tvLastUpdate.setText("Last updated: " + getCurrentTime() + " (Parse Error)");
                if (tvETAStatus != null) {
                    tvETAStatus.setText("Parse Error");
                }
            });
        }
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void calculateFallbackETA() {
        Log.d(TAG, "🔄 Using fallback ETA calculation (straight-line distance)");
        
        // Calculate straight-line distance
        double distance = calculateDistance(rescuerLat, rescuerLong, seniorLat, seniorLong);
        
        // Handle very close distances (less than 10 meters)
        if (distance < 0.01) {
            distance = 0.01; // Show as 0.01 km (10 meters)
        }
        
        // Estimate travel time based on distance with different speeds for different distances
        final double estimatedTimeMinutes;
        if (distance < 0.01) {
            // Extremely close - minimum 1 minute
            estimatedTimeMinutes = 1.0;
        } else if (distance < 1.0) {
            // Very close - walking speed (5 km/h)
            estimatedTimeMinutes = (distance / 5.0) * 60 * 1.3; // 30% buffer
        } else if (distance < 5.0) {
            // Local area - slower driving (25 km/h)
            estimatedTimeMinutes = (distance / 25.0) * 60 * 1.3; // 30% buffer
        } else if (distance < 20.0) {
            // Urban area - moderate speed (35 km/h)
            estimatedTimeMinutes = (distance / 35.0) * 60 * 1.3; // 30% buffer
        } else {
            // Longer distance - highway speed (50 km/h)
            estimatedTimeMinutes = (distance / 50.0) * 60 * 1.3; // 30% buffer
        }
        
        // Make variables final for lambda
        final double finalDistance = distance;
        
        runOnUiThread(() -> {
            tvETA.setText(String.format("%.0f min", estimatedTimeMinutes) + " (est.)");
            tvDistance.setText(String.format("%.2f km", finalDistance));
            tvLastUpdate.setText("Last updated: " + getCurrentTime() + " (Estimated)");
            if (tvETAStatus != null) {
                tvETAStatus.setText("Estimated based on distance");
            }
        });
        
        Log.d(TAG, "📍 Fallback ETA: " + String.format("%.0f min", estimatedTimeMinutes) + 
              " for " + String.format("%.1f km", distance) + " distance");
    }
    
    private void testETACalculation() {
        Log.d(TAG, "🧪 Testing ETA calculation with sample data");
        
        // Use sample coordinates (Manila area)
        double testRescuerLat = 14.5995; // Manila
        double testRescuerLong = 120.9842;
        double testSeniorLat = 14.6042; // Nearby location
        double testSeniorLong = 120.9822;
        
        // Temporarily store original values
        double originalRescuerLat = rescuerLat;
        double originalRescuerLong = rescuerLong;
        double originalSeniorLat = seniorLat;
        double originalSeniorLong = seniorLong;
        
        // Set test values
        rescuerLat = testRescuerLat;
        rescuerLong = testRescuerLong;
        seniorLat = testSeniorLat;
        seniorLong = testSeniorLong;
        
        Log.d(TAG, "🧪 Test coordinates - Rescuer: " + rescuerLat + ", " + rescuerLong + " Senior: " + seniorLat + ", " + seniorLong);
        
        // Calculate ETA with test data using database method
        calculateETAFromDatabase();
        
        // Restore original values after a delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            rescuerLat = originalRescuerLat;
            rescuerLong = originalRescuerLong;
            seniorLat = originalSeniorLat;
            seniorLong = originalSeniorLong;
            Log.d(TAG, "🧪 Restored original coordinates");
        }, 5000);
    }
    
    
    private void testUIUpdate() {
        Log.d(TAG, "🧪 Testing UI update with sample data");
        
        runOnUiThread(() -> {
            if (tvETA != null) {
                tvETA.setText("TEST ETA");
                Log.d(TAG, "✅ Test ETA text set");
            } else {
                Log.e(TAG, "❌ tvETA is null in test!");
            }
            
            if (tvDistance != null) {
                tvDistance.setText("TEST DIST");
                Log.d(TAG, "✅ Test distance text set");
            } else {
                Log.e(TAG, "❌ tvDistance is null in test!");
            }
            
            if (tvETAStatus != null) {
                tvETAStatus.setText("Testing UI");
                Log.d(TAG, "✅ Test status text set");
            } else {
                Log.e(TAG, "❌ tvETAStatus is null in test!");
            }
            
            if (tvLastUpdate != null) {
                tvLastUpdate.setText("Last updated: " + getCurrentTime() + " (Test)");
                Log.d(TAG, "✅ Test last update text set");
            } else {
                Log.e(TAG, "❌ tvLastUpdate is null in test!");
            }
        });
    }
    
    private void checkRescuerLocationInDatabase() {
        if (rescuerId == null) {
            Log.w(TAG, "❌ No rescuer ID available for database check");
            return;
        }
        
        Log.d(TAG, "🔍 Checking rescuer location in database for ID: " + rescuerId);
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "✅ Rescuer document exists in database");
                        
                        // Check currentLocation field
                        com.google.firebase.firestore.GeoPoint currentLocation = documentSnapshot.getGeoPoint("currentLocation");
                        if (currentLocation != null) {
                            Log.d(TAG, "✅ currentLocation found: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
                        } else {
                            Log.w(TAG, "❌ currentLocation field not found");
                        }
                        
                        // Check location field
                        com.google.firebase.firestore.GeoPoint location = documentSnapshot.getGeoPoint("location");
                        if (location != null) {
                            Log.d(TAG, "✅ location field found: " + location.getLatitude() + ", " + location.getLongitude());
                        } else {
                            Log.w(TAG, "❌ location field not found");
                        }
                        
                        // Check lastLocationUpdate
                        Long lastUpdate = documentSnapshot.getLong("lastLocationUpdate");
                        if (lastUpdate != null) {
                            Log.d(TAG, "✅ lastLocationUpdate: " + new java.util.Date(lastUpdate));
                        } else {
                            Log.w(TAG, "❌ lastLocationUpdate not found");
                        }
                        
                        // Show all fields for debugging
                        Log.d(TAG, "📋 All document fields: " + documentSnapshot.getData());
                        
                    } else {
                        Log.w(TAG, "❌ Rescuer document not found in database");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error checking rescuer location in database: " + e.getMessage());
                });
    }

    private void setupUpdateRunnable() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // Update rescuer location from database first, then calculate ETA
                updateRescuerLocationFromDatabase();
                updateHandler.postDelayed(this, 30000);
            }
        };
        updateHandler.postDelayed(updateRunnable, 30000);
    }
    
    private void updateRescuerLocationFromDatabase() {
        if (rescuerId == null) {
            Log.w(TAG, "⚠️ No rescuer ID available for location update");
            return;
        }
        
        Log.d(TAG, "🔄 Updating rescuer location from database for ID: " + rescuerId);
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get rescuer location from database
                        GeoPoint rescuerLocation = documentSnapshot.getGeoPoint("currentLocation");
                        if (rescuerLocation != null) {
                            double newRescuerLat = rescuerLocation.getLatitude();
                            double newRescuerLong = rescuerLocation.getLongitude();
                            
                            // Check if location has changed significantly
                            double distanceChange = calculateDistance(rescuerLat, rescuerLong, newRescuerLat, newRescuerLong);
                            
                            if (distanceChange > 0.01) { // More than 10 meters change
                                rescuerLat = newRescuerLat;
                                rescuerLong = newRescuerLong;
                                Log.d(TAG, "📍 Rescuer location updated: " + rescuerLat + ", " + rescuerLong);
                                
                                // Calculate ETA with updated location using Google Maps API
                                calculateETA();
                            } else {
                                Log.d(TAG, "📍 Rescuer location unchanged, skipping ETA update");
                            }
                        } else {
                            Log.w(TAG, "⚠️ No rescuer location found in database");
                            // Still try to calculate ETA with existing location
                            calculateETA();
                        }
                    } else {
                        Log.w(TAG, "❌ Rescuer document not found in database");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error updating rescuer location from database: " + e.getMessage());
                    // Still try to calculate ETA with existing location
                    calculateETA();
                });
    }

    private void getCurrentLocationForETA() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "⚠️ No location permission for current location");
            return;
        }
        
        Log.d(TAG, "📍 Getting current location for ETA calculation");
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        // Update senior location with current GPS location
                        seniorLat = location.getLatitude();
                        seniorLong = location.getLongitude();
                        Log.d(TAG, "📍 Updated senior location from GPS: " + seniorLat + ", " + seniorLong);
                        
                        // Recalculate ETA with updated senior location
                        calculateETA();
                    } else {
                        Log.w(TAG, "⚠️ No current location available from GPS");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting current location: " + e.getMessage());
                });
    }
    
    private void callRescuer() {
        if (rescuerPhone != null && !rescuerPhone.equals("Not available")) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + rescuerPhone));
                startActivity(callIntent);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 1);
            }
        } else {
            Toast.makeText(this, "Rescuer phone number not available", Toast.LENGTH_SHORT).show();
        }
    }


    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? ProgressBar.VISIBLE : ProgressBar.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    private String getCurrentTime() {
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }
    
    // Navigation method
    private void goBackToDashboard() {
        Log.d(TAG, "🏠 Navigating back to dashboard with rescuer info");
        Intent dashboardIntent = new Intent(this, Senior_Dashboard.class);
        dashboardIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        // Pass rescuer information to show floating panel
        if (rescuerId != null) {
            dashboardIntent.putExtra("showFloatingPanel", true);
            dashboardIntent.putExtra("rescuerId", rescuerId);
            dashboardIntent.putExtra("rescuerName", tvRescuerName != null ? tvRescuerName.getText().toString() : "Rescuer");
            dashboardIntent.putExtra("rescuerPhone", rescuerPhone);
            dashboardIntent.putExtra("eta", tvETA != null ? tvETA.getText().toString() : "-- min");
            dashboardIntent.putExtra("distance", tvDistance != null ? tvDistance.getText().toString() : "-- km");
            dashboardIntent.putExtra("rescuerLat", rescuerLat);
            dashboardIntent.putExtra("rescuerLong", rescuerLong);
            dashboardIntent.putExtra("seniorLat", seniorLat);
            dashboardIntent.putExtra("seniorLong", seniorLong);
            
            Log.d(TAG, "📤 Passing rescuer data to dashboard:");
            Log.d(TAG, "   showFloatingPanel: true");
            Log.d(TAG, "   Rescuer ID: " + rescuerId);
            Log.d(TAG, "   Rescuer Name: " + (tvRescuerName != null ? tvRescuerName.getText().toString() : "Rescuer"));
            Log.d(TAG, "   ETA: " + (tvETA != null ? tvETA.getText().toString() : "-- min"));
            Log.d(TAG, "   Distance: " + (tvDistance != null ? tvDistance.getText().toString() : "-- km"));
            Log.d(TAG, "   Rescuer Location: " + rescuerLat + ", " + rescuerLong);
            Log.d(TAG, "   Senior Location: " + seniorLat + ", " + seniorLong);
        } else {
            Log.w(TAG, "⚠️ No rescuer ID available, not passing data to dashboard");
        }
        
        startActivity(dashboardIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up handler
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        
        // Clean up executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                // Wait for existing tasks to complete
                if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        Log.d(TAG, "🧹 RescuerDetailsActivity destroyed and cleaned up");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ RescuerDetailsActivity paused");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "▶️ RescuerDetailsActivity resumed");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 1) {
                callRescuer();
            }
        } else {
            Toast.makeText(this, "Call permission denied", Toast.LENGTH_SHORT).show();
        }
    }
    
}
