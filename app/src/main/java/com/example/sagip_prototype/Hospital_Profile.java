package com.example.sagip_prototype;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class Hospital_Profile extends AppCompatActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_EMAIL = "userEmail";

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hospital_profile);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        LinearLayout UpdateProfile = findViewById(R.id.gotoupdate);
        TextView hospitalProfile = findViewById(R.id.profileName);
        TextView hospitalEmail = findViewById(R.id.profileEmail);
        LinearLayout logout = findViewById(R.id.logoutLayout);

        setupBottomNavigation();

        UpdateProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotoUpdate = new Intent(Hospital_Profile.this, Hospital_Registration.class);
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
        String userType = "hospital";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("hospiitalname");
                        String middleName = documentSnapshot.getString("hospitaladdress");


                        hospitalProfile.setText(firstName);
                        hospitalEmail.setText(middleName);
                    }
                });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // User confirmed logout
                    // Clear stored credentials FIRST
                    clearStoredCredentials();

                    // Then sign out from Firebase
                    mAuth.signOut();

                    Intent intent = new Intent(Hospital_Profile.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // User cancelled logout
                    dialog.dismiss();
                })
                .show();
    }

    // Helper method to clear stored credentials
    private void clearStoredCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_EMAIL);
        editor.apply();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavBar2);
        bottomNavigationView.setSelectedItemId(R.id.hospital_profile);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.hospital_profile) {
                return true;
            } else if (itemId == R.id.hospital_list) {
                startActivity(new Intent(getApplicationContext(), Hospital_List.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.hospital_home) {
                startActivity(new Intent(getApplicationContext(), Hospital_Dashboard.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

}