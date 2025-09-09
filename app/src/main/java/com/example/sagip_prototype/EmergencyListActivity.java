package com.example.sagip_prototype;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class EmergencyListActivity extends AppCompatActivity {

    private static final String TAG = "EmergencyListActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FusedLocationProviderClient fusedLocationClient;
    
    private RecyclerView emergencyRecyclerView;
    private EmergencyAdapter emergencyAdapter;
    private List<EmergencyItem> emergencyList;
    private TextView noEmergenciesText;
    private Button refreshButton;
    private Button backButton;
    
    private double currentLat = 0.0;
    private double currentLong = 0.0;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_emergency_list);
        
        initializeComponents();
        setupRecyclerView();
        getCurrentLocation();
        loadEmergencies();
    }
    
    private void initializeComponents() {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        emergencyRecyclerView = findViewById(R.id.emergencyRecyclerView);
        noEmergenciesText = findViewById(R.id.noEmergenciesText);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);
        
        emergencyList = new ArrayList<>();
        
        // Get current user ID
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        }
        
        // Set up button listeners
        refreshButton.setOnClickListener(v -> loadEmergencies());
        backButton.setOnClickListener(v -> finish());
    }
    
    private void setupRecyclerView() {
        emergencyAdapter = new EmergencyAdapter(emergencyList, this::onEmergencyItemClick);
        emergencyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        emergencyRecyclerView.setAdapter(emergencyAdapter);
    }
    
    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                currentLat = location.getLatitude();
                                currentLong = location.getLongitude();
                                Log.d(TAG, "Current location: " + currentLat + ", " + currentLong);
                                loadEmergencies(); // Reload with location data
                            }
                        }
                    });
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }
    
    private void loadEmergencies() {
        Log.d(TAG, "Loading emergencies within 5km radius...");
        
        db.collection("Sagip")
                .document("emergencyNotifications")
                .collection("activeEmergencies")
                .whereEqualTo("isActive", true)
                .orderBy("timestamp", Query.Direction.ASCENDING) // FIFO order
                .get()
                .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        emergencyList.clear();
                        
                        for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                            String title = document.getString("title");
                            String message = document.getString("message");
                            String seniorName = document.getString("seniorName");
                            String seniorPhone = document.getString("seniorPhone");
                            String locationAddress = document.getString("locationAddress");
                            Double latitude = document.getDouble("latitude");
                            Double longitude = document.getDouble("longitude");
                            String helpRequestId = document.getString("helpRequestId");
                            String respondedBy = document.getString("respondedBy");
                            Long timestamp = document.getLong("timestamp");
                            
                            // Skip if already responded by current rescuer
                            if (respondedBy != null && respondedBy.equals(userId)) {
                                continue;
                            }
                            
                            // Check if within 5km radius
                            if (latitude != null && longitude != null) {
                                double distance = calculateDistance(currentLat, currentLong, latitude, longitude);
                                if (distance <= 5.0) { // 5km radius
                                    EmergencyItem emergency = new EmergencyItem(
                                            title, message, seniorName, seniorPhone,
                                            locationAddress, latitude, longitude,
                                            helpRequestId, document.getId(), 1, 
                                            emergencyList.size() + 1, distance, timestamp
                                    );
                                    emergencyList.add(emergency);
                                }
                            }
                        }
                        
                        updateUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading emergencies", e);
                    Toast.makeText(this, "Failed to load emergencies", Toast.LENGTH_SHORT).show();
                });
    }
    
    private void updateUI() {
        if (emergencyList.isEmpty()) {
            emergencyRecyclerView.setVisibility(View.GONE);
            noEmergenciesText.setVisibility(View.VISIBLE);
            noEmergenciesText.setText("No active emergencies within 5km radius");
        } else {
            emergencyRecyclerView.setVisibility(View.VISIBLE);
            noEmergenciesText.setVisibility(View.GONE);
            emergencyAdapter.notifyDataSetChanged();
        }
    }
    
    private void onEmergencyItemClick(EmergencyItem emergency) {
        // Open emergency details or navigation
        Intent intent = new Intent(this, RescuerNavigationActivity.class);
        intent.putExtra("helpRequestId", emergency.helpRequestId);
        intent.putExtra("seniorName", emergency.seniorName);
        intent.putExtra("seniorPhone", emergency.seniorPhone);
        intent.putExtra("latitude", emergency.latitude);
        intent.putExtra("longitude", emergency.longitude);
        intent.putExtra("locationAddress", emergency.locationAddress);
        startActivity(intent);
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
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, "Location permission required to show nearby emergencies", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    // Emergency item class for the list
    public static class EmergencyItem {
        public String title;
        public String message;
        public String seniorName;
        public String seniorPhone;
        public String locationAddress;
        public Double latitude;
        public Double longitude;
        public String helpRequestId;
        public String emergencyId;
        public int priority;
        public int queuePosition;
        public double distance;
        public Long timestamp;
        
        public EmergencyItem(String title, String message, String seniorName, String seniorPhone,
                           String locationAddress, Double latitude, Double longitude,
                           String helpRequestId, String emergencyId, int priority, 
                           int queuePosition, double distance, Long timestamp) {
            this.title = title;
            this.message = message;
            this.seniorName = seniorName;
            this.seniorPhone = seniorPhone;
            this.locationAddress = locationAddress;
            this.latitude = latitude;
            this.longitude = longitude;
            this.helpRequestId = helpRequestId;
            this.emergencyId = emergencyId;
            this.priority = priority;
            this.queuePosition = queuePosition;
            this.distance = distance;
            this.timestamp = timestamp;
        }
        
        public String getDistanceText() {
            if (distance < 1.0) {
                return String.format("%.0f m", distance * 1000);
            } else {
                return String.format("%.1f km", distance);
            }
        }
        
        public String getTimeAgo() {
            if (timestamp == null) return "Unknown";
            
            long now = System.currentTimeMillis();
            long diff = now - timestamp;
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            
            if (minutes < 1) {
                return "Just now";
            } else if (minutes < 60) {
                return minutes + " min ago";
            } else {
                return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            }
        }
    }
}
