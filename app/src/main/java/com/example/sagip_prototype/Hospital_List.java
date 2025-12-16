package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Hospital_List extends AppCompatActivity implements HospitalAdapter.OnHospitalClickListener {

    private static final String TAG = "Hospital_List";
    
    private RecyclerView hospitalsRecyclerView;
    private HospitalAdapter hospitalAdapter;
    private List<Hospital> hospitalsList;
    private LinearLayout noHospitalsLayout;
    private TextView noHospitalsText;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hospital_list);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize views
        initializeViews();
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Load hospitals
        loadHospitals();
        
        // Setup bottom navigation
        setupBottomNavigation();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the list when returning to this screen to reflect completed rescues
        Log.d(TAG, "onResume - refreshing incoming patient list");
        loadHospitals();
    }

    private void initializeViews() {
        hospitalsRecyclerView = findViewById(R.id.hospitalsRecyclerView);
        noHospitalsLayout = findViewById(R.id.noHospitalsLayout);
        noHospitalsText = findViewById(R.id.noHospitalsText);
    }

    private void setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView");
        
        // Initialize the hospitals list
        hospitalsList = new ArrayList<>();
        
        // Create adapter
        hospitalAdapter = new HospitalAdapter(hospitalsList, this);
        
        // Setup RecyclerView
        hospitalsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        hospitalsRecyclerView.setAdapter(hospitalAdapter);
    }

    private void loadHospitals() {
        Log.d(TAG, "Loading hospitals from Firestore");

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user; cannot load notifications");
            showBlankPage();
            return;
        }

        String userId = currentUser.getUid();

        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("type", "EMERGENCY_INCOMING")
                .whereEqualTo("isRead", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Successfully loaded notifications, count: " + queryDocumentSnapshots.size());

                    List<Hospital> emergencyHospitals = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        // Filter out completed rescues - check status field
                        String status = document.getString("status");
                        Boolean isActive = document.getBoolean("isActive");
                        
                        // Skip notifications that are completed or inactive
                        if ("completed".equals(status)) {
                            Log.d(TAG, "Skipping completed notification: " + document.getId());
                            continue;
                        }
                        if (isActive != null && !isActive) {
                            Log.d(TAG, "Skipping inactive notification: " + document.getId());
                            continue;
                        }
                        
                        Hospital hospital = createHospitalFromNotificationDocument(document);
                        emergencyHospitals.add(hospital);
                    }

                    if (emergencyHospitals.isEmpty()) {
                        showNoHospitalsMessage();
                    } else {
                        displayHospitals(emergencyHospitals);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading notifications: " + e.getMessage(), e);
                    showNoHospitalsMessage();
                    Toast.makeText(this, getString(R.string.failed_to_load_hospitals), Toast.LENGTH_SHORT).show();
                });
    }

    private Hospital createHospitalFromNotificationDocument(QueryDocumentSnapshot document) {
        Hospital hospital = new Hospital();
        hospital.setDocumentId(document.getId());

        hospital.setSeniorName(document.getString("seniorName"));
        hospital.setSeniorPhone(document.getString("seniorPhone"));
        hospital.setSeniorAddress(document.getString("seniorAddress"));
        hospital.setRescuerName(document.getString("rescuerName"));
        hospital.setRescuerPhone(document.getString("rescuerPhone"));
        hospital.setEmergencyId(document.getString("emergencyId"));

        Object timestampObj = document.get("timestamp");
        if (timestampObj instanceof Number) {
            hospital.setEmergencyTimestamp(((Number) timestampObj).longValue());
        }

        Object estimatedArrivalObj = document.get("estimatedArrivalMinutes");
        if (estimatedArrivalObj instanceof Number) {
            hospital.setEstimatedArrivalMinutes(((Number) estimatedArrivalObj).doubleValue());
        }

        hospital.setHasIncomingEmergency(true);
        return hospital;
    }

    private void displayHospitals(List<Hospital> hospitals) {
        Log.d(TAG, "Displaying " + hospitals.size() + " hospitals");
        
        // Sort by timestamp (newest first = first in queue)
        Collections.sort(hospitals, (h1, h2) -> {
            Long t1 = h1.getEmergencyTimestamp();
            Long t2 = h2.getEmergencyTimestamp();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1); // Descending order (newest first)
        });
        
        Log.d(TAG, "Sorted hospitals by timestamp (newest first for queue numbering)");
        
        // Hide no hospitals message
        noHospitalsLayout.setVisibility(View.GONE);
        hospitalsRecyclerView.setVisibility(View.VISIBLE);
        
        // Update adapter
        hospitalAdapter.updateHospitals(hospitals);
    }

    private void showNoHospitalsMessage() {
        Log.d(TAG, "No hospitals found, showing blank page");
        
        // Hide everything - completely blank page (no message)
        hospitalsRecyclerView.setVisibility(View.GONE);
        noHospitalsLayout.setVisibility(View.GONE);
    }
    
    private void showBlankPage() {
        Log.d(TAG, "No emergency cases, showing blank page");
        
        // Hide everything - completely blank page
        hospitalsRecyclerView.setVisibility(View.GONE);
        noHospitalsLayout.setVisibility(View.GONE);
    }

    @Override
    public void onHospitalClick(Hospital hospital) {
        Log.d(TAG, "Hospital clicked: " + hospital.getHospitalName());
        
        // Navigate to HospitalEmergencyDetailActivity with senior and rescuer info
        Intent intent = new Intent(this, HospitalEmergencyDetailActivity.class);
        
        // Pass senior information
        intent.putExtra("senior_name", hospital.getSeniorName());
        intent.putExtra("senior_phone", hospital.getSeniorPhone());
        intent.putExtra("senior_address", hospital.getSeniorAddress());
        
        // Pass rescuer information
        intent.putExtra("rescuer_name", hospital.getRescuerName());
        intent.putExtra("rescuer_phone", hospital.getRescuerPhone());
        
        // Pass ETA if available
        if (hospital.getEstimatedArrivalMinutes() != null) {
            intent.putExtra("estimated_arrival_minutes", hospital.getEstimatedArrivalMinutes());
        }
        
        startActivity(intent);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.hospital_list);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.hospital_list) {
                return true;
            } else if (itemId == R.id.hospital_profile) {
                startActivity(new Intent(getApplicationContext(), Hospital_Profile.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.hospital_home) {
                startActivity(new Intent(getApplicationContext(), Hospital_Dashboard.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }
}