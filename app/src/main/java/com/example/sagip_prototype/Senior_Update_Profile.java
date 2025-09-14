package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Senior_Update_Profile extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText mobileNumberInput;
    private EditText emailInput;
    private Spinner barangaySpinner;
    private Button updateButton;
    private String originalMobileNumber;
    private String selectedBarangay = "";
    
    // List of all barangays in Angeles City
    private final String[] barangays = {
        "BARANGAY",
        "Agapito del Rosario",
        "Amsic",
        "Anunas",
        "Balibago",
        "Capaya",
        "Claro M. Recto",
        "Cuayan",
        "Cutcut",
        "Cutud",
        "Lourdes North West",
        "Lourdes Sur",
        "Lourdes Sur East",
        "Malabañas",
        "Margot",
        "Mining",
        "Ninoy Aquino",
        "Pampang",
        "Pandan",
        "Pulung Cacutud",
        "Pulung Maragul",
        "Pulungbulu",
        "Salapungan",
        "San Jose",
        "San Nicolas",
        "Santa Teresita",
        "Santa Trinidad",
        "Santo Cristo",
        "Santo Domingo",
        "Santo Rosario",
        "Sapalibutad",
        "Sapangbato",
        "Tabun",
        "Virgen Delos Remedios"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_update_profile);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        mobileNumberInput = findViewById(R.id.mobileNumber);
        emailInput = findViewById(R.id.emailAddress);
        barangaySpinner = findViewById(R.id.barangaySpinner);
        updateButton = findViewById(R.id.submitButton);

        // Setup barangay spinner
        setupBarangaySpinner();
        
        loadUserData();

        updateButton.setOnClickListener(v -> updateProfile());
    }

    private void loadUserData() {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get data from Firestore
                        String mobileNumber = documentSnapshot.getString("mobileNumber");
                        String barangay = documentSnapshot.getString("barangay");
                        String email = documentSnapshot.getString("email");
                        
                        // Store original mobile number for comparison
                        originalMobileNumber = mobileNumber;
                        
                        // Display data in UI
                        mobileNumberInput.setText(mobileNumber);
                        emailInput.setText(email != null ? email : "");
                        
                        // Set selected barangay in spinner
                        if (barangay != null && !barangay.isEmpty()) {
                            for (int i = 0; i < barangays.length; i++) {
                                if (barangays[i].equals(barangay)) {
                                    barangaySpinner.setSelection(i);
                                    selectedBarangay = barangay;
                                    break;
                                }
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Senior_Update_Profile.this,
                            "Failed to load profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateProfile() {
        // Validate inputs
        String mobileNum = mobileNumberInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();

        if (mobileNum.isEmpty() || selectedBarangay.isEmpty() || selectedBarangay.equals("BARANGAY")) {
            Toast.makeText(this, "Please fill in all required fields and select a barangay", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if mobile number has changed
        boolean mobileNumberChanged = !mobileNum.equals(originalMobileNumber);
        
        if (mobileNumberChanged) {
            // Show confirmation dialog for phone number change
            showPhoneChangeConfirmationDialog(mobileNum, selectedBarangay, email);
        } else {
            // Update profile directly
            updateProfileData(mobileNum, selectedBarangay, email);
        }
    }

    private void showPhoneChangeConfirmationDialog(String newMobileNumber, String barangay, String email) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Phone Number Change")
                .setMessage("You are changing your phone number from " + originalMobileNumber + " to " + newMobileNumber + 
                           ". This will require phone verification. Do you want to continue?")
                .setPositiveButton("Continue", (dialog, which) -> {
                    // Start phone verification process
                    startPhoneVerification(newMobileNumber, barangay, email);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Reset phone number to original
                    mobileNumberInput.setText(originalMobileNumber);
                })
                .show();
    }

    private void startPhoneVerification(String newMobileNumber, String barangay, String email) {
        // Disable button and show loading
        updateButton.setEnabled(false);
        updateButton.setText("Verifying...");
        
        // For now, we'll just update the profile
        // In a full implementation, you would integrate with Firebase Phone Auth here
        Toast.makeText(this, "Phone verification would be implemented here", Toast.LENGTH_SHORT).show();
        
        // Update profile data
        updateProfileData(newMobileNumber, barangay, email);
    }

    private void updateProfileData(String mobileNumber, String barangay, String email) {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        Map<String, Object> updates = new HashMap<>();
        updates.put("barangay", barangay);
        updates.put("mobileNumber", mobileNumber);
        
        // Only add email if it's not empty
        if (!email.isEmpty()) {
            updates.put("email", email);
        }

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(Senior_Update_Profile.this,
                            "Profile updated successfully",
                            Toast.LENGTH_SHORT).show();
                    updateButton.setEnabled(true);
                    updateButton.setText("Update Information");
                    Intent intent = new Intent(Senior_Update_Profile.this, Senior_Profile.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Senior_Update_Profile.this,
                            "Failed to update profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    updateButton.setEnabled(true);
                    updateButton.setText("Update Information");
                });
    }
    
    private void setupBarangaySpinner() {
        // Create ArrayAdapter for the spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, barangays);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        // Set the adapter to the spinner
        barangaySpinner.setAdapter(adapter);
        
        // Set up item selection listener
        barangaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBarangay = barangays[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedBarangay = "";
            }
        });
    }
}