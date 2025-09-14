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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class Hospital_List extends AppCompatActivity implements HospitalAdapter.OnHospitalClickListener {

    private static final String TAG = "Hospital_List";
    
    private RecyclerView hospitalsRecyclerView;
    private HospitalAdapter hospitalAdapter;
    private List<Hospital> hospitalsList;
    private LinearLayout noHospitalsLayout;
    private TextView noHospitalsText;
    
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hospital_list);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        initializeViews();
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Load hospitals
        loadHospitals();
        
        // Setup bottom navigation
        setupBottomNavigation();
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
        
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Successfully loaded hospitals, count: " + queryDocumentSnapshots.size());
                    
                    List<Hospital> hospitals = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Hospital hospital = createHospitalFromDocument(document);
                        hospitals.add(hospital);
                    }
                    
                    if (hospitals.isEmpty()) {
                        showNoHospitalsMessage();
                    } else {
                        displayHospitals(hospitals);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading hospitals: " + e.getMessage(), e);
                    showNoHospitalsMessage();
                    Toast.makeText(this, "Failed to load hospitals", Toast.LENGTH_SHORT).show();
                });
    }

    private Hospital createHospitalFromDocument(QueryDocumentSnapshot document) {
        Hospital hospital = new Hospital();
        hospital.setDocumentId(document.getId());
        hospital.setHospitalName((String) document.get("hospitalName"));
        hospital.setContactNumber((String) document.get("contactNumber"));
        hospital.setEmail((String) document.get("email"));
        hospital.setAddress((String) document.get("address"));
        hospital.setStatus((String) document.get("status"));
        hospital.setUserType((String) document.get("userType"));
        hospital.setProfileImageUrl((String) document.get("profileImageUrl"));
        hospital.setEmergencyContact((String) document.get("emergencyContact"));
        hospital.setSpecialization((String) document.get("specialization"));
        
        // Handle bed capacity
        Object bedCapacityObj = document.get("bedCapacity");
        if (bedCapacityObj instanceof Number) {
            hospital.setBedCapacity(((Number) bedCapacityObj).intValue());
        }
        
        Object availableBedsObj = document.get("availableBeds");
        if (availableBedsObj instanceof Number) {
            hospital.setAvailableBeds(((Number) availableBedsObj).intValue());
        }
        
        // Handle emergency ready status
        Object emergencyReadyObj = document.get("isEmergencyReady");
        if (emergencyReadyObj instanceof Boolean) {
            hospital.setEmergencyReady((Boolean) emergencyReadyObj);
        }
        
        return hospital;
    }

    private void displayHospitals(List<Hospital> hospitals) {
        Log.d(TAG, "Displaying " + hospitals.size() + " hospitals");
        
        // Hide no hospitals message
        noHospitalsLayout.setVisibility(View.GONE);
        hospitalsRecyclerView.setVisibility(View.VISIBLE);
        
        // Update adapter
        hospitalAdapter.updateHospitals(hospitals);
    }

    private void showNoHospitalsMessage() {
        Log.d(TAG, "No hospitals found, showing message");
        
        // Show no hospitals message
        hospitalsRecyclerView.setVisibility(View.GONE);
        noHospitalsLayout.setVisibility(View.VISIBLE);
        noHospitalsText.setText("No hospitals found");
    }

    @Override
    public void onHospitalClick(Hospital hospital) {
        Log.d(TAG, "Hospital clicked: " + hospital.getHospitalName());
        
        // Show hospital details in a toast for now
        // In a real app, you might navigate to a hospital detail page
        String message = "Hospital: " + hospital.getHospitalName() + 
                        "\nStatus: " + hospital.getStatusDisplay() +
                        "\nBeds: " + hospital.getBedStatus();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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