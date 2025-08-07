package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class Rescuer_Profile extends AppCompatActivity {

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        LinearLayout UpdateProfile = findViewById(R.id.gotoupdate1);
        TextView rescueProfile = findViewById(R.id.profileName);
        TextView rescueEmail = findViewById(R.id.profileEmail);
        LinearLayout logout = findViewById(R.id.logoutLayout);

        setupBottomNavigation();

        UpdateProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotoUpdate = new Intent(Rescuer_Profile.this, Rescuer_Registration.class);
                startActivity(gotoUpdate);

            }
        });

        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogoutConfirmationDialog();
            }
        });

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "rescuer";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("rescuegroup");
                        String middleName = documentSnapshot.getString("email");


                        rescueProfile.setText(firstName);
                        rescueEmail.setText(middleName);
                    }
                });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // User confirmed logout - use the proper logout method
                    handleLogout();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // User cancelled logout
                    dialog.dismiss();
                })
                .show();
    }

    // Method to handle logout and clear emergency state
    private void handleLogout() {
        // Sign out from Firebase
        mAuth.signOut();
        
        // Navigate to login screen
        Intent intent = new Intent(Rescuer_Profile.this, MainActivity.class);
        intent.putExtra("LOGOUT_ACTION", true); // Signal that this is a logout action
        startActivity(intent);
        finish();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.rescuer_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.rescuer_profile) {
                return true;
            } else if (itemId == R.id.rescuer_hospital) {
                startActivity(new Intent(getApplicationContext(), Rescuer_List.class));
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
}