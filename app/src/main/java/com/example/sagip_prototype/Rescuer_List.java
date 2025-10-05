package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class Rescuer_List extends BaseRescuerActivity implements HospitalLIstAdapter.OnHospitalLIstClickListener {

    private static final String TAG = "Rescuer_List";
    
    RecyclerView recyclerView;
    HospitalLIstAdapter hospitalAdapter;
    List<HospitalLIst> hospitalList;

    FirebaseFirestore  db;
    
    // Variables for notification handling
    private String highlightHospitalName = null;
    private String notificationType = null;
    
    // Emergency notification system variables
    private ListenerRegistration emergencyListener;
    private String userId;
    private String userType;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_list);
        
        Log.d(TAG, "=== RESCUER_LIST ACTIVITY CREATED ===");
        
        // Initialize emergency notification system
        initializeEmergencyNotificationSystem();
        
        // Handle notification extras
        handleNotificationExtras();
        
        setupBottomNavigation();
        SetupRecyclerView();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🚨 Rescuer_List onResume - starting emergency listener");
        
        // Start emergency listener when activity resumes
        if (emergencyListener == null) {
            startEmergencyListener();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "🚨 Rescuer_List onPause - stopping emergency listener");
        
        // Stop emergency listener when activity pauses - EmergencyNotificationService will handle background
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🚨 Rescuer_List onDestroy - cleaning up emergency listener");
        
        // Remove emergency listener
        if (emergencyListener != null) {
            emergencyListener.remove();
            emergencyListener = null;
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        android.util.Log.d("Rescuer_List", "=== ON_NEW_INTENT CALLED ===");
        setIntent(intent);
        handleNotificationExtras();
    }
    
    private void handleNotificationExtras() {
        Intent intent = getIntent();
        if (intent != null) {
            notificationType = intent.getStringExtra("notification_type");
            highlightHospitalName = intent.getStringExtra("highlight_hospital");
            
            android.util.Log.d("Rescuer_List", "Notification extras - Type: " + notificationType + 
                ", Hospital: " + highlightHospitalName);
            
            if ("hospital_status_update".equals(notificationType) && highlightHospitalName != null) {
                // Show a toast to indicate this was opened from a notification
                android.widget.Toast.makeText(this, 
                    "🏥 Hospital Status Update: " + highlightHospitalName, 
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private void SetupRecyclerView() {

        recyclerView = findViewById(R.id.recyclerViewHospitals);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        hospitalList = new ArrayList<>();
        hospitalAdapter = new HospitalLIstAdapter(hospitalList, this);
        recyclerView.setAdapter(hospitalAdapter);

        db = FirebaseFirestore.getInstance();
        fetchHospitalData();

    }

    private void fetchHospitalData() {

        db.collection("Sagip")
                .document("users")
                .collection("hospital") // This should match the userType you use when saving hospital data
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (error != null) {
                            // Log error for debugging
                            System.out.println("Error fetching hospital data: " + error.getMessage());
                            return;
                        }

                        if (value != null) {
                            hospitalList.clear();
                            android.util.Log.d("Rescuer_List", "Found " + value.getDocuments().size() + " hospital documents");
                            for (com.google.firebase.firestore.DocumentSnapshot document : value.getDocuments()) {
                                // Create hospital object manually to handle all fields
                                HospitalLIst hospital = new HospitalLIst();
                                
                                // Set basic info
                                hospital.setHospitalName(document.getString("hospitalName"));
                                hospital.setHospitalAddress(document.getString("hospitalAddress"));
                                
                                android.util.Log.d("Rescuer_List", "Processing hospital: " + hospital.getHospitalName());
                                
                                // Set numeric fields
                                if (document.getLong("totalBeds") != null) {
                                    hospital.setTotalBeds(document.getLong("totalBeds").intValue());
                                }
                                if (document.getLong("availableBeds") != null) {
                                    hospital.setAvailableBeds(document.getLong("availableBeds").intValue());
                                }
                                if (document.getLong("doctorsAvailable") != null) {
                                    hospital.setDoctorsAvailable(document.getLong("doctorsAvailable").intValue());
                                }
                                
                                // Set status fields
                                hospital.setErStatus(document.getString("erStatus"));
                                if (document.getDouble("capacityPercentage") != null) {
                                    hospital.setCapacityPercentage(document.getDouble("capacityPercentage"));
                                }
                                
                                // Set timestamp
                                if (document.getTimestamp("lastUpdated") != null) {
                                    hospital.setLastUpdated(document.getTimestamp("lastUpdated").toString());
                                }
                                
                                
                                // Debug logging for status calculation
                                String calculatedStatus = hospital.getCalculatedStatus();
                                android.util.Log.d("Rescuer_List", "Hospital: " + hospital.getHospitalName() + 
                                    ", Beds: " + hospital.getAvailableBeds() + "/" + hospital.getTotalBeds() + 
                                    ", Doctors: " + hospital.getDoctorsAvailable() + 
                                    ", Status: " + calculatedStatus);
                                
                                if (hospital.getHospitalName() != null) {
                                    hospitalList.add(hospital);
                                }
                            }
                            hospitalAdapter.notifyDataSetChanged();
                            
                            // Scroll to and highlight the specific hospital if opened from notification
                            if (highlightHospitalName != null) {
                                scrollToAndHighlightHospital(highlightHospitalName);
                            }
                        }
                    }
                });
    }
    
    private void scrollToAndHighlightHospital(String hospitalName) {
        if (hospitalList != null && hospitalAdapter != null) {
            for (int i = 0; i < hospitalList.size(); i++) {
                HospitalLIst hospital = hospitalList.get(i);
                if (hospital.getHospitalName() != null && 
                    hospital.getHospitalName().equalsIgnoreCase(hospitalName)) {
                    
                    // Scroll to the specific hospital
                    recyclerView.smoothScrollToPosition(i);
                    
                    // Highlight the hospital (you can add visual highlighting here)
                    android.util.Log.d("Rescuer_List", "🏥 Highlighting hospital: " + hospitalName + " at position: " + i);
                    
                    // Optional: Add a slight delay and scroll again to ensure visibility
                    final int finalPosition = i;
                    recyclerView.postDelayed(() -> {
                        recyclerView.smoothScrollToPosition(finalPosition);
                    }, 500);
                    
                    break;
                }
            }
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.rescuer_hospital);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.rescuer_hospital) {
                return true;
            } else if (itemId == R.id.rescuer_profile) {
                startActivity(new Intent(getApplicationContext(), Rescuer_Profile.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.rescuer_dashboard) {
                startActivity(new Intent(getApplicationContext(), Rescuer_Dashboard.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }
    
    // =============== EMERGENCY NOTIFICATION SYSTEM ===============
    
    /**
     * Initialize emergency notification system
     */
    private void initializeEmergencyNotificationSystem() {
        Log.d(TAG, "🚨 Initializing emergency notification system in Rescuer_List");
        
        // Get user info from preferences
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", null);
        userType = prefs.getString("user_type", null);
        
        Log.d(TAG, "🚨 User ID: " + userId + ", User Type: " + userType);
    }
    
    /**
     * Start emergency listener
     */
    private void startEmergencyListener() {
        Log.d(TAG, "🚨 Starting emergency listener in Rescuer_List...");
        
        // Check if user is a rescuer
        if (userId == null || userType == null || !userType.equals("rescuer")) {
            Log.w(TAG, "⚠️ User is not a rescuer, skipping emergency listener");
            return;
        }
        
        // Prevent duplicate listeners
        if (emergencyListener != null) {
            Log.w(TAG, "Emergency listener already exists, removing old one first");
            emergencyListener.remove();
            emergencyListener = null;
        }
        
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
                    
                    Log.d(TAG, "🚨 Emergency listener triggered in Rescuer_List - snapshots: " + (snapshots != null ? snapshots.size() : "null"));
                    
                    if (snapshots != null && !snapshots.isEmpty()) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            Log.d(TAG, "🚨 Document change type: " + dc.getType() + " for document: " + dc.getDocument().getId());
                            
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                // New emergency detected!
                                DocumentSnapshot emergency = dc.getDocument();
                                Log.d(TAG, "🚨 NEW EMERGENCY DETECTED IN RESCUER_LIST: " + emergency.getId());
                                handleNewEmergency(emergency);
                            }
                        }
                    } else {
                        Log.d(TAG, "🚨 No active emergencies found in Rescuer_List");
                    }
                });
        
        Log.d(TAG, "🚨 Emergency listener started successfully in Rescuer_List");
    }
    
    /**
     * Handle new emergency notification
     */
    private void handleNewEmergency(DocumentSnapshot emergency) {
        String title = emergency.getString("title");
        String message = emergency.getString("message");
        String seniorName = emergency.getString("seniorName");
        String seniorPhone = emergency.getString("seniorPhone");
        String locationAddress = emergency.getString("locationAddress");
        String helpRequestId = emergency.getString("helpRequestId");
        
        Log.d(TAG, "🚨🚨🚨 NEW EMERGENCY RECEIVED IN RESCUER_LIST 🚨🚨🚨");
        Log.d(TAG, "🚨 Senior: " + seniorName);
        Log.d(TAG, "🚨 Location: " + locationAddress);
        Log.d(TAG, "🚨 Help Request ID: " + helpRequestId);
        
        // Check if this rescuer has already responded to this emergency
        String respondedBy = emergency.getString("respondedBy");
        if (respondedBy != null && respondedBy.equals(userId)) {
            Log.d(TAG, "Current rescuer already responded to this emergency, skipping notification for: " + helpRequestId);
            return;
        }
        
        // Show emergency alert dialog
        showEmergencyAlert(title, message, seniorName, seniorPhone, locationAddress, helpRequestId);
    }
    
    /**
     * Show emergency alert dialog
     */
    private void showEmergencyAlert(String title, String message, String seniorName, 
                                  String seniorPhone, String locationAddress, String helpRequestId) {
        
        String fullMessage = "🚨 EMERGENCY ALERT 🚨\n\n" +
                "👤 Senior: " + seniorName + "\n" +
                "📍 Location: " + locationAddress + "\n" +
                "📞 Phone: " + (seniorPhone != null ? seniorPhone : "Not provided") + "\n\n" +
                "Please respond immediately!";
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title != null ? title : "🚨 EMERGENCY HELP REQUEST")
                .setMessage(fullMessage)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("🚑 RESPOND NOW", (dialog, which) -> {
                    // Navigate to dashboard to handle emergency
                    Intent intent = new Intent(this, Rescuer_Dashboard.class);
                    intent.putExtra("emergency_notification", true);
                    intent.putExtra("helpRequestId", helpRequestId);
                    intent.putExtra("senior_name", seniorName);
                    intent.putExtra("location", locationAddress);
                    startActivity(intent);
                    dialog.dismiss();
                })
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                .setCancelable(false); // Prevent dismissing by tapping outside
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        Log.d(TAG, "✅ Emergency alert shown in Rescuer_List for: " + seniorName);
    }
    
    // =============== HOSPITAL CLICK LISTENER ===============
    
    @Override
    public void onHospitalClick(HospitalLIst hospital) {
        Log.d(TAG, "Hospital clicked: " + hospital.getHospitalName());
        // You can add hospital click handling here
        // For example, show hospital details or navigate to hospital dashboard
    }
}