package com.example.sagip_prototype;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class Hospital_Status_Edit extends AppCompatActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private String userId;

    // UI Elements
    private EditText etTotalBeds, etAvailableBeds, etDoctorsAvailable;
    private Button btnSaveStatus, btnCancel;
    private TextView tvHospitalName;
    private TextView tvAutoStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hospital_status_edit);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Initialize UI elements
        initializeViews();
        setupClickListeners();
        loadCurrentStatus();
    }

    private void initializeViews() {
        tvHospitalName = findViewById(R.id.tvHospitalName);
        etTotalBeds = findViewById(R.id.etTotalBeds);
        etAvailableBeds = findViewById(R.id.etAvailableBeds);
        etDoctorsAvailable = findViewById(R.id.etDoctorsAvailable);
        tvAutoStatus = findViewById(R.id.tvAutoStatus);
        btnSaveStatus = findViewById(R.id.btnSaveStatus);
        btnCancel = findViewById(R.id.btnCancel);

        // Get user ID
        userId = sharedPreferences.getString(KEY_USER_ID, null);
        if (userId == null) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            }
        }
    }

    private void setupClickListeners() {
        btnSaveStatus.setOnClickListener(v -> saveHospitalStatus());
        btnCancel.setOnClickListener(v -> finish());

        // Update status when values change (but don't auto-fill available beds)
        etTotalBeds.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateAutoStatus();
            }
        });

        etAvailableBeds.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateAutoStatus();
            }
        });

        etDoctorsAvailable.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateAutoStatus();
            }
        });
    }



    private void updateAutoStatus() {
        try {
            String totalBedsStr = etTotalBeds.getText().toString();
            String availableBedsStr = etAvailableBeds.getText().toString();
            String doctorsStr = etDoctorsAvailable.getText().toString();

            if (!totalBedsStr.isEmpty() && !availableBedsStr.isEmpty() && !doctorsStr.isEmpty()) {
                int totalBeds = Integer.parseInt(totalBedsStr);
                int availableBeds = Integer.parseInt(availableBedsStr);
                int doctors = Integer.parseInt(doctorsStr);

                String status = calculateAutoStatus(totalBeds, availableBeds, doctors);
                if (!status.equals("unknown")) {
                    String statusEmoji = getStatusEmoji(status);
                    int statusColor = getStatusColor(status);

                    String statusText = statusEmoji + " " + status.toUpperCase();
                    tvAutoStatus.setText(statusText);
                    tvAutoStatus.setTextColor(statusColor);
                } else {
                    tvAutoStatus.setText("⚪ Invalid data - check your inputs");
                    tvAutoStatus.setTextColor(0xFF9E9E9E);
                }
            } else {
                // Show placeholder when not all fields are filled
                tvAutoStatus.setText("⚪ Enter bed and doctor information");
                tvAutoStatus.setTextColor(0xFF9E9E9E);
            }
        } catch (NumberFormatException e) {
            tvAutoStatus.setText("⚪ Enter valid numbers");
            tvAutoStatus.setTextColor(0xFF9E9E9E);
        }
    }

    private String calculateAutoStatus(int totalBeds, int availableBeds, int doctors) {
        // Validate input
        if (totalBeds <= 0 || availableBeds < 0 || doctors <= 0) {
            return "unknown";
        }
        
        if (availableBeds > totalBeds) {
            return "unknown";
        }
        
        // Calculate capacity percentage
        double capacityPercentage = ((double) (totalBeds - availableBeds) / totalBeds) * 100;
        
        // Calculate beds per doctor ratio
        double bedsPerDoctor = (double) totalBeds / doctors;
        
        // Automatic status logic based on multiple factors
        if (capacityPercentage >= 90 || availableBeds == 0) {
            return "crowded"; // At or near capacity
        } else if (capacityPercentage >= 70 || bedsPerDoctor > 8 || doctors < 2) {
            return "busy"; // High capacity or insufficient staff
        } else if (capacityPercentage >= 50 || bedsPerDoctor > 6) {
            return "busy"; // Moderate capacity
        } else {
            return "available"; // Good capacity and staff ratio
        }
    }

    private String getStatusEmoji(String status) {
        switch (status.toLowerCase()) {
            case "available":
                return "🟢";
            case "busy":
                return "🟡";
            case "crowded":
                return "🔴";
            default:
                return "⚪";
        }
    }

    private int getStatusColor(String status) {
        switch (status.toLowerCase()) {
            case "available":
                return 0xFF4CAF50; // Green
            case "busy":
                return 0xFFFF9800; // Orange
            case "crowded":
                return 0xFFF44336; // Red
            default:
                return 0xFF9E9E9E; // Gray
        }
    }

    private void loadCurrentStatus() {
        if (userId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Load hospital name
                        String hospitalName = documentSnapshot.getString("hospitalName");
                        if (hospitalName != null) {
                            tvHospitalName.setText(hospitalName);
                        }

                        // Load current status
                        Long totalBeds = documentSnapshot.getLong("totalBeds");
                        Long availableBeds = documentSnapshot.getLong("availableBeds");
                        Long doctorsAvailable = documentSnapshot.getLong("doctorsAvailable");

                        if (totalBeds != null) etTotalBeds.setText(String.valueOf(totalBeds));
                        if (availableBeds != null) etAvailableBeds.setText(String.valueOf(availableBeds));
                        if (doctorsAvailable != null) etDoctorsAvailable.setText(String.valueOf(doctorsAvailable));

                        // Update auto status display
                        updateAutoStatus();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load current status: " + e.getMessage(), 
                                 Toast.LENGTH_SHORT).show();
                });
    }

    private void saveHospitalStatus() {
        if (userId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate input
        String totalBedsStr = etTotalBeds.getText().toString();
        String availableBedsStr = etAvailableBeds.getText().toString();
        String doctorsAvailableStr = etDoctorsAvailable.getText().toString();

        if (totalBedsStr.isEmpty() || availableBedsStr.isEmpty() || doctorsAvailableStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int totalBeds = Integer.parseInt(totalBedsStr);
            int availableBeds = Integer.parseInt(availableBedsStr);
            int doctorsAvailable = Integer.parseInt(doctorsAvailableStr);

            // Validate the data
            if (totalBeds <= 0) {
                Toast.makeText(this, "Total beds must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (availableBeds < 0) {
                Toast.makeText(this, "Available beds cannot be negative", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (availableBeds > totalBeds) {
                Toast.makeText(this, "Available beds cannot exceed total beds", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (doctorsAvailable <= 0) {
                Toast.makeText(this, "Doctors available must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            // Calculate automatic status
            String status = calculateAutoStatus(totalBeds, availableBeds, doctorsAvailable);
            
            // Calculate capacity percentage
            double capacityPercentage = ((double) (totalBeds - availableBeds) / totalBeds) * 100;

            // Create status data
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("totalBeds", totalBeds);
            statusData.put("availableBeds", availableBeds);
            statusData.put("doctorsAvailable", doctorsAvailable);
            statusData.put("erStatus", status);
            statusData.put("capacityPercentage", capacityPercentage);
            statusData.put("lastUpdated", Timestamp.now());
            
            // Also update the status in the main hospital document
            statusData.put("status", status);

            // Save to Firestore
            db.collection("Sagip")
                    .document("users")
                    .collection("hospital")
                    .document(userId)
                    .update(statusData)
                    .addOnSuccessListener(aVoid -> {
                        String statusMessage = "Hospital status updated successfully!\nStatus: " + 
                                             getStatusEmoji(status) + " " + status.toUpperCase();
                        Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update status: " + e.getMessage(), 
                                     Toast.LENGTH_SHORT).show();
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
        }
    }
}
