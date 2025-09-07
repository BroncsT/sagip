package com.example.sagip_prototype;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
    private EditText etAvailableBeds, etDoctorsAvailable;
    private Button btnSaveStatus, btnCancel, btnRefreshTotals;
    private TextView tvAutoStatus;
    private TextView tvDatabaseTotalBeds, tvDatabaseTotalDoctors;
    
    // Database totals for calculation
    private int databaseTotalBeds = 0;
    private int databaseTotalDoctors = 0;
    
    // Handler for debouncing real-time updates
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

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
        loadDatabaseTotals();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handler to prevent memory leaks
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    private void initializeViews() {
        etAvailableBeds = findViewById(R.id.etAvailableBeds);
        etDoctorsAvailable = findViewById(R.id.etDoctorsAvailable);
        tvAutoStatus = findViewById(R.id.tvAutoStatus);
        tvDatabaseTotalBeds = findViewById(R.id.tvDatabaseTotalBeds);
        tvDatabaseTotalDoctors = findViewById(R.id.tvDatabaseTotalDoctors);
        btnSaveStatus = findViewById(R.id.btnSaveStatus);
        btnCancel = findViewById(R.id.btnCancel);
        btnRefreshTotals = findViewById(R.id.btnRefreshTotals);

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
        btnRefreshTotals.setOnClickListener(v -> loadDatabaseTotals());

        // Real-time status update as user types (with debouncing)
        etAvailableBeds.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel previous update if user is still typing
                if (updateRunnable != null) {
                    updateHandler.removeCallbacks(updateRunnable);
                }
                
                // Schedule new update with 300ms delay
                updateRunnable = () -> updateAutoStatusRealTime();
                updateHandler.postDelayed(updateRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etDoctorsAvailable.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel previous update if user is still typing
                if (updateRunnable != null) {
                    updateHandler.removeCallbacks(updateRunnable);
                }
                
                // Schedule new update with 300ms delay
                updateRunnable = () -> updateAutoStatusRealTime();
                updateHandler.postDelayed(updateRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Keep focus change listeners as backup
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



    private void updateAutoStatusRealTime() {
        try {
            String availableBedsStr = etAvailableBeds.getText().toString().trim();
            String doctorsStr = etDoctorsAvailable.getText().toString().trim();

            Log.d("Hospital_Status_Edit", "Real-time update - Available beds: '" + availableBedsStr + "', Doctors: '" + doctorsStr + "'");
            Log.d("Hospital_Status_Edit", "Database totals - Beds: " + databaseTotalBeds + ", Doctors: " + databaseTotalDoctors);

            // Check if both fields have values and database totals are loaded
            if (!availableBedsStr.isEmpty() && !doctorsStr.isEmpty() && databaseTotalBeds > 0 && databaseTotalDoctors > 0) {
                int availableBeds = Integer.parseInt(availableBedsStr);
                int availableDoctors = Integer.parseInt(doctorsStr);

                String status = calculateAutoStatus(availableBeds, availableDoctors);
                if (!status.equals("unknown")) {
                    String statusEmoji = getStatusEmoji(status);
                    int statusColor = getStatusColor(status);

                    String statusText = statusEmoji + " " + status.toUpperCase();
                    tvAutoStatus.setText(statusText);
                    tvAutoStatus.setTextColor(statusColor);
                    
                    Log.d("Hospital_Status_Edit", "Real-time status calculated: " + status);
                } else {
                    tvAutoStatus.setText("⚪ Invalid data - check your inputs");
                    tvAutoStatus.setTextColor(0xFF9E9E9E);
                }
            } else if (availableBedsStr.isEmpty() || doctorsStr.isEmpty()) {
                tvAutoStatus.setText("⚪ Enter both values");
                tvAutoStatus.setTextColor(0xFF9E9E9E);
            } else if (databaseTotalBeds == 0 || databaseTotalDoctors == 0) {
                tvAutoStatus.setText("⚪ Loading database totals...");
                tvAutoStatus.setTextColor(0xFF9E9E9E);
            }
        } catch (NumberFormatException e) {
            Log.e("Hospital_Status_Edit", "Error parsing numbers in real-time update: " + e.getMessage());
            tvAutoStatus.setText("⚪ Enter valid numbers");
            tvAutoStatus.setTextColor(0xFF9E9E9E);
        }
    }

    private void updateAutoStatus() {
        try {
            String availableBedsStr = etAvailableBeds.getText().toString();
            String doctorsStr = etDoctorsAvailable.getText().toString();

            if (!availableBedsStr.isEmpty() && !doctorsStr.isEmpty() && databaseTotalBeds > 0 && databaseTotalDoctors > 0) {
                int availableBeds = Integer.parseInt(availableBedsStr);
                int availableDoctors = Integer.parseInt(doctorsStr);

                String status = calculateAutoStatus(availableBeds, availableDoctors);
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
            } else if (databaseTotalBeds == 0 || databaseTotalDoctors == 0) {
                // Show message when database totals are not loaded
                tvAutoStatus.setText("⚪ Loading database totals...");
                tvAutoStatus.setTextColor(0xFF9E9E9E);
            } else {
                // Show placeholder when not all fields are filled
                tvAutoStatus.setText("⚪ Enter available beds and doctors");
                tvAutoStatus.setTextColor(0xFF9E9E9E);
            }
        } catch (NumberFormatException e) {
            tvAutoStatus.setText("⚪ Enter valid numbers");
            tvAutoStatus.setTextColor(0xFF9E9E9E);
        }
    }

    private String calculateAutoStatus(int availableBeds, int availableDoctors) {
        Log.d("Hospital_Status_Edit", "=== CALCULATION DEBUG ===");
        Log.d("Hospital_Status_Edit", "Input - availableBeds: " + availableBeds + ", availableDoctors: " + availableDoctors);
        Log.d("Hospital_Status_Edit", "Database totals - totalBeds: " + databaseTotalBeds + ", totalDoctors: " + databaseTotalDoctors);
        
        // Validate input
        if (availableBeds < 0 || availableDoctors <= 0 || databaseTotalBeds <= 0 || databaseTotalDoctors <= 0) {
            Log.w("Hospital_Status_Edit", "Validation failed - returning unknown");
            return "unknown";
        }
        
        // Calculate capacity percentage based on database totals
        double capacityPercentage = ((double) (databaseTotalBeds - availableBeds) / databaseTotalBeds) * 100;
        
        // Calculate beds per doctor ratio based on database totals
        double bedsPerDoctor = (double) databaseTotalBeds / databaseTotalDoctors;
        
        // Calculate available beds per available doctor ratio
        double availableBedsPerDoctor = (double) availableBeds / availableDoctors;
        
        Log.d("Hospital_Status_Edit", "Calculated - capacityPercentage: " + capacityPercentage + 
              "%, bedsPerDoctor: " + bedsPerDoctor + ", availableBedsPerDoctor: " + availableBedsPerDoctor);
        
        // Automatic status logic based on database totals and current availability
        String result;
        if (availableBeds == 0) {
            result = "crowded"; // No available beds
        } else if (capacityPercentage >= 90 || availableBedsPerDoctor > 8 || availableDoctors < 2) {
            result = "crowded"; // At or near capacity, or insufficient staff
        } else if (capacityPercentage >= 70 || availableBedsPerDoctor > 6 || availableDoctors < 3) {
            result = "busy"; // High capacity or insufficient staff
        } else if (capacityPercentage >= 50 || availableBedsPerDoctor > 4) {
            result = "busy"; // Moderate capacity
        } else {
            result = "available"; // Good capacity and staff ratio
        }
        
        Log.d("Hospital_Status_Edit", "Final result: " + result);
        return result;
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
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {

                        // Load current status
                        Long availableBeds = documentSnapshot.getLong("availableBeds");
                        Long availableDoctors = documentSnapshot.getLong("availableDoctors");

                        if (availableBeds != null) etAvailableBeds.setText(String.valueOf(availableBeds));
                        if (availableDoctors != null) etDoctorsAvailable.setText(String.valueOf(availableDoctors));

                        // Update auto status display
                        updateAutoStatus();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, getString(R.string.failed_load_data), 
                                 Toast.LENGTH_SHORT).show();
                });
    }

    private void loadDatabaseTotals() {
        // Show loading state
        tvDatabaseTotalBeds.setText("Loading...");
        tvDatabaseTotalDoctors.setText("Loading...");
        
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalBeds = 0;
                    int totalDoctors = 0;
                    
                    for (com.google.firebase.firestore.DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        // Try both field names for backward compatibility
                        Long beds = document.getLong("totalBeds");
                        if (beds == null) {
                            beds = document.getLong("emergencyRoomBeds");
                        }
                        if (beds != null) {
                            totalBeds += beds.intValue();
                            Log.d("Hospital_Status_Edit", "Found beds: " + beds.intValue() + " from hospital: " + document.getString("hospitalName"));
                        } else {
                            Log.w("Hospital_Status_Edit", "No beds data found for hospital: " + document.getString("hospitalName"));
                        }
                    }
                    
                    Log.d("Hospital_Status_Edit", "Total beds calculated: " + totalBeds);
                    
                    // Get total doctors from a separate collection or document
                    // For now, we'll use a fixed value or get it from a system settings document
                    loadTotalDoctorsFromSystem();
                    
                    // Store totals for calculation
                    databaseTotalBeds = totalBeds;
                    
                    // Update UI with totals
                    tvDatabaseTotalBeds.setText(String.valueOf(totalBeds));
                    
                    // Update status calculation with new database totals
                    updateAutoStatus();
                })
                .addOnFailureListener(e -> {
                    tvDatabaseTotalBeds.setText("Error");
                    tvDatabaseTotalDoctors.setText("Error");
                    Toast.makeText(this, "Failed to load database totals: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadTotalDoctorsFromSystem() {
        // Load total doctors by summing up all doctors registered during hospital registration
        db.collection("Sagip")
                .document("users")
                .collection("hospital")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalRegisteredDoctors = 0;
                    
                    for (com.google.firebase.firestore.DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        // Try both field names for backward compatibility
                        Long registeredDoctors = document.getLong("totalDoctors");
                        if (registeredDoctors == null) {
                            registeredDoctors = document.getLong("emergencyRoomDoctors");
                        }
                        if (registeredDoctors != null) {
                            totalRegisteredDoctors += registeredDoctors.intValue();
                            Log.d("Hospital_Status_Edit", "Found doctors: " + registeredDoctors.intValue() + " from hospital: " + document.getString("hospitalName"));
                        } else {
                            Log.w("Hospital_Status_Edit", "No doctors data found for hospital: " + document.getString("hospitalName"));
                        }
                    }
                    
                    Log.d("Hospital_Status_Edit", "Total doctors calculated: " + totalRegisteredDoctors);
                    
                    databaseTotalDoctors = totalRegisteredDoctors;
                    tvDatabaseTotalDoctors.setText(String.valueOf(totalRegisteredDoctors));
                    updateAutoStatus();
                })
                .addOnFailureListener(e -> {
                    tvDatabaseTotalDoctors.setText("Error");
                    Toast.makeText(this, "Failed to load total registered doctors: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updateAutoStatus();
                });
    }

    private void saveHospitalStatus() {
        if (userId == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate input
        String availableBedsStr = etAvailableBeds.getText().toString();
        String doctorsAvailableStr = etDoctorsAvailable.getText().toString();

        if (availableBedsStr.isEmpty() || doctorsAvailableStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_fill_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int availableBeds = Integer.parseInt(availableBedsStr);
            int doctorsAvailable = Integer.parseInt(doctorsAvailableStr);

            // Validate the data
            if (availableBeds < 0) {
                Toast.makeText(this, getString(R.string.available_beds_cannot_be_negative), Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (doctorsAvailable <= 0) {
                Toast.makeText(this, getString(R.string.doctors_available_greater_than_zero), Toast.LENGTH_SHORT).show();
                return;
            }

            // Calculate automatic status
            String status = calculateAutoStatus(availableBeds, doctorsAvailable);

            // Create status data
            Map<String, Object> statusData = new HashMap<>();
            statusData.put("availableBeds", availableBeds);
            statusData.put("availableDoctors", doctorsAvailable); // New field name
            statusData.put("doctorsAvailable", doctorsAvailable); // Keep old field name for backward compatibility
            statusData.put("erStatus", status);
            statusData.put("lastUpdated", Timestamp.now());
            
            // Also update the status in the main hospital document
            statusData.put("DashboardStatues", status);

            // Save to Firestore
            db.collection("Sagip")
                    .document("users")
                    .collection("hospital")
                    .document(userId)
                    .update(statusData)
                    .addOnSuccessListener(aVoid -> {
                        String statusMessage = getString(R.string.hospital_status_updated_successfully) + "\n" + getString(R.string.status_label) + " " + 
                                             getStatusEmoji(status) + " " + status.toUpperCase();
                        Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show();
                        
                        // Clear the status update notification
                        clearStatusUpdateNotification();
                        
                        // Refresh database totals after successful update
                        loadDatabaseTotals();
                        
                        // Set result to indicate successful update
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, getString(R.string.failed_to_update_status), 
                                     Toast.LENGTH_SHORT).show();
                    });

        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.enter_valid_numbers), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Clears the status update notification
     */
    private void clearStatusUpdateNotification() {
        try {
            // Clear the notification from the notification manager
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(1001); // Same ID as used in the notification service
                Log.d("Hospital_Status_Edit", "Status update notification cleared");
            }
            
            // Also tell the notification service to cancel scheduled notifications
            android.content.Intent serviceIntent = new android.content.Intent(this, HospitalStatusNotificationService.class);
            serviceIntent.putExtra("action", "cancel_notification");
            startService(serviceIntent);
            
        } catch (Exception e) {
            Log.e("Hospital_Status_Edit", "Error clearing notification", e);
        }
    }
}
