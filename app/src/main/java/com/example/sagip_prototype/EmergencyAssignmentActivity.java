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
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.firestore.GeoPoint;

import com.example.sagip_prototype.ai.EmergencyRoomAI;
import com.example.sagip_prototype.models.Emergency;
import com.example.sagip_prototype.models.Hospital;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class EmergencyAssignmentActivity extends AppCompatActivity implements OnMapReadyCallback {
    // FORCE REBUILD - Updated to use markDone instead of markArrived
    private static final String TAG = "EmergencyAssignmentActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    
    // UI Components
    private TextView tvSeniorName, tvSeniorPhone, tvLocation;
    private TextView tvEstimatedArrival, tvDistance, tvStatus;
    private TextView tvHospitalName, tvHospitalAddress, tvHospitalDistance;
    private Button btnCallSenior, btnNavigateToSenior, btnUpdateLocation, btnMarkDone, btnNavigateHospital;
    private GoogleMap mMap;
    
    // Data
    private String seniorName, seniorPhone, locationAddress, rescuerId;
    private double seniorLat, seniorLng, rescuerLat, rescuerLng;
    private double hospitalLat, hospitalLng;
    private String hospitalName, hospitalAddress;
    private long assignmentTime;
    private String emergencyId;
    private String emergencyType;
    private String emergencySeverity;
    
    // AI System
    private EmergencyRoomAI emergencyRoomAI;
    private Emergency currentEmergency;
    private double aiConfidenceScore;
    private List<Hospital> alternativeHospitals;
    
    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_assignment);
        
        Log.d(TAG, "🚨🚨🚨 EmergencyAssignmentActivity CREATED 🚨🚨🚨");
        Log.d(TAG, "🔍 [ACTIVITY_CREATE] Intent extras:");
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, "🔍 [ACTIVITY_CREATE]   " + key + " = " + value);
            }
        } else {
            Log.w(TAG, "🔍 [ACTIVITY_CREATE] No intent extras found!");
        }
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Initialize AI System
        emergencyRoomAI = new EmergencyRoomAI(db);
        Log.d(TAG, "🤖 AI System initialized");
        
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
        
        // Add debug logging for hospital data
        logHospitalDataStatus();
        
        // Test hospital loading after a delay
        new android.os.Handler().postDelayed(() -> {
            Log.d(TAG, "🔍 Testing hospital data after 5 seconds...");
            logHospitalDataStatus();
            if (hospitalLat == 0.0 && hospitalLng == 0.0) {
                Log.w(TAG, "⚠️ Hospital coordinates still not loaded, trying to reload...");
                loadNearestHospital();
                
                // If still no data after reload, set test data for debugging
                new android.os.Handler().postDelayed(() -> {
                    if (hospitalLat == 0.0 && hospitalLng == 0.0) {
                        Log.w(TAG, "🔧 Setting test hospital data for debugging...");
                        setTestHospitalData();
                    }
                }, 3000);
            }
        }, 5000);
        
        // Get current location and calculate arrival time
        getCurrentLocationAndCalculateArrival();
        
        // Update location every 30 seconds
        startLocationUpdates();
        
        // Check hospital data status periodically
        startHospitalDataStatusUpdates();
    }
    
    private void getIntentData() {
        Intent intent = getIntent();
        seniorName = intent.getStringExtra("senior_name");
        seniorPhone = intent.getStringExtra("senior_phone");
        locationAddress = intent.getStringExtra("location_address");
        seniorLat = intent.getDoubleExtra("senior_lat", 0.0);
        seniorLng = intent.getDoubleExtra("senior_lng", 0.0);
        assignmentTime = intent.getLongExtra("assignment_time", System.currentTimeMillis());
        emergencyId = intent.getStringExtra("request_id");
        if (emergencyId == null || emergencyId.isEmpty()) {
            emergencyId = intent.getStringExtra("emergency_id");
            Log.w(TAG, "request_id not found, using emergency_id: " + emergencyId);
        }
        
        // Get emergency type and severity for AI system
        emergencyType = intent.getStringExtra("emergency_type");
        emergencySeverity = intent.getStringExtra("severity");
        if (emergencyType == null) emergencyType = "general";
        if (emergencySeverity == null) emergencySeverity = "medium";
        
        rescuerId = mAuth.getCurrentUser().getUid();
        
        Log.d(TAG, "Emergency assignment data: " + seniorName + " at " + locationAddress);
        Log.d(TAG, "Senior phone number: " + seniorPhone);
        Log.d(TAG, "Senior coordinates: " + seniorLat + ", " + seniorLng);
        Log.d(TAG, "Using emergencyId (request_id): " + emergencyId);
        Log.d(TAG, "Emergency type: " + emergencyType + ", Severity: " + emergencySeverity);
        
        // Create Emergency object for AI system
        createEmergencyObject();
        
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
        
        // Debug: Check specific keys
        String requestId = intent.getStringExtra("request_id");
        String emergencyId = intent.getStringExtra("emergency_id");
        Log.d(TAG, "🔍 request_id from intent: " + requestId);
        Log.d(TAG, "🔍 emergency_id from intent: " + emergencyId);
        Log.d(TAG, "🔍 Final emergencyId being used: " + emergencyId);
    }
    
    private void initializeViews() {
        tvSeniorName = findViewById(R.id.tv_senior_name);
        tvSeniorPhone = findViewById(R.id.tv_senior_phone);
        tvLocation = findViewById(R.id.tv_location);
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
        btnNavigateToSenior = findViewById(R.id.btn_navigate_to_senior);
        btnUpdateLocation = findViewById(R.id.btn_update_location);
        btnMarkDone = findViewById(R.id.btn_mark_arrived);
        btnNavigateHospital = findViewById(R.id.btn_navigate_hospital);
        
        // Set initial data
        tvSeniorName.setText(seniorName != null ? seniorName : "Unknown Senior");
        
        // Format and display phone number
        String phoneToDisplay = seniorPhone != null && !seniorPhone.isEmpty() ? 
            PhoneNumberUtils.formatPhoneNumber(seniorPhone) : "Phone not available";
        Log.d(TAG, "Setting phone display to: " + phoneToDisplay);
        tvSeniorPhone.setText(phoneToDisplay);
        
        tvLocation.setText(locationAddress != null ? locationAddress : "Location not available");
        
        // Note: tvAssignmentTime no longer exists in the layout (removed in redesign)
        // Log assignment time instead
        String timeStr = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(assignmentTime));
        Log.d(TAG, "Assignment Time: " + timeStr);
        
        tvStatus.setText("🚨 RESPONDING");
        
        // Setup button listeners
        setupButtonListeners();
    }
    
    private void setupButtonListeners() {
        btnCallSenior.setOnClickListener(v -> callSenior());
        btnNavigateToSenior.setOnClickListener(v -> openNavigation());
        btnUpdateLocation.setOnClickListener(v -> updateLocation());
        btnMarkDone.setOnClickListener(v -> markDone());
        
        // Test functionality removed to prevent confusion
        
        // Add null check and debug logging for hospital navigation button
        if (btnNavigateHospital != null) {
            btnNavigateHospital.setOnClickListener(v -> {
                Log.d(TAG, "🏥 Hospital navigation button clicked!");
                Toast.makeText(this, getString(R.string.hospital_navigation_clicked), Toast.LENGTH_SHORT).show();
                navigateToHospital();
            });
            Log.d(TAG, "✅ Hospital navigation button listener set successfully");
        } else {
            Log.e(TAG, "❌ Hospital navigation button is null!");
        }
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
                            Log.d(TAG, "Rescuer Name: " + rescuerName);
                        } else {
                            Log.d(TAG, "Rescuer Name: Rescuer (default)");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading rescuer info: " + e.getMessage());
                });
    }
    
    private void createEmergencyObject() {
        currentEmergency = new Emergency();
        currentEmergency.emergencyId = emergencyId;
        currentEmergency.seniorName = seniorName;
        currentEmergency.seniorPhone = seniorPhone;
        currentEmergency.location = new GeoPoint(seniorLat, seniorLng);
        currentEmergency.locationAddress = locationAddress;
        currentEmergency.emergencyType = emergencyType;
        currentEmergency.severity = emergencySeverity;
        currentEmergency.timestamp = assignmentTime;
        currentEmergency.rescuerId = rescuerId;
        currentEmergency.status = "responded";
        
        Log.d(TAG, "🤖 Emergency object created for AI system");
        Log.d(TAG, "   Type: " + emergencyType + ", Severity: " + emergencySeverity);
    }
    
    private void loadNearestHospital() {
        Log.d(TAG, "🏥 Loading nearest hospital using AI system...");
        Log.d(TAG, "📍 Senior location for hospital search: " + seniorLat + ", " + seniorLng);
        
        // Validate emergency object
        if (currentEmergency == null || currentEmergency.location == null) {
            Log.e(TAG, "❌ Emergency object not initialized properly");
            setDefaultHospitalInfo();
            return;
        }
        
        // Get rescuer location first, then call AI
        getCurrentLocationForAI();
    }
    
    private void getCurrentLocationForAI() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "⚠️ Location permission not granted, using senior location as rescuer location");
            rescuerLat = seniorLat;
            rescuerLng = seniorLng;
            callAIHospitalSelection();
            return;
        }
        
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        rescuerLat = location.getLatitude();
                        rescuerLng = location.getLongitude();
                        Log.d(TAG, "📍 Rescuer location: " + rescuerLat + ", " + rescuerLng);
                    } else {
                        Log.w(TAG, "⚠️ Rescuer location null, using senior location");
                        rescuerLat = seniorLat;
                        rescuerLng = seniorLng;
                    }
                    callAIHospitalSelection();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to get rescuer location: " + e.getMessage());
                    rescuerLat = seniorLat;
                    rescuerLng = seniorLng;
                    callAIHospitalSelection();
                });
    }
    
    private void callAIHospitalSelection() {
        Log.d(TAG, "🤖 Calling AI system for hospital selection...");
        Log.d(TAG, "   Emergency: " + emergencyType + " (" + emergencySeverity + ")");
        Log.d(TAG, "   Senior: " + seniorLat + ", " + seniorLng);
        Log.d(TAG, "   Rescuer: " + rescuerLat + ", " + rescuerLng);
        
        emergencyRoomAI.selectOptimalHospital(
            currentEmergency,
            rescuerLat,
            rescuerLng,
            new EmergencyRoomAI.HospitalSelectionCallback() {
                @Override
                public void onResult(EmergencyRoomAI.AIRecommendationResult result) {
                    runOnUiThread(() -> handleAIResult(result));
                }
            }
        );
    }
    
    private void handleAIResult(EmergencyRoomAI.AIRecommendationResult result) {
        if (result.recommendedHospital == null) {
            Log.w(TAG, "⚠️ AI returned no hospital recommendation: " + result.message);
            setDefaultHospitalInfo();
            return;
        }
        
        Hospital hospital = result.recommendedHospital;
        aiConfidenceScore = result.confidenceScore;
        alternativeHospitals = result.alternativeHospitals;
        
        Log.d(TAG, "🤖 AI RECOMMENDATION:");
        Log.d(TAG, "   Hospital: " + hospital.name);
        Log.d(TAG, "   Distance: " + String.format("%.2f km", hospital.distanceFromSenior));
        Log.d(TAG, "   TOPSIS Score: " + String.format("%.2f%%", hospital.topsisScore * 100));
        Log.d(TAG, "   ML Score: " + String.format("%.2f%%", hospital.mlScore * 100));
        Log.d(TAG, "   Final Score: " + String.format("%.2f%%", hospital.finalScore * 100));
        Log.d(TAG, "   Confidence: " + String.format("%.1f%%", aiConfidenceScore * 100));
        Log.d(TAG, "   ER Status: " + hospital.operationalStatus);
        
        if (result.isLowConfidence()) {
            Log.w(TAG, "⚠️ LOW CONFIDENCE - Manual verification recommended");
        }
        
        if (alternativeHospitals != null && !alternativeHospitals.isEmpty()) {
            Log.d(TAG, "📋 Alternative hospitals (" + alternativeHospitals.size() + "):");
            for (int i = 0; i < alternativeHospitals.size(); i++) {
                Hospital alt = alternativeHospitals.get(i);
                Log.d(TAG, "   " + (i+1) + ". " + alt.name + " (" + String.format("%.2f km", alt.distanceFromSenior) + ")");
            }
        }
        
        // Display hospital info
        displayAIHospitalInfo(hospital, result);
    }
    
    private void displayAIHospitalInfo(Hospital hospital, EmergencyRoomAI.AIRecommendationResult result) {
        hospitalName = hospital.name;
        hospitalAddress = hospital.address;
        hospitalLat = hospital.location.getLatitude();
        hospitalLng = hospital.location.getLongitude();
        
        if (hospitalName != null) {
            // Add AI confidence indicator to hospital name
            String confidenceIndicator = "";
            if (result.isHighConfidence()) {
                confidenceIndicator = " ✓ (AI: High)";
            } else if (result.isMediumConfidence()) {
                confidenceIndicator = " ⚠ (AI: Medium)";
            } else {
                confidenceIndicator = " ⚠️ (AI: Low - Verify)";
            }
            
            tvHospitalName.setText(hospitalName + confidenceIndicator);
            Log.d(TAG, "✅ Hospital name set: " + hospitalName + confidenceIndicator);
        }
        
        if (hospitalAddress != null) {
            tvHospitalAddress.setText(hospitalAddress);
            Log.d(TAG, "✅ Hospital address set: " + hospitalAddress);
        }
        
        // Display distance with AI scores
        String distanceText = String.format("%.2f km away\nTOPSIS: %.0f%% | ML: %.0f%% | Final: %.0f%%",
            hospital.distanceFromSenior,
            hospital.topsisScore * 100,
            hospital.mlScore * 100,
            hospital.finalScore * 100);
        tvHospitalDistance.setText(distanceText);
        Log.d(TAG, "✅ Hospital distance set: " + distanceText);
        
        // Update map with hospital marker
        if (mMap != null && hospitalLat != 0.0 && hospitalLng != 0.0) {
            LatLng hospitalLocation = new LatLng(hospitalLat, hospitalLng);
            mMap.addMarker(new MarkerOptions()
                    .position(hospitalLocation)
                    .title(hospitalName)
                    .snippet("AI Selected - Confidence: " + String.format("%.0f%%", aiConfidenceScore * 100)));
            Log.d(TAG, "✅ Hospital marker added to map");
        }
        
        // Show alternatives in a toast (could be enhanced with a dialog)
        if (alternativeHospitals != null && !alternativeHospitals.isEmpty()) {
            StringBuilder altText = new StringBuilder("Alternatives: ");
            for (int i = 0; i < Math.min(2, alternativeHospitals.size()); i++) {
                if (i > 0) altText.append(", ");
                altText.append(alternativeHospitals.get(i).name);
            }
            Toast.makeText(this, altText.toString(), Toast.LENGTH_LONG).show();
        }
    }
    
    // OLD METHODS BELOW - KEPT FOR FALLBACK
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
            // Try to use the first available hospital as fallback
            if (!hospitals.isEmpty()) {
                HospitalData fallbackHospital = hospitals.get(0);
                displayHospitalInfo(fallbackHospital.document, fallbackHospital.distance);
                Log.d(TAG, "🔄 Using fallback hospital: " + fallbackHospital.name);
            } else {
                setDefaultHospitalInfo();
            }
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
        
        // Enable navigation button if hospital coordinates are available
        if (hospitalLat != 0.0 && hospitalLng != 0.0) {
            btnNavigateHospital.setEnabled(true);
            Log.d(TAG, "✅ Navigation button enabled for hospital: " + hospitalName);
        } else {
            btnNavigateHospital.setEnabled(false);
            Log.w(TAG, "⚠️ Navigation button disabled - no coordinates for hospital: " + hospitalName);
        }
        
        // Add hospital marker to map if available
        if (mMap != null && hospitalLat != 0.0 && hospitalLng != 0.0) {
            addHospitalMarker();
        }
        
        // Send hospital details notification to senior
        sendHospitalDetailsNotificationToSenior(hospitalDoc, distance);
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
        Log.w(TAG, "⚠️ Using default hospital info - navigation disabled");
    }
    
    private void logHospitalDataStatus() {
        Log.d(TAG, "🔍 Hospital Data Status Check:");
        Log.d(TAG, "  - Hospital Name: " + (hospitalName != null ? hospitalName : "null"));
        Log.d(TAG, "  - Hospital Address: " + (hospitalAddress != null ? hospitalAddress : "null"));
        Log.d(TAG, "  - Hospital Coordinates: " + hospitalLat + ", " + hospitalLng);
        Log.d(TAG, "  - Navigation Button Enabled: " + btnNavigateHospital.isEnabled());
        Log.d(TAG, "  - Senior Location: " + seniorLat + ", " + seniorLng);
    }
    
    private void setTestHospitalData() {
        Log.d(TAG, "🔧 Setting test hospital data for debugging...");
        hospitalName = "Test Hospital";
        hospitalAddress = "123 Test Street, Test City";
        hospitalLat = 14.5995; // Manila coordinates as example
        hospitalLng = 120.9842;
        
        // Update UI
        tvHospitalName.setText(hospitalName);
        tvHospitalAddress.setText(hospitalAddress);
        tvHospitalDistance.setText("Distance: Test Mode");
        
        // Enable button
        btnNavigateHospital.setEnabled(true);
        
        Log.d(TAG, "✅ Test hospital data set: " + hospitalName + " at " + hospitalLat + ", " + hospitalLng);
        Toast.makeText(this, getString(R.string.test_hospital_data_loaded), Toast.LENGTH_SHORT).show();
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
        Log.d(TAG, "🏥 Navigate to hospital clicked");
        Log.d(TAG, "📍 Hospital coordinates: " + hospitalLat + ", " + hospitalLng);
        Log.d(TAG, "📍 Hospital address: " + hospitalAddress);
        Log.d(TAG, "📍 Hospital name: " + hospitalName);
        Log.d(TAG, "📍 Button enabled: " + btnNavigateHospital.isEnabled());
        
        // Send alert to hospital first
        sendHospitalAlert();
        
        if (hospitalLat != 0.0 && hospitalLng != 0.0) {
            try {
                String uri = String.format(Locale.getDefault(), 
                        "google.navigation:q=%.6f,%.6f", hospitalLat, hospitalLng);
                Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                navIntent.setPackage("com.google.android.apps.maps");
                
                // Check if Google Maps is available
                if (navIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(navIntent);
                    Log.d(TAG, "✅ Opened Google Maps navigation to hospital");
                    Toast.makeText(this, String.format(getString(R.string.opening_navigation_to_format), hospitalName), Toast.LENGTH_SHORT).show();
                } else {
                    // Fallback to web navigation
                    openWebNavigation();
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error opening Google Maps navigation: " + e.getMessage());
                openWebNavigation();
            }
        } else if (hospitalAddress != null && !hospitalAddress.isEmpty()) {
            // Fallback to web navigation using address
            openWebNavigation();
        } else {
            Log.w(TAG, "❌ No hospital location data available for navigation");
            Toast.makeText(this, getString(R.string.hospital_location_not_available), Toast.LENGTH_LONG).show();
            
            // For debugging: try to open a test location
            Log.d(TAG, "🔧 Opening test location for debugging...");
            try {
                String testUri = "google.navigation:q=14.5995,120.9842";
                Intent testNavIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(testUri));
                testNavIntent.setPackage("com.google.android.apps.maps");
                if (testNavIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(testNavIntent);
                    Toast.makeText(this, getString(R.string.opening_test_location), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.google_maps_not_available), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error opening test location: " + e.getMessage());
            }
        }
    }
    
    private void openWebNavigation() {
        try {
            String url;
            if (hospitalLat != 0.0 && hospitalLng != 0.0) {
                // Use coordinates for web navigation
                url = "https://www.google.com/maps/dir/?api=1&destination=" + 
                        hospitalLat + "," + hospitalLng + "&travelmode=driving";
            } else if (hospitalAddress != null && !hospitalAddress.isEmpty()) {
                // Use address for web navigation
                url = "https://www.google.com/maps/dir/?api=1&destination=" + 
                        Uri.encode(hospitalAddress) + "&travelmode=driving";
            } else {
                Toast.makeText(this, getString(R.string.no_hospital_location_data), Toast.LENGTH_SHORT).show();
                return;
            }
            
            Intent webNavIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(webNavIntent);
            Log.d(TAG, "✅ Opened web navigation to hospital");
            Toast.makeText(this, String.format(getString(R.string.opening_web_navigation_to_format), hospitalName), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error opening web navigation: " + e.getMessage());
            Toast.makeText(this, getString(R.string.unable_to_open_navigation_contact_services), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Send alert to the chosen hospital with emergency details, rescuer info, and senior info
     */
    private void sendHospitalAlert() {
        Log.d(TAG, "🚨 Sending hospital alert to: " + hospitalName);
        
        if (hospitalName == null || hospitalName.isEmpty()) {
            Log.w(TAG, "⚠️ Cannot send hospital alert - hospital name not available");
            return;
        }
        
        if (emergencyId == null || emergencyId.isEmpty()) {
            Log.w(TAG, "⚠️ Cannot send hospital alert - emergency ID not available");
            return;
        }
        
        // Get rescuer details
        getRescuerDetailsForHospitalAlert();
    }
    
    /**
     * Get rescuer details and send hospital alert
     */
    private void getRescuerDetailsForHospitalAlert() {
        Log.d(TAG, "🔍 Getting rescuer details for hospital alert...");
        
        if (rescuerId == null) {
            Log.w(TAG, "⚠️ Rescuer ID is null, using default values");
            sendHospitalAlertWithDetails("Unknown Rescuer", "Not available", "Emergency Response Team");
            return;
        }
        
        // Get rescuer details from database
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .get()
                .addOnSuccessListener(rescuerDoc -> {
                    if (rescuerDoc.exists()) {
                        String rescuerName = rescuerDoc.getString("rescuegroup");
                        String rescuerPhone = rescuerDoc.getString("mobileNumber");
                        String rescuerTeam = rescuerDoc.getString("rescuegroup");
                        
                        // Use fallback values if data is missing
                        if (rescuerName == null || rescuerName.isEmpty()) {
                            rescuerName = "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length()));
                        }
                        if (rescuerPhone == null || rescuerPhone.isEmpty()) {
                            rescuerPhone = "Not available";
                        }
                        if (rescuerTeam == null || rescuerTeam.isEmpty()) {
                            rescuerTeam = "Emergency Response Team";
                        }
                        
                        Log.d(TAG, "✅ Rescuer details loaded: " + rescuerName + " | " + rescuerPhone + " | " + rescuerTeam);
                        sendHospitalAlertWithDetails(rescuerName, rescuerPhone, rescuerTeam);
                    } else {
                        Log.w(TAG, "⚠️ Rescuer document not found, using default values");
                        sendHospitalAlertWithDetails("Unknown Rescuer", "Not available", "Emergency Response Team");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading rescuer details: " + e.getMessage());
                    sendHospitalAlertWithDetails("Unknown Rescuer", "Not available", "Emergency Response Team");
                });
    }
    
    /**
     * Send hospital alert with complete details
     */
    private void sendHospitalAlertWithDetails(String rescuerName, String rescuerPhone, String rescuerTeam) {
        Log.d(TAG, "📤 Sending hospital alert with details...");
        Log.d(TAG, "🏥 Hospital: " + hospitalName);
        Log.d(TAG, "👨‍⚕️ Rescuer: " + rescuerName + " (" + rescuerPhone + ")");
        Log.d(TAG, "👴 Senior: " + seniorName + " (" + seniorPhone + ")");
        Log.d(TAG, "🚨 Emergency ID: " + emergencyId);
        
        // Calculate estimated arrival time to hospital
        double estimatedArrivalMinutes = 0.0;
        if (rescuerLat != 0.0 && rescuerLng != 0.0 && hospitalLat != 0.0 && hospitalLng != 0.0) {
            double distanceToHospital = calculateDistance(rescuerLat, rescuerLng, hospitalLat, hospitalLng);
            estimatedArrivalMinutes = (distanceToHospital / 30.0) * 60; // Assuming 30 km/h average speed
        }
        
        // Create hospital alert data
        Map<String, Object> hospitalAlert = new HashMap<>();
        hospitalAlert.put("type", "EMERGENCY_INCOMING");
        hospitalAlert.put("title", "🚨 Emergency Patient Incoming");
        hospitalAlert.put("message", "Emergency patient " + seniorName + " is being transported to your facility by " + rescuerName);
        hospitalAlert.put("emergencyId", emergencyId);
        hospitalAlert.put("timestamp", System.currentTimeMillis());
        hospitalAlert.put("isRead", false);
        hospitalAlert.put("isActive", true);
        hospitalAlert.put("priority", "HIGH");
        
        // Senior details
        hospitalAlert.put("seniorName", seniorName != null ? seniorName : "Unknown Senior");
        hospitalAlert.put("seniorPhone", seniorPhone != null ? seniorPhone : "Not available");
        hospitalAlert.put("seniorAddress", locationAddress != null ? locationAddress : "Address not available");
        hospitalAlert.put("seniorLat", seniorLat);
        hospitalAlert.put("seniorLng", seniorLng);
        
        // Rescuer details
        hospitalAlert.put("rescuerId", rescuerId);
        hospitalAlert.put("rescuerName", rescuerName);
        hospitalAlert.put("rescuerPhone", rescuerPhone);
        hospitalAlert.put("rescuerTeam", rescuerTeam);
        hospitalAlert.put("rescuerLat", rescuerLat);
        hospitalAlert.put("rescuerLng", rescuerLng);
        
        // Hospital details
        hospitalAlert.put("hospitalName", hospitalName);
        hospitalAlert.put("hospitalAddress", hospitalAddress != null ? hospitalAddress : "Address not available");
        hospitalAlert.put("hospitalLat", hospitalLat);
        hospitalAlert.put("hospitalLng", hospitalLng);
        
        // Emergency details
        hospitalAlert.put("assignmentTime", assignmentTime);
        hospitalAlert.put("estimatedArrivalMinutes", estimatedArrivalMinutes);
        hospitalAlert.put("estimatedArrivalTime", System.currentTimeMillis() + (long)(estimatedArrivalMinutes * 60 * 1000));
        
        // Additional context
        hospitalAlert.put("emergencyType", "Medical Emergency");
        hospitalAlert.put("transportStatus", "In Transit");
        hospitalAlert.put("requiresImmediateAttention", true);
        
        // Send alert to hospital
        sendAlertToHospital(hospitalAlert);
    }
    
    /**
     * Send alert to the specific hospital
     */
    private void sendAlertToHospital(Map<String, Object> alertData) {
        Log.d(TAG, "📤 Sending alert to hospital: " + hospitalName);
        Log.d(TAG, "📋 Alert data: " + alertData.toString());
        
        // First, find the hospital document ID
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .whereEqualTo("hospitalName", hospitalName)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "🔍 Hospital query result: " + querySnapshot.size() + " hospitals found");
                    if (!querySnapshot.isEmpty()) {
                        // Get the first matching hospital document
                        DocumentSnapshot hospitalDoc = querySnapshot.getDocuments().get(0);
                        String hospitalId = hospitalDoc.getId();
                        
                        Log.d(TAG, "🏥 Found hospital document ID: " + hospitalId);
                        Log.d(TAG, "🏥 Hospital document data: " + hospitalDoc.getData());
                        
                        // Send alert to hospital's notifications collection
                        db.collection("Sagip")
                                .document("users")
                                .collection("hospital")
                                .document(hospitalId)
                                .collection("notifications")
                                .add(alertData)
                                .addOnSuccessListener(documentReference -> {
                                    Log.d(TAG, "✅ Hospital alert sent successfully!");
                                    Log.d(TAG, "🏥 Hospital: " + hospitalName);
                                    Log.d(TAG, "📱 Alert ID: " + documentReference.getId());
                                    Log.d(TAG, "👴 Senior: " + seniorName);
                                    Log.d(TAG, "👨‍⚕️ Rescuer: " + alertData.get("rescuerName"));
                                    
                                    // Show success message to rescuer
                                    Toast.makeText(this, String.format(getString(R.string.hospital_notified_format), hospitalName), Toast.LENGTH_LONG).show();
                                    
                                    // Also send a push notification to hospital if they have FCM token
                                    sendPushNotificationToHospital(hospitalDoc, alertData);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to send hospital alert: " + e.getMessage());
                                    Toast.makeText(this, getString(R.string.failed_to_notify_hospital), Toast.LENGTH_LONG).show();
                                });
                    } else {
                        Log.w(TAG, "⚠️ Hospital not found in database: " + hospitalName);
                        Toast.makeText(this, getString(R.string.hospital_not_found_in_system), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error finding hospital: " + e.getMessage());
                    Toast.makeText(this, getString(R.string.error_notifying_hospital), Toast.LENGTH_LONG).show();
                });
    }
    
    /**
     * Send push notification to hospital's device
     */
    private void sendPushNotificationToHospital(DocumentSnapshot hospitalDoc, Map<String, Object> alertData) {
        String fcmToken = hospitalDoc.getString("fcmToken");
        if (fcmToken != null && !fcmToken.isEmpty()) {
            Log.d(TAG, "📱 Sending push notification to hospital FCM token: " + fcmToken);
            
            // Create push notification payload
            Map<String, Object> pushNotification = new HashMap<>();
            pushNotification.put("title", "🚨 Emergency Patient Incoming");
            pushNotification.put("body", "Patient " + seniorName + " is being transported by " + alertData.get("rescuerName"));
            pushNotification.put("type", "EMERGENCY_INCOMING");
            pushNotification.put("emergencyId", emergencyId);
            pushNotification.put("hospitalName", hospitalName);
            pushNotification.put("seniorName", seniorName);
            pushNotification.put("rescuerName", alertData.get("rescuerName"));
            pushNotification.put("rescuerPhone", alertData.get("rescuerPhone"));
            pushNotification.put("estimatedArrivalMinutes", alertData.get("estimatedArrivalMinutes"));
            pushNotification.put("timestamp", System.currentTimeMillis());
            
            // Log the push notification data (in real implementation, this would be sent via FCM)
            Log.d(TAG, "📱 Push Notification Data: " + pushNotification.toString());
            Log.d(TAG, "📱 FCM Token: " + fcmToken);
            
            // TODO: Implement actual FCM sending via Firebase Admin SDK on server side
            // This would typically be done through a Cloud Function or your backend server
        } else {
            Log.w(TAG, "⚠️ Hospital FCM token not found: " + hospitalName);
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
                        
                        // Log rescuer location (tvRescuerLocation no longer exists in layout)
                        Log.d(TAG, "Rescuer Location: " + String.format(Locale.getDefault(), 
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
    
    private void startHospitalDataStatusUpdates() {
        // Check hospital data status every 10 seconds
        new android.os.Handler().postDelayed(() -> {
            updateHospitalButtonState();
            startHospitalDataStatusUpdates(); // Recursive call for continuous updates
        }, 10000);
    }
    
    private void updateHospitalButtonState() {
        if (hospitalLat != 0.0 && hospitalLng != 0.0) {
            if (!btnNavigateHospital.isEnabled()) {
                btnNavigateHospital.setEnabled(true);
                Log.d(TAG, "✅ Hospital navigation button enabled - coordinates available");
            }
        } else if (hospitalAddress != null && !hospitalAddress.isEmpty()) {
            if (!btnNavigateHospital.isEnabled()) {
                btnNavigateHospital.setEnabled(true);
                Log.d(TAG, "✅ Hospital navigation button enabled - address available");
            }
        } else {
            if (btnNavigateHospital.isEnabled()) {
                btnNavigateHospital.setEnabled(false);
                Log.w(TAG, "⚠️ Hospital navigation button disabled - no location data");
            }
        }
    }
    
    private void callSenior() {
        if (seniorPhone != null && !seniorPhone.isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + seniorPhone));
            startActivity(callIntent);
        } else {
            Toast.makeText(this, getString(R.string.senior_phone_not_available), Toast.LENGTH_SHORT).show();
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
    
    private void markDone() {
        Log.d(TAG, "🔍 markDone() called - UPDATED VERSION");
        Log.d(TAG, "🔍 emergencyId in markDone: " + emergencyId);
        
        // Update status to done
        tvStatus.setText("✅ DONE");
        btnMarkDone.setEnabled(false);
        btnMarkDone.setText("DONE");
        
        // Clear rescuer's assignment status - they can now receive new alerts
        clearRescuerAssignmentStatus();
        
        // Update database
        updateEmergencyStatus("done");
        
        // Save SOS details to completedRescue collection
        saveRescueCompletedDetails();
        
        Toast.makeText(this, "Status updated: Emergency response completed", Toast.LENGTH_LONG).show();
        
        // Navigate to rescuer dashboard after completion
        navigateToRescuerDashboard();
    }
    
    /**
     * Clear rescuer's assignment status so they can receive new emergency alerts
     */
    private void clearRescuerAssignmentStatus() {
        if (rescuerId == null || rescuerId.isEmpty()) {
            Log.w(TAG, "⚠️ Cannot clear assignment status - rescuer ID is null");
            return;
        }
        
        Log.d(TAG, "✅ Clearing assignment status for rescuer: " + rescuerId);
        Log.d(TAG, "✅ Rescuer will now be able to receive new emergency alerts");
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("onAssignment", false);
        updates.put("onAssignmentUpdatedAt", System.currentTimeMillis());
        updates.put("lastCompletedAt", System.currentTimeMillis());
        
        db.collection("Sagip")
                .document("users")
                .collection("rescuer")
                .document(rescuerId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Assignment status cleared successfully");
                    Log.d(TAG, "✅ Rescuer " + rescuerId + " is now available for new emergencies");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to clear assignment status: " + e.getMessage());
                });
    }
    
    private void updateEmergencyStatus(String status) {
        // Update the emergency status in database
        if (emergencyId != null) {
            Log.d(TAG, "🔍 updateEmergencyStatus called with status: " + status);
            Log.d(TAG, "🔍 emergencyId value in updateEmergencyStatus: " + emergencyId);
            Log.d(TAG, "Updating emergency status for ID: " + emergencyId);
            db.collection("Sagip")
                    .document("emergencyRequests")
                    .collection("activeRequests")
                    .document(emergencyId)
                    .update("status", status, "doneAt", System.currentTimeMillis())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Emergency status updated to: " + status);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating emergency status: " + e.getMessage());
                    });
        }
    }
    
    private void saveRescueCompletedDetails() {
        if (emergencyId == null) {
            Log.w(TAG, "Emergency ID is null, cannot save rescue completed details");
            return;
        }
        
        Log.d(TAG, "Starting to save rescue completed details for emergencyId: " + emergencyId);
        Log.d(TAG, "Senior name: " + seniorName + ", Phone: " + seniorPhone + ", Address: " + locationAddress);
        
        // Get current rescuer information
        String rescuerName = getCurrentRescuerName();
        String rescuerTeam = getCurrentRescuerTeam();
        long completionTime = System.currentTimeMillis();
        
        // Create rescue completed document
        Map<String, Object> rescueCompletedData = new HashMap<>();
        rescueCompletedData.put("emergencyId", emergencyId);
        rescueCompletedData.put("seniorName", seniorName);
        rescueCompletedData.put("seniorPhone", seniorPhone);
        rescueCompletedData.put("locationAddress", locationAddress);
        rescueCompletedData.put("seniorLat", seniorLat);
        rescueCompletedData.put("seniorLng", seniorLng);
        rescueCompletedData.put("rescuerId", rescuerId);
        rescueCompletedData.put("rescuerName", rescuerName);
        rescueCompletedData.put("rescuerTeam", rescuerTeam);
        rescueCompletedData.put("assignmentTime", assignmentTime);
        rescueCompletedData.put("completionTime", completionTime);
        rescueCompletedData.put("responseDuration", completionTime - assignmentTime);
        rescueCompletedData.put("status", "RescueCompleted");
        rescueCompletedData.put("timestamp", completionTime);
        
        // Add hospital information if available
        if (hospitalName != null && !hospitalName.isEmpty()) {
            rescueCompletedData.put("hospitalName", hospitalName);
            rescueCompletedData.put("hospitalAddress", hospitalAddress);
            rescueCompletedData.put("hospitalLat", hospitalLat);
            rescueCompletedData.put("hospitalLng", hospitalLng);
        }
        
        Log.d(TAG, "Saving rescue completed data: " + rescueCompletedData.toString());
        
        // Save to completedRescue collection
        db.collection("Sagip")
                .document("completedRescue")
                .collection("rescues")
                .add(rescueCompletedData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Rescue completed details saved with ID: " + documentReference.getId());
                    Toast.makeText(this, "Rescue details saved successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving rescue completed details: " + e.getMessage());
                    Toast.makeText(this, "Error saving rescue details", Toast.LENGTH_SHORT).show();
                });
    }
    
    private String getCurrentRescuerName() {
        // Get rescuer name from Firebase Auth or database
        if (mAuth.getCurrentUser() != null) {
            return mAuth.getCurrentUser().getDisplayName() != null ? 
                   mAuth.getCurrentUser().getDisplayName() : "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length()));
        }
        return "Unknown Rescuer";
    }
    
    private String getCurrentRescuerTeam() {
        // This would typically be fetched from the rescuer's profile in the database
        // For now, return a default value
        return "Emergency Response Team";
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
        Log.d(TAG, "🚨🚨🚨 EmergencyAssignmentActivity DESTROYED 🚨🚨🚨");
        // Update status to in-progress when leaving
        updateEmergencyStatus("in_progress");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🚨🚨🚨 EmergencyAssignmentActivity RESUMED 🚨🚨🚨");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "🚨🚨🚨 EmergencyAssignmentActivity PAUSED 🚨🚨🚨");
    }
    
    private void sendHospitalDetailsNotificationToSenior(DocumentSnapshot hospitalDoc, double distance) {
        if (emergencyId == null || emergencyId.isEmpty()) {
            Log.w(TAG, "⚠️ Cannot send hospital notification - no emergency ID");
            return;
        }
        
        Log.d(TAG, "📤 Sending hospital details notification to senior for emergency: " + emergencyId);
        
        // Get hospital details from the document
        String hospitalId = hospitalDoc.getId();
        String hospitalName = hospitalDoc.getString("hospitalName");
        String hospitalAddress = hospitalDoc.getString("hospitalAddress");
        String hospitalPhone = hospitalDoc.getString("hospitalPhone");
        String erStatus = hospitalDoc.getString("erStatus");
        
        // Get rescuer details
        final String[] rescuerName = {"Rescuer"}; // Default fallback
        final String[] rescuerPhone = {"Not available"};
        final String[] rescuerTeam = {"Emergency Response Team"};
        
        // Try to get rescuer details from database
        if (rescuerId != null) {
            db.collection("Sagip")
                    .document("users")
                    .collection("rescuer")
                    .document(rescuerId)
                    .get()
                    .addOnSuccessListener(rescuerDoc -> {
                        if (rescuerDoc.exists()) {
                            rescuerName[0] = rescuerDoc.getString("rescuegroup");
                            rescuerPhone[0] = rescuerDoc.getString("mobileNumber");
                            rescuerTeam[0] = rescuerDoc.getString("rescuegroup");
                            
                            if (rescuerName[0] == null || rescuerName[0].isEmpty()) {
                                rescuerName[0] = "Rescuer " + rescuerId.substring(0, Math.min(8, rescuerId.length()));
                            }
                            if (rescuerPhone[0] == null || rescuerPhone[0].isEmpty()) {
                                rescuerPhone[0] = "Not available";
                            }
                            if (rescuerTeam[0] == null || rescuerTeam[0].isEmpty()) {
                                rescuerTeam[0] = "Emergency Response Team";
                            }
                        }
                        
                        // Create notification with complete details
                        createHospitalDetailsNotification(hospitalId, hospitalName, hospitalAddress, hospitalPhone, erStatus, distance, rescuerName[0], rescuerPhone[0], rescuerTeam[0]);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Error loading rescuer details for notification: " + e.getMessage());
                        // Create notification with default rescuer details
                        createHospitalDetailsNotification(hospitalId, hospitalName, hospitalAddress, hospitalPhone, erStatus, distance, rescuerName[0], rescuerPhone[0], rescuerTeam[0]);
                    });
        } else {
            // Create notification with default rescuer details
            createHospitalDetailsNotification(hospitalId, hospitalName, hospitalAddress, hospitalPhone, erStatus, distance, rescuerName[0], rescuerPhone[0], rescuerTeam[0]);
        }
    }
    
    private void createHospitalDetailsNotification(String hospitalId, String hospitalName, String hospitalAddress, 
                                                 String hospitalPhone, String erStatus, double distance,
                                                 String rescuerName, String rescuerPhone, String rescuerTeam) {
        
        // Get senior UID from emergency document
        db.collection("Sagip")
                .document("emergencyRequests")
                .collection("activeRequests")
                .document(emergencyId)
                .get()
                .addOnSuccessListener(emergencyDoc -> {
                    if (!emergencyDoc.exists()) {
                        Log.w(TAG, "⚠️ Emergency document not found for notification: " + emergencyId);
                        return;
                    }
                    
                    String seniorUid = emergencyDoc.getString("seniorUid");
                    if (seniorUid == null || seniorUid.isEmpty()) {
                        Log.w(TAG, "⚠️ Senior UID not found in emergency document");
                        return;
                    }
                    
                    // Create notification data
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "HOSPITAL_DETAILS_UPDATE");
                    notification.put("title", "🏥 Hospital Details Confirmed");
                    notification.put("message", "Hospital " + hospitalName + " has been selected for your emergency. Distance: " + String.format("%.2f km", distance));
                    notification.put("emergencyId", emergencyId);
                    notification.put("hospitalId", hospitalId);
                    notification.put("hospitalName", hospitalName != null ? hospitalName : "Hospital");
                    notification.put("hospitalAddress", hospitalAddress != null ? hospitalAddress : "Address not available");
                    notification.put("hospitalPhone", hospitalPhone != null ? hospitalPhone : "Contact hospital directly");
                    notification.put("erStatus", erStatus != null ? erStatus : "Unknown");
                    notification.put("distance", distance);
                    notification.put("rescuerName", rescuerName);
                    notification.put("rescuerPhone", rescuerPhone);
                    notification.put("rescuerTeam", rescuerTeam);
                    notification.put("timestamp", System.currentTimeMillis());
                    notification.put("isRead", false);
                    notification.put("isActive", true);
                    
                    // Send notification to senior
                    Log.d(TAG, "📤 Sending hospital details notification to senior: " + seniorUid);
                    
                    db.collection("Sagip")
                            .document("users")
                            .collection("seniors")
                            .document(seniorUid)
                            .collection("notifications")
                            .add(notification)
                            .addOnSuccessListener(documentReference -> {
                                Log.d(TAG, "✅ Hospital details notification sent to senior: " + seniorUid);
                                Log.d(TAG, "🏥 Hospital: " + hospitalName + " at " + hospitalAddress);
                                Log.d(TAG, "📱 Notification ID: " + documentReference.getId());
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to send hospital details notification: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading emergency for notification: " + e.getMessage());
                });
    }
    
    private void navigateToRescuerDashboard() {
        Log.d(TAG, "🏠 Navigating to rescuer dashboard after emergency completion");
        
        try {
            Intent dashboardIntent = new Intent(this, Rescuer_Dashboard.class);
            dashboardIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(dashboardIntent);
            finish();
            Log.d(TAG, "✅ Successfully navigated to rescuer dashboard");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error navigating to rescuer dashboard: " + e.getMessage());
            Toast.makeText(this, "Error navigating to dashboard", Toast.LENGTH_SHORT).show();
        }
    }
    
    
    

    
    
    /**
     * Get current rescuer's phone number
     */
    private String getCurrentRescuerPhone() {
        // Try to get from Firebase Auth first
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.getPhoneNumber() != null) {
            return currentUser.getPhoneNumber();
        }
        
        // Fallback: return a default message
        return "Contact emergency services";
    }
    
    /**
     * Send push notification to senior's device
     */
    private void sendPushNotificationToSenior(String seniorUid, String rescuerName, String rescuerTeam) {
        Log.d(TAG, "📱 Sending push notification to senior: " + seniorUid);
        
        // Get senior's FCM token
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .document(seniorUid)
                .get()
                .addOnSuccessListener(seniorDoc -> {
                    if (seniorDoc.exists()) {
                        String fcmToken = seniorDoc.getString("fcmToken");
                        if (fcmToken != null && !fcmToken.isEmpty()) {
                            // Send push notification via Firebase Cloud Messaging
                            sendFCMNotification(fcmToken, rescuerName, rescuerTeam);
                        } else {
                            Log.w(TAG, "⚠️ Senior FCM token not found: " + seniorUid);
                        }
                    } else {
                        Log.w(TAG, "⚠️ Senior document not found: " + seniorUid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error getting senior FCM token: " + e.getMessage());
                });
    }
    
    /**
     * Send FCM notification to senior's device
     */
    private void sendFCMNotification(String fcmToken, String rescuerName, String rescuerTeam) {
        Log.d(TAG, "📤 Sending FCM notification to token: " + fcmToken);
        
        // Create notification payload
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("title", "🚑 Rescuer Has Arrived!");
        notificationData.put("body", "Your rescuer " + rescuerName + " from " + rescuerTeam + " has arrived at your location.");
        notificationData.put("type", "RESCUER_ARRIVED");
        notificationData.put("emergencyId", emergencyId);
        notificationData.put("rescuerName", rescuerName);
        notificationData.put("rescuerTeam", rescuerTeam);
        notificationData.put("timestamp", System.currentTimeMillis());
        
        // Send to Firebase Cloud Messaging
        // Note: In a real implementation, you would use Firebase Admin SDK on your server
        // For now, we'll log the notification data
        Log.d(TAG, "📱 FCM Notification Data: " + notificationData.toString());
        Log.d(TAG, "📱 FCM Token: " + fcmToken);
        
        // TODO: Implement actual FCM sending via Firebase Admin SDK on server side
        // This would typically be done through a Cloud Function or your backend server
    }
    
    
    
}
