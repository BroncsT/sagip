package com.example.sagip_prototype;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class Barangay_List extends AppCompatActivity implements SeniorAdapter.OnSeniorClickListener {

    private static final String TAG = "Barangay_List";
    
    private RecyclerView seniorsRecyclerView;
    private SeniorAdapter seniorAdapter;
    private List<Senior> seniorsList;
    private TextView noSeniorsText;
    private TextView labelProfile;
    private ImageView backButton;
    private ImageView notificationButton;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentBarangay;
    private String userId;
    private String userType = "barangay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_barangay_list);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize views
        initializeViews();
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Show immediate loading state
        showImmediateLoadingState();
        
        // Load current barangay and seniors
        loadCurrentBarangayAndSeniors();
        
        // Setup bottom navigation
        setupBottomNavigation();
    }

    private void initializeViews() {
        seniorsRecyclerView = findViewById(R.id.seniorsRecyclerView);
        noSeniorsText = findViewById(R.id.noSeniorsText);
        labelProfile = findViewById(R.id.labelProfile);
        backButton = findViewById(R.id.backButton);
        notificationButton = findViewById(R.id.notification);
        
        // Set click listeners
        backButton.setOnClickListener(v -> onBackPressed());
        notificationButton.setOnClickListener(v -> {
            // Navigate to notifications or show notification dialog
            Toast.makeText(this, "Notifications feature coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView");
        
        // Initialize the list and adapter
        seniorsList = new ArrayList<>();
        seniorAdapter = new SeniorAdapter(seniorsList, this);
        
        // Setup RecyclerView
        seniorsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        seniorsRecyclerView.setAdapter(seniorAdapter);
        
        Log.d(TAG, "RecyclerView setup completed");
    }
    
    private void showImmediateLoadingState() {
        // Show immediate loading state for instant feedback
        labelProfile.setText("Loading...");
        showNoSeniorsMessage("Loading senior citizens...");
    }

    private void loadCurrentBarangayAndSeniors() {
        // Get current user
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "No authenticated user found");
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        userId = mAuth.getCurrentUser().getUid();
        Log.d(TAG, "Loading barangay info for user ID: " + userId);
         
        // Try to load from cache first for instant display
        loadCachedBarangayAndSeniors();
        
        // Then load fresh data from Firebase
        loadBarangayFromFirebase();
    }
    
    private void loadCachedBarangayAndSeniors() {
        // Try to load cached barangay name for instant display
        SharedPreferences prefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        String cachedBarangay = prefs.getString("cachedBarangayName", null);
        
        if (cachedBarangay != null && !cachedBarangay.isEmpty()) {
            Log.d(TAG, "Using cached barangay: " + cachedBarangay);
            currentBarangay = cachedBarangay;
            labelProfile.setText("Senior Citizens in " + currentBarangay);
            
            // Load cached seniors if available
            loadCachedSeniors();
        } else {
            Log.d(TAG, "No cached barangay found, showing loading...");
            labelProfile.setText("Loading...");
        }
    }
    
    private void loadCachedSeniors() {
        // Load cached seniors count for instant display
        loadCachedSeniorsCount(currentBarangay);
    }
    
    private void loadBarangayFromFirebase() {
        // Load barangay information for current user
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Log.d(TAG, "Document exists: " + documentSnapshot.exists());
                    if (documentSnapshot.exists()) {
                        // Try to get barangay name from different possible fields
                        String barangayName = documentSnapshot.getString("barangayName");
                        if (barangayName == null || barangayName.isEmpty()) {
                            barangayName = documentSnapshot.getString("barangay");
                        }
                        if (barangayName == null || barangayName.isEmpty()) {
                            barangayName = documentSnapshot.getString("rescueGroup");
                        }
                        
                        Log.d(TAG, "Current barangay from Firebase: " + barangayName);
                        
                        if (barangayName != null && !barangayName.isEmpty()) {
                            currentBarangay = barangayName;
                            labelProfile.setText("Senior Citizens in " + currentBarangay);
                            
                            // Cache the barangay name for future instant loading
                            cacheBarangayName(barangayName);
                            
                            // Load seniors for this barangay
                            loadSeniorsForBarangay(currentBarangay);
                        } else {
                            Log.e(TAG, "Barangay not found for user. Available fields: " + documentSnapshot.getData().keySet());
                            showNoSeniorsMessage("Barangay information not found");
                        }
                    } else {
                        Log.e(TAG, "User document not found");
                        showNoSeniorsMessage("User profile not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading barangay information: " + e.getMessage());
                    showNoSeniorsMessage("Error loading barangay information: " + e.getMessage());
                });
    }
    
    private void cacheBarangayName(String barangayName) {
        SharedPreferences prefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        prefs.edit().putString("cachedBarangayName", barangayName).apply();
        Log.d(TAG, "Cached barangay name: " + barangayName);
    }

    private void loadSeniorsForBarangay(String barangay) {
        Log.d(TAG, "Loading seniors for barangay: " + barangay);
        
        // Show loading state immediately
        showNoSeniorsMessage("Loading seniors...");
        
        // Query seniors from the specified barangay (without status filter initially)
        db.collection("Sagip")
                .document("users")
                .collection("seniors")
                .whereEqualTo("barangay", barangay)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Query completed, found " + queryDocumentSnapshots.size() + " seniors");
                    
                    List<Senior> seniors = new ArrayList<>();
                    
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Senior senior = new Senior();
                            senior.setDocumentId(document.getId());
                            senior.setFirstName(document.getString("firstName"));
                            senior.setLastName(document.getString("lastName"));
                            senior.setMiddleName(document.getString("middleName"));
                            senior.setBirthday(document.getString("birthday"));
                            senior.setBarangay(document.getString("barangay"));
                            senior.setMobileNumber(document.getString("mobileNumber"));
                            senior.setProfileImageUrl(document.getString("profileImageUrl"));
                            
                            // Check for selfie verification URL with fallback to old field names
                            String selfieUrl = document.getString("selfieVerificationUrl");
                            if (selfieUrl == null || selfieUrl.isEmpty()) {
                                // Fallback to old field names for existing data
                                selfieUrl = document.getString("selfieUrl");
                                if (selfieUrl == null || selfieUrl.isEmpty()) {
                                    selfieUrl = document.getString("faceVerificationUrl");
                                }
                            }
                            senior.setSelfieVerificationUrl(selfieUrl);
                            
                            senior.setStatus(document.getString("status"));
                            senior.setUserType(document.getString("userType"));
                            senior.setEmail(document.getString("email"));
                            senior.setAddress(document.getString("address"));
                            
                            // Debug logging for image URLs
                            Log.d(TAG, "Senior: " + senior.getFullName() + 
                                " - ProfileImageUrl: " + senior.getProfileImageUrl() + 
                                " - SelfieVerificationUrl: " + senior.getSelfieVerificationUrl() +
                                " - Raw selfieVerificationUrl: " + document.getString("selfieVerificationUrl") +
                                " - Raw selfieUrl: " + document.getString("selfieUrl") +
                                " - Raw faceVerificationUrl: " + document.getString("faceVerificationUrl"));
                            
                            seniors.add(senior);
                            Log.d(TAG, "Added senior: " + senior.getFullName() + " (Status: " + senior.getStatus() + ")");
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing senior document: " + e.getMessage());
                        }
                    }
                    
                    // Filter to show only approved seniors
                    List<Senior> approvedSeniors = new ArrayList<>();
                    for (Senior senior : seniors) {
                        if ("approved".equals(senior.getStatus())) {
                            approvedSeniors.add(senior);
                        }
                    }
                    
                    Log.d(TAG, "Filtered to " + approvedSeniors.size() + " approved seniors out of " + seniors.size() + " total");
                    
                    // Cache the results for instant loading next time
                    cacheSeniors(barangay, approvedSeniors);
                    
                    // Update the adapter
                    updateSeniorsList(approvedSeniors);
                    
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading seniors: " + e.getMessage());
                    showNoSeniorsMessage("Error loading seniors: " + e.getMessage());
                });
    }
    
    private void cacheSeniors(String barangay, List<Senior> seniors) {
        // Cache seniors count for instant display
        SharedPreferences prefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        prefs.edit()
                .putInt("cachedSeniorsCount_" + barangay, seniors.size())
                .putLong("cachedSeniorsTimestamp_" + barangay, System.currentTimeMillis())
                .apply();
        
        Log.d(TAG, "Cached " + seniors.size() + " seniors for " + barangay);
    }
    
    private void loadCachedSeniorsCount(String barangay) {
        SharedPreferences prefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        int cachedCount = prefs.getInt("cachedSeniorsCount_" + barangay, -1);
        long cachedTimestamp = prefs.getLong("cachedSeniorsTimestamp_" + barangay, 0);
        
        // Check if cache is recent (within 5 minutes)
        boolean isCacheRecent = (System.currentTimeMillis() - cachedTimestamp) < (5 * 60 * 1000);
        
        if (cachedCount >= 0 && isCacheRecent) {
            Log.d(TAG, "Using cached seniors count: " + cachedCount);
            if (cachedCount > 0) {
                showNoSeniorsMessage("Found " + cachedCount + " senior citizens (cached)\n\nTap refresh to load latest data");
            } else {
                showNoSeniorsMessage("No senior citizens found (cached)\n\nTap refresh to load latest data");
            }
        }
    }

    private void updateSeniorsList(List<Senior> seniors) {
        Log.d(TAG, "Updating seniors list with " + seniors.size() + " items");
        
        seniorsList.clear();
        seniorsList.addAll(seniors);
        
        // Preload images for faster display
        preloadImages(seniors);
        
        seniorAdapter.notifyDataSetChanged();
        
        // Show/hide appropriate views
        if (seniors.isEmpty()) {
            showNoSeniorsMessage("No senior citizens registered in " + currentBarangay);
        } else {
            hideNoSeniorsMessage();
        }
    }
    
    private void preloadImages(List<Senior> seniors) {
        // Preload images into Picasso cache for faster display
        for (Senior senior : seniors) {
            String imageUrl = senior.getSelfieVerificationUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = senior.getProfileImageUrl();
            }
            
            if (imageUrl != null && !imageUrl.isEmpty()) {
                try {
                    // Preload image into cache
                    Picasso.get()
                            .load(imageUrl)
                            .resize(120, 120)
                            .centerCrop()
                            .transform(new CircleTransform())
                            .fetch(); // Fetch without setting to ImageView
                } catch (Exception e) {
                    Log.e(TAG, "Error preloading image for " + senior.getFullName() + ": " + e.getMessage());
                }
            }
        }
    }
    

    private void showNoSeniorsMessage(String message) {
        noSeniorsText.setText(message);
        noSeniorsText.setVisibility(View.VISIBLE);
        seniorsRecyclerView.setVisibility(View.GONE);
    }

    private void hideNoSeniorsMessage() {
        noSeniorsText.setVisibility(View.GONE);
        seniorsRecyclerView.setVisibility(View.VISIBLE);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.barangay_seniorList);
        
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.barangay_dashboard) {
                startActivity(new Intent(getApplicationContext(), Barangay_Dashboard.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.barangay_seniorList) {
                // Already in this activity
                return true;
            } else if (itemId == R.id.barangay_profile) {
                startActivity(new Intent(getApplicationContext(), Barangay_Profile.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    @Override
    public void onSeniorClick(Senior senior) {
        Log.d(TAG, "Senior clicked: " + senior.getFullName());
        
        // Preload the image for faster display in details view
        preloadSeniorImage(senior);
        
        // Create intent to show senior details
        Intent intent = new Intent(this, Senior_Details_Activity.class);
        intent.putExtra("senior_document_id", senior.getDocumentId());
        intent.putExtra("senior_name", senior.getFullName());
        intent.putExtra("senior_barangay", senior.getBarangay());
        intent.putExtra("senior_phone", senior.getMobileNumber());
        intent.putExtra("senior_address", senior.getAddress());
        intent.putExtra("senior_age", senior.getAge());
        intent.putExtra("senior_status", senior.getStatus());
        intent.putExtra("senior_profile_image", senior.getProfileImageUrl());
        intent.putExtra("senior_selfie_image", senior.getSelfieVerificationUrl());
        
        startActivity(intent);
    }
    
    private void preloadSeniorImage(Senior senior) {
        // Preload the image that will be used in the details view
        String imageUrl = senior.getSelfieVerificationUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = senior.getProfileImageUrl();
        }
        
        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                // Preload image into cache for details view
                Picasso.get()
                        .load(imageUrl)
                        .resize(200, 200)
                        .centerCrop()
                        .transform(new CircleTransform())
                        .fetch(); // Fetch without setting to ImageView
                Log.d(TAG, "Preloaded image for " + senior.getFullName());
            } catch (Exception e) {
                Log.e(TAG, "Error preloading image for " + senior.getFullName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Activity resumed, refreshing data");
        
        // Show cached data immediately for instant display
        if (currentBarangay != null && !currentBarangay.isEmpty()) {
            loadCachedSeniorsCount(currentBarangay);
            // Then refresh with fresh data
            loadSeniorsForBarangay(currentBarangay);
        } else {
            // If no barangay loaded yet, try to load from cache
            loadCachedBarangayAndSeniors();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Navigate back to Barangay Dashboard
        startActivity(new Intent(this, Barangay_Dashboard.class));
        finish();
    }
}