package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmergencyAssignmentActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "EmergencyAssignmentActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    // UI Components
    private TextView tvSeniorName, tvSeniorPhone, tvLocation, tvRescuerName, tvRescuerLocation;
    private TextView tvAssignmentTime, tvEstimatedArrival, tvDistance, tvStatus;
    private TextView tvHospitalName, tvHospitalAddress, tvHospitalDistance;
    private Button btnCallSenior, btnNavigate, btnUpdateLocation, btnMarkArrived, btnNavigateHospital;
    private GoogleMap mMap;
    
    // Data
    private String seniorName, seniorPhone, locationAddress, rescuerId;
    private double seniorLat, seniorLng, rescuerLat, rescuerLng;
    private double hospitalLat, hospitalLng;
    private String hospitalName, hospitalAddress;
    private long assignmentTime;
    private String emergencyId;
    
    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_assignment);
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Get data from intent
        getIntentData();
        
        // Initialize UI
        initializeViews();
        
        // Setup map
        setupMap();
        
        // Load rescuer information
        loadRescuerInfo();
        
        // Load nearest hospital information
        loadNearestHospital();
        
        // Get current location and calculate arrival time
        getCurrentLocationAndCalculateArrival();
        
        // Update location every 30 seconds
        startLocationUpdates();
    }
    
    private void getIntentData() {
        Intent intent = getIntent();
        seniorName = intent.getStringExtra("senior_name");
        seniorPhone = intent.getStringExtra("senior_phone");
        locationAddress = intent.getStringExtra("location_address");
        seniorLat = intent.getDoubleExtra("senior_lat", 0.0);
        seniorLng = intent.getDoubleExtra("senior_lng", 0.0);
        assignmentTime = intent.getLongExtra("assignment_time", System.currentTimeMillis());
        emergencyId = intent.getStringExtra("emergency_id");
        rescuerId = mAuth.getCurrentUser().getUid();
        
        Log.d(TAG, "Emergency assignment data: " + seniorName + " at " + locationAddress);
        Log.d(TAG, "Senior phone number: " + seniorPhone);
        Log.d(TAG, "Senior coordinates: " + seniorLat + ", " + seniorLng);
        
        // Debug: Check all intent extras
        Bundle extras = intent.getExtras();
        if (extras != null) {
            Log.d(TAG, "All intent extras:");
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, "  " + key + " = " + value);
            }
        } else {
            Log.w(TAG, "No intent extras found!");
        }
    }
    
    private void initializeViews() {
        tvSeniorName = findViewById(R.id.tv_senior_name);
        tvSeniorPhone = findViewById(R.id.tv_senior_phone);
        tvLocation = findViewById(R.id.tv_location);
        tvRescuerName = findViewById(R.id.tv_rescuer_name);
        tvRescuerLocation = findViewById(R.id.tv_rescuer_location);
        tvAssignmentTime = findViewById(R.id.tv_assignment_time);
        tvEstimatedArrival = findViewById(R.id.tv_estimated_arrival);
        tvDistance = findViewById(R.id.tv_distance);
        tvStatus = findViewById(R.id.tv_status);
        
        // Hospital UI components
        tvHospitalName = findViewById(R.id.tv_hospital_name);
        tvHospitalAddress = findViewById(R.id.tv_hospital_address);
        tvHospitalDistance = findViewById(R.id.tv_hospital_distance);
        
        // Debug: Check if TextView is found
        if (tvSeniorPhone == null) {
            Log.e(TAG, "❌ tvSeniorPhone TextView not found!");
        } else {
            Log.d(TAG, "✅ tvSeniorPhone TextView found successfully");
        }
        
        btnCallSenior = findViewById(R.id.btn_call_senior);
        btnNavigate = findViewById(R.id.btn_navigate);
        btnUpdateLocation = findViewById(R.id.btn_update_location);
        btnMarkArrived = findViewById(R.id.btn_mark_arrived);
        btnNavigateHospital = findViewById(R.id.btn_navigate_hospital);
        
        // Set initial data
        tvSeniorName.setText(seniorName != null ? seniorName : "Unknown Senior");
        
        // Format and display phone number
        String phoneToDisplay = seniorPhone != null && !seniorPhone.isEmpty() ? 
            PhoneNumberUtils.formatPhoneNumber(seniorPhone) : "Phone not available";
        Log.d(TAG, "Setting phone display to: " + phoneToDisplay);
        tvSeniorPhone.setText(phoneToDisplay);
        
        tvLocation.setText(locationAddress != null ? locationAddress : "Location not available");
        
        String timeStr = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(assignmentTime));
        tvAssignmentTime.setText(timeStr);
        tvStatus.setText("🚨 RESPONDING");
        
        // Setup button listeners
        setupButtonListeners();
    }
    
    private void setupButtonListeners() {
        btnCallSenior.setOnClickListener(v -> callSenior());
        btnNavigate.setOnClickListener(v -> openNavigation());
        btnUpdateLocation.setOnClickListener(v -> updateLocation());
        btnMarkArrived.setOnClickListener(v -> markArrived());
        btnNavigateHospital.setOnClickListener(v -> navigateToHospital());
    }
    
    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }
    
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        
        // Add senior location marker
        if (seniorLat != 0.0 && seniorLng != 0.0) {
            LatLng seniorLocation = new LatLng(seniorLat, seniorLng);
            mMap.addMarker(new MarkerOptions()
                    .position(seniorLocation)
                    .title("Senior: " + seniorName)
                    .snippet(locationAddress));
            
            // Move camera to senior location
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(seniorLocation, 15f));
        }
        
        // Add rescuer location marker when available
        if (rescuerLat != 0.0 && rescuerLng != 0.0) {
            LatLng rescuerLocation = new LatLng(rescuerLat, rescuerLng);
            mMap.addMarker(new MarkerOptions()
                    .position(rescuerLocation)
                    .title("Your Location")
                    .snippet("Rescuer Location"));
            
            // Draw route between rescuer and senior
            drawRoute(rescuerLocation, new LatLng(seniorLat, seniorLng));
        }
        
        // Add hospital marker if available
        if (hospitalLat != 0.0 && hospitalLng != 0.0) {
            addHospitalMarker();
        }
    }
    
    private void drawRoute(LatLng start, LatLng end) {
        // Simple straight line route (in real app, you'd use Google Directions API)
        PolylineOptions polylineOptions = new PolylineOptions()
                .add(start, end)
                .width(5)
                .color(0xFF0000FF);
        mMap.addPolyline(polylineOptions);
    }
    
    private void loadRescuerInfo() {
        db.collection("Sagip/users/rescuer").document(rescuerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String rescuerName = documentSnapshot.getString("rescuegroup");
                        if (rescuerName != null && !rescuerName.isEmpty()) {
                            tvRescuerName.setText(rescuerName);
                        } else {
                            tvRescuerName.setText("Rescuer");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading rescuer info: " + e.getMessage());
                    tvRescuerName.setText("Rescuer");
                });
    }
    
    private void loadNearestHospital() {
        Log.d(TAG, "🏥 Loading nearest hospital information...");
        
        // Query hospitals from database
        db.collection("Sagip/users/hospital")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Find the nearest hospital based on senior's location
                        findNearestHospital(querySnapshot);
                    } else {
                        Log.w(TAG, "⚠️ No hospitals found in database");
                        setDefaultHospitalInfo();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading hospitals: " + e.getMessage());
                    setDefaultHospitalInfo();
                });
    }
    
    private void findNearestHospital(com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        Log.d(TAG, "🤖 Using AI to determine optimal hospital...");
        Log.d(TAG, "📊 Senior location: " + seniorLat + ", " + seniorLng);
        Log.d(TAG, "📊 Total hospitals in database: " + querySnapshot.size());
        
        // Collect all valid hospitals with their data
        java.util.List<HospitalData> hospitals = new java.util.ArrayList<>();
        java.util.List<DocumentSnapshot> hospitalsToGeocode = new java.util.ArrayList<>();
        
        // First pass: collect hospitals with coordinates and those that need geocoding
        for (DocumentSnapshot hospitalDoc : querySnapshot) {
            // Try to get coordinates from currentLocation GeoPoint first (primary method)
            Double lat = null;
            Double lng = null;
            
            com.google.firebase.firestore.GeoPoint currentLocation = hospitalDoc.getGeoPoint("currentLocation");
            if (currentLocation != null) {
                lat = currentLocation.getLatitude();
                lng = currentLocation.getLongitude();
                Log.d(TAG, "📍 Found currentLocation GeoPoint: " + lat + ", " + lng);
            } else {
                // Fallback to individual coordinate fields
                lat = hospitalDoc.getDouble("latitude");
                lng = hospitalDoc.getDouble("longitude");
                
                // If not found, try alternative field names
                if (lat == null) lat = hospitalDoc.getDouble("lat");
                if (lng == null) lng = hospitalDoc.getDouble("lng");
                if (lat == null) lat = hospitalDoc.getDouble("currentLatitude");
                if (lng == null) lng = hospitalDoc.getDouble("currentLongitude");
            }
            
            String erStatus = hospitalDoc.getString("erStatus");
            String hospitalName = hospitalDoc.getString("hospitalName");
            String address = hospitalDoc.getString("hospitalAddress");
            
            Log.d(TAG, "🔍 Checking hospital: " + hospitalName + " | Lat: " + lat + " | Lng: " + lng + " | ER: " + erStatus + " | Address: " + address);
            
            // Debug: Show all available fields in the document
            Log.d(TAG, "📋 Hospital document fields: " + hospitalDoc.getData().keySet());
            
            // Debug: Show currentLocation details
            if (currentLocation != null) {
                Log.d(TAG, "📍 Using currentLocation GeoPoint: " + currentLocation.getLatitude() + ", " + currentLocation.getLongitude());
            } else {
                Log.d(TAG, "📍 No currentLocation GeoPoint found, using fallback coordinates");
            }
            
            if (lat != null && lng != null && seniorLat != 0.0 && seniorLng != 0.0) {
                // Hospital has coordinates, use them
                double distance = calculateDistance(seniorLat, seniorLng, lat, lng);
                
                HospitalData hospital = new HospitalData();
                hospital.document = hospitalDoc;
                hospital.name = hospitalName != null ? hospitalName : "Unknown Hospital";
                hospital.address = address != null ? address : "Address not available";
                hospital.latitude = lat;
                hospital.longitude = lng;
                hospital.distance = distance;
                hospital.erStatus = erStatus != null ? erStatus : "unknown";
                
                hospitals.add(hospital);
                Log.d(TAG, "✅ Added hospital: " + hospital.name + " | Distance: " + String.format("%.2f km", distance) + " | ER Status: " + hospital.erStatus);
            } else if (address != null && !address.trim().isEmpty()) {
                // Hospital doesn't have coordinates, add to geocoding list
                hospitalsToGeocode.add(hospitalDoc);
                Log.d(TAG, "📍 Added to geocoding queue: " + hospitalName + " | Address: " + address);
            } else {
                Log.w(TAG, "❌ Skipped hospital: " + hospitalName + " | Reason: lat=" + lat + ", lng=" + lng + ", address=" + address + ", seniorLat=" + seniorLat + ", seniorLng=" + seniorLng);
            }
        }
        
        // If we have hospitals with coordinates, use them immediately
        if (!hospitals.isEmpty()) {
            Log.d(TAG, "🏥 Processing " + hospitals.size() + " hospitals with coordinates");
            processHospitals(hospitals);
        } else if (!hospitalsToGeocode.isEmpty()) {
            // No hospitals with coordinates, try geocoding
            Log.d(TAG, "🔍 No hospitals with coordinates, attempting geocoding for " + hospitalsToGeocode.size() + " hospitals");
            geocodeHospitals(hospitalsToGeocode, hospitals);
        } else {
            Log.w(TAG, "⚠️ No hospitals with valid coordinates or addresses found");
            setDefaultHospitalInfo();
        }
    }
    
    private void geocodeHospitals(java.util.List<DocumentSnapshot> hospitalsToGeocode, java.util.List<HospitalData> hospitals) {
        Log.d(TAG, "🔍 Starting geocoding for " + hospitalsToGeocode.size() + " hospitals...");
        
        int geocodedCount = 0;
        for (DocumentSnapshot hospitalDoc : hospitalsToGeocode) {
            String hospitalName = hospitalDoc.getString("hospitalName");
            String address = hospitalDoc.getString("hospitalAddress");
            String erStatus = hospitalDoc.getString("erStatus");
            
            boolean geocoded = geocodeHospitalAddress(hospitalDoc, hospitalName, address, erStatus, hospitals);
            if (geocoded) {
                geocodedCount++;
            }
        }
        
        Log.d(TAG, "📍 Geocoding completed: " + geocodedCount + " out of " + hospitalsToGeocode.size() + " hospitals geocoded successfully");
        
        // Process hospitals after geocoding
        if (!hospitals.isEmpty()) {
            processHospitals(hospitals);
        } else {
            Log.w(TAG, "⚠️ No hospitals could be geocoded");
            setDefaultHospitalInfo();
        }
    }
    
    private void processHospitals(java.util.List<HospitalData> hospitals) {
        if (hospitals.isEmpty()) {
            Log.w(TAG, "⚠️ No hospitals with valid coordinates found");
            setDefaultHospitalInfo();
            return;
        }
        
        // Use AI to determine the best hospital
        HospitalData selectedHospital = selectOptimalHospital(hospitals);
        
        if (selectedHospital != null) {
            displayHospitalInfo(selectedHospital.document, selectedHospital.distance);
            Log.d(TAG, "🤖 AI selected: " + selectedHospital.name + " (Distance: " + String.format("%.2f km", selectedHospital.distance) + ", ER Status: " + selectedHospital.erStatus + ")");
        } else {
            Log.w(TAG, "⚠️ AI could not select a hospital");
            setDefaultHospitalInfo();
        }
    }
    
    private boolean geocodeHospitalAddress(DocumentSnapshot hospitalDoc, String hospitalName, String address, String erStatus, java.util.List<HospitalData> hospitals) {
        if (address == null || address.isEmpty()) {
            Log.w(TAG, "❌ Cannot geocode: address is null or empty");
            return false;
        }
        
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        if (!Geocoder.isPresent()) {
            Log.e(TAG, "❌ Geocoder is not available on this device");
            return false;
        }
        
        try {
            Log.d(TAG, "🔍 Attempting to geocode: " + address);
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address location = addresses.get(0);
                double lat = location.getLatitude();
                double lng = location.getLongitude();
                
                Log.d(TAG, "📍 Geocoded coordinates: " + lat + ", " + lng);
                
                if (seniorLat != 0.0 && seniorLng != 0.0) {
                    double distance = calculateDistance(seniorLat, seniorLng, lat, lng);
                    
                    HospitalData hospital = new HospitalData();
                    hospital.document = hospitalDoc;
                    hospital.name = hospitalName != null ? hospitalName : "Unknown Hospital";
                    hospital.address = address != null ? address : "Address not available";
                    hospital.latitude = lat;
                    hospital.longitude = lng;
                    hospital.distance = distance;
                    hospital.erStatus = erStatus != null ? erStatus : "unknown";
                    
                    hospitals.add(hospital);
                    Log.d(TAG, "✅ Geocoded hospital: " + hospital.name + " | Distance: " + String.format("%.2f km", distance) + " | ER Status: " + hospital.erStatus);
                    return true;
                } else {
                    Log.w(TAG, "❌ Cannot calculate distance: senior location is invalid");
                    return false;
                }
            } else {
                Log.w(TAG, "❌ Could not geocode address: " + address + " - no results returned");
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ Geocoding failed for address: " + address, e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "❌ Unexpected error during geocoding: " + address, e);
            return false;
        }
    }
    
    private HospitalData selectOptimalHospital(java.util.List<HospitalData> hospitals) {
        Log.d(TAG, "🤖 AI Decision Making Process:");
        
        // AI Decision Matrix
        HospitalData bestHospital = null;
        double bestScore = Double.MIN_VALUE;
        
        for (HospitalData hospital : hospitals) {
            double score = calculateHospitalScore(hospital);
            Log.d(TAG, "🏥 " + hospital.name + " | Score: " + String.format("%.2f", score) + " | ER: " + hospital.erStatus + " | Distance: " + String.format("%.2f km", hospital.distance));
            
            if (score > bestScore) {
                bestScore = score;
                bestHospital = hospital;
            }
        }
        
        return bestHospital;
    }
    
    private double calculateHospitalScore(HospitalData hospital) {
        double score = 0.0;
        
        // Distance factor (closer is better) - 40% weight
        double distanceScore = Math.max(0, 100 - (hospital.distance * 10)); // 100 points max, -10 points per km
        score += distanceScore * 0.4;
        
        // ER Status factor - 60% weight
        double erScore = getERStatusScore(hospital.erStatus);
        score += erScore * 0.6;
        
        Log.d(TAG, "📊 " + hospital.name + " | Distance Score: " + String.format("%.1f", distanceScore) + " | ER Score: " + String.format("%.1f", erScore) + " | Total: " + String.format("%.1f", score));
        
        return score;
    }
    
    private double getERStatusScore(String erStatus) {
        if (erStatus == null) return 50.0; // Neutral score for unknown status
        
        switch (erStatus.toLowerCase()) {
            case "available":
                return 100.0; // Maximum score for available ER
            case "busy":
                return 70.0; // Good score but not optimal
            case "crowded":
            case "overcrowded":
                return 30.0; // Low score for overcrowded ER
            default:
                return 50.0; // Neutral score for unknown status
        }
    }
    
    // Helper class for hospital data
    private static class HospitalData {
        DocumentSnapshot document;
        String name;
        String address;
        double latitude;
        double longitude;
        double distance;
        String erStatus;
    }
    
    private void displayHospitalInfo(DocumentSnapshot hospitalDoc, double distance) {
        hospitalName = hospitalDoc.getString("hospitalName");
        hospitalAddress = hospitalDoc.getString("hospitalAddress");
        
        // Get coordinates from currentLocation GeoPoint first (same logic as in findNearestHospital)
        com.google.firebase.firestore.GeoPoint currentLocation = hospitalDoc.getGeoPoint("currentLocation");
        if (currentLocation != null) {
            hospitalLat = currentLocation.getLatitude();
            hospitalLng = currentLocation.getLongitude();
            Log.d(TAG, "📍 Using currentLocation for display: " + hospitalLat + ", " + hospitalLng);
        } else {
            // Fallback to individual coordinate fields
            Double lat = hospitalDoc.getDouble("latitude");
            Double lng = hospitalDoc.getDouble("longitude");
            
            if (lat != null && lng != null) {
                hospitalLat = lat;
                hospitalLng = lng;
            } else {
                // Try alternative field names
                lat = hospitalDoc.getDouble("lat");
                lng = hospitalDoc.getDouble("lng");
                if (lat != null && lng != null) {
                    hospitalLat = lat;
                    hospitalLng = lng;
                } else {
                    hospitalLat = 0.0;
                    hospitalLng = 0.0;
                    Log.w(TAG, "⚠️ No coordinates found for hospital: " + hospitalName);
                }
            }
        }
        
        String erStatus = hospitalDoc.getString("erStatus");
        
        if (hospitalName != null) {
            tvHospitalName.setText(hospitalName);
        } else {
            tvHospitalName.setText("Hospital");
        }
        
        if (hospitalAddress != null) {
            tvHospitalAddress.setText(hospitalAddress);
        } else {
            tvHospitalAddress.setText("Address not available");
        }
        
        // Display distance and ER status
        String statusText = String.format(Locale.getDefault(), 
                "Distance: %.2f km", distance);
        
        if (erStatus != null && !erStatus.isEmpty()) {
            String erStatusDisplay = getERStatusDisplay(erStatus);
            statusText += " | ER: " + erStatusDisplay;
        }
        
        tvHospitalDistance.setText(statusText);
        
        Log.d(TAG, "🏥 AI Selected hospital: " + hospitalName + " (" + distance + " km, ER: " + erStatus + ")");
        
        // Add hospital marker to map if available
        if (mMap != null && hospitalLat != 0.0 && hospitalLng != 0.0) {
            addHospitalMarker();
        }
    }
    
    private String getERStatusDisplay(String erStatus) {
        if (erStatus == null) return "Unknown";
        
        switch (erStatus.toLowerCase()) {
            case "available":
                return "✅ Available";
            case "busy":
                return "⚠️ Busy";
            case "crowded":
            case "overcrowded":
                return "🚨 Overcrowded";
            default:
                return "❓ " + erStatus;
        }
    }
    
    private void setDefaultHospitalInfo() {
        tvHospitalName.setText("Hospital Information Not Available");
        tvHospitalAddress.setText("Please contact emergency services");
        tvHospitalDistance.setText("Distance: Unknown");
        btnNavigateHospital.setEnabled(false);
    }
    
    private void addHospitalMarker() {
        if (hospitalLat != 0.0 && hospitalLng != 0.0) {
            LatLng hospitalLocation = new LatLng(hospitalLat, hospitalLng);
            
            // Get ER status for marker snippet
            String erStatus = "Unknown";
            if (hospitalName != null) {
                // Try to get ER status from the hospital data
                // This would need to be passed from the displayHospitalInfo method
                erStatus = "AI Selected";
            }
            
            mMap.addMarker(new MarkerOptions()
                    .position(hospitalLocation)
                    .title("🏥 " + hospitalName)
                    .snippet("AI Selected | " + hospitalAddress));
        }
    }
    
    private void navigateToHospital() {
        if (hospitalLat != 0.0 && hospitalLng != 0.0) {
            String uri = String.format(Locale.getDefault(), 
                    "google.navigation:q=%.6f,%.6f", hospitalLat, hospitalLng);
            Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            navIntent.setPackage("com.google.android.apps.maps");
            startActivity(navIntent);
        } else if (hospitalAddress != null && !hospitalAddress.isEmpty()) {
            // Fallback to web navigation using address
            String url = "https://www.google.com/maps/dir/?api=1&destination=" + 
                    Uri.encode(hospitalAddress) + "&travelmode=driving";
            Intent webNavIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(webNavIntent);
        } else {
            Toast.makeText(this, "Hospital location not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void getCurrentLocationAndCalculateArrival() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        rescuerLat = location.getLatitude();
                        rescuerLng = location.getLongitude();
                        
                        // Update UI with rescuer location
                        tvRescuerLocation.setText(String.format(Locale.getDefault(), 
                                "%.6f, %.6f", rescuerLat, rescuerLng));
                        
                        // Calculate distance and arrival time
                        calculateDistanceAndArrival();
                        
                        // Update map if ready
                        if (mMap != null) {
                            onMapReady(mMap);
                        }
                    }
                });
    }
    
    private void calculateDistanceAndArrival() {
        if (seniorLat == 0.0 || seniorLng == 0.0 || rescuerLat == 0.0 || rescuerLng == 0.0) {
            tvDistance.setText("Distance: Calculating...");
            tvEstimatedArrival.setText("ETA: Calculating...");
            return;
        }
        
        // Calculate distance using Haversine formula
        double distance = calculateDistance(rescuerLat, rescuerLng, seniorLat, seniorLng);
        tvDistance.setText(String.format(Locale.getDefault(), "Distance: %.2f km", distance));
        
        // Estimate arrival time (assuming average speed of 30 km/h in city)
        double estimatedTimeMinutes = (distance / 30.0) * 60; // Convert to minutes
        long estimatedArrivalTime = System.currentTimeMillis() + (long)(estimatedTimeMinutes * 60 * 1000);
        
        String arrivalTimeStr = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(estimatedArrivalTime));
        tvEstimatedArrival.setText("ETA: " + arrivalTimeStr + " (" + 
                String.format(Locale.getDefault(), "%.0f min", estimatedTimeMinutes) + ")");
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
    
    private void startLocationUpdates() {
        // Update location every 30 seconds
        new android.os.Handler().postDelayed(() -> {
            getCurrentLocationAndCalculateArrival();
            startLocationUpdates(); // Recursive call for continuous updates
        }, 30000);
    }
    
    private void callSenior() {
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            startActivity(callIntent);
        } else {
            Toast.makeText(this, "Senior phone number not available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openNavigation() {
        if (seniorLat != 0.0 && seniorLng != 0.0) {
            String uri = String.format(Locale.getDefault(), 
                    "google.navigation:q=%.6f,%.6f", seniorLat, seniorLng);
            Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            navIntent.setPackage("com.google.android.apps.maps");
            startActivity(navIntent);
        } else {
            // Fallback to web navigation
            String url = "https://www.google.com/maps/dir/?api=1&destination=" + 
                    Uri.encode(locationAddress) + "&travelmode=driving";
            Intent webNavIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(webNavIntent);
        }
    }
    
    private void updateLocation() {
        getCurrentLocationAndCalculateArrival();
        Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show();
    }
    
    private void markArrived() {
        // Update status to arrived
        tvStatus.setText("✅ ARRIVED");
        btnMarkArrived.setEnabled(false);
        btnMarkArrived.setText("ARRIVED");
        
        // Update database
        updateEmergencyStatus("arrived");
        
        Toast.makeText(this, "Status updated: Arrived at location", Toast.LENGTH_LONG).show();
    }
    
    private void updateEmergencyStatus(String status) {
        // Update the emergency status in database
        if (emergencyId != null) {
            db.collection("Sagip/emergencyRequests/activeRequests").document(emergencyId)
                    .update("status", status, "arrivedAt", System.currentTimeMillis())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Emergency status updated to: " + status);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating emergency status: " + e.getMessage());
                    });
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndCalculateArrival();
            } else {
                Toast.makeText(this, "Location permission required for accurate tracking", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Update status to in-progress when leaving
        updateEmergencyStatus("in_progress");
    }
}
