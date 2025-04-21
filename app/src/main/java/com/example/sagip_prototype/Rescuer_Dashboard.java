package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class Rescuer_Dashboard extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView brgyName;
    private String userType = "rescuer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        brgyName = findViewById(R.id.barangayStaffName);

        // Setup bottom navigation
        setupBottomNavigation();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in when activity starts
        checkAuthState();
    }

    private void checkAuthState() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // User is not logged in, redirect to login
            navigateToLogin();
        } else {
            // User is logged in, load their data
            loadUserData(currentUser.getUid());
        }
    }

    private void navigateToLogin() {
        Intent intent = new Intent(Rescuer_Dashboard.this, Log_in_Via_Email.class);
        // Clear the back stack so user can't press back to return after logging out
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadUserData(String uid) {
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // Check for rescuegroup field first as that should be in your rescuer documents
                                String rescueGroup = document.getString("rescuegroup");
                                if (rescueGroup != null) {
                                    brgyName.setText(rescueGroup);
                                } else {
                                    // Fallback to firstName if rescuegroup doesn't exist
                                    String firstName = document.getString("firstName");
                                    if (firstName != null) {
                                        brgyName.setText(firstName);
                                    } else {
                                        brgyName.setText("Rescue Group Not Available");
                                    }
                                }
                            } else {
                                Toast.makeText(Rescuer_Dashboard.this,
                                        "User document does not exist",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(Rescuer_Dashboard.this,
                                    "Error loading user data: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar);
        bottomNavigationView.setSelectedItemId(R.id.rescuer_dashboard);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.rescuer_dashboard) {
                return true;
            } else if (itemId == R.id.rescuer_hospital) {
                startActivity(new Intent(getApplicationContext(), Barangay_List.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (itemId == R.id.rescuer_profile) {
                startActivity(new Intent(getApplicationContext(), Barangay_Profile.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    // Add a method to handle logout (can be called from a menu item or button)
    public void logoutUser() {
        mAuth.signOut();
        navigateToLogin();
    }
}