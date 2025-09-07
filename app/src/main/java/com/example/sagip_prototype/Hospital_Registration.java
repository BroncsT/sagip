package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import android.util.Log;

public class Hospital_Registration extends AppCompatActivity {

    FirebaseAuth auth;
    FirebaseFirestore db;

    String userType = "hospital";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_registration);
        
        // Enable smooth scrolling
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // View references
        EditText getHospitalName = findViewById(R.id.hospitalName);
        EditText getAddress = findViewById(R.id.emerContact_Number);
        EditText getPhoneNumber = findViewById(R.id.phoneNumber);
        EditText emergencyRoomBeds = findViewById(R.id.emergencyRoomBeds);
        EditText emergencyRoomDoctors = findViewById(R.id.emergencyRoomDoctors);
        EditText newPassword = findViewById(R.id.newPassword);
        EditText confirmNewPassword = findViewById(R.id.confirmNewPassword);
        Button continueButton = findViewById(R.id.addEmerContact);

        // Firebase initialization
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Setup real-time password validation
        setupPasswordValidation(newPassword, confirmNewPassword);

        // Get passed phone number from intent (not displayed in new layout)
        String passedMobileNumber = getIntent().getStringExtra("MOBILE_NUMBER");

        // Save data on button click
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String hospitalName = getHospitalName.getText().toString().trim();
                String address = getAddress.getText().toString().trim();
                String phoneNumber = getPhoneNumber.getText().toString().trim();
                String erBeds = emergencyRoomBeds.getText().toString().trim();
                String erDoctors = emergencyRoomDoctors.getText().toString().trim();
                String password = newPassword.getText().toString().trim();
                String confirmPassword = confirmNewPassword.getText().toString().trim();
                
                if (hospitalName.isEmpty() || address.isEmpty() || erBeds.isEmpty() || erDoctors.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(Hospital_Registration.this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Only validate phone number if it's provided
                if (!phoneNumber.isEmpty() && !isValidPhoneNumber(phoneNumber)) {
                    Toast.makeText(Hospital_Registration.this, getString(R.string.valid_mobile_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate numeric fields
                if (!isValidNumber(erBeds) || !isValidNumber(erDoctors)) {
                    Toast.makeText(Hospital_Registration.this, "Please enter valid numbers for beds and doctors", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate password
                String passwordError = validatePassword(password, confirmPassword);
                if (passwordError != null) {
                    Toast.makeText(Hospital_Registration.this, passwordError, Toast.LENGTH_SHORT).show();
                    return;
                }


                FirebaseUser user = auth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Hospital_Registration.this, "User not authenticated", Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = user.getUid();

                // Change password first
                user.updatePassword(password)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Password updated successfully, now save user data
                                saveUserData(hospitalName, address, phoneNumber, erBeds, erDoctors, uid);
                            } else {
                                Toast.makeText(Hospital_Registration.this, "Failed to update password: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            }
        });
    }

    private void saveUserData(String hospitalName, String address, String phoneNumber, String erBeds, String erDoctors, String uid) {
        // Prepare user data
        Map<String, Object> usrData = new HashMap<>();
        usrData.put("hospitalName", hospitalName);
        usrData.put("hospitalAddress", address); // Changed from "address" to "hospitalAddress" for consistency
        usrData.put("mobileNumber", phoneNumber);
        usrData.put("totalBeds", Integer.parseInt(erBeds)); // Changed to "totalBeds" for consistency
        usrData.put("emergencyRoomBeds", Integer.parseInt(erBeds)); // Keep both for backward compatibility
        usrData.put("totalDoctors", Integer.parseInt(erDoctors)); // Add "totalDoctors" for consistency
        usrData.put("emergencyRoomDoctors", Integer.parseInt(erDoctors)); // Keep both for backward compatibility
        usrData.put("user-type", userType);
        usrData.put("status", "registered");
        
        // Add logging for debugging
        Log.d("Hospital_Registration", "Saving data: " + usrData.toString());
        Log.d("Hospital_Registration", "Saving to path: Sagip/users/" + userType + "/" + uid);

        Toast.makeText(Hospital_Registration.this, "Starting registration process...", Toast.LENGTH_SHORT).show();

        // Step 1: Save to Firestore with admin-provided email first
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .set(usrData)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (!isFinishing() && !isDestroyed()) {
                            if (task.isSuccessful()) {
                                Toast.makeText(Hospital_Registration.this, "Data saved to Firestore successfully!", Toast.LENGTH_SHORT).show();
                                
                                // Step 2: Admin-provided email is stored in Firestore only
                                // Firebase Auth email remains as phone number (already verified)
                                // This avoids verification requirements and maintains admin control
                                Toast.makeText(Hospital_Registration.this,
                                        "Registration complete! Redirecting to dashboard...", 
                                        Toast.LENGTH_LONG).show();
                                
                                // Redirect to hospital dashboard
                                Intent dashboardIntent = new Intent(Hospital_Registration.this, Hospital_Dashboard.class);
                                startActivity(dashboardIntent);
                                finish();
                            } else {
                                String errorMsg = "Failed to save data: " + task.getException().getMessage();
                                Toast.makeText(Hospital_Registration.this, errorMsg, Toast.LENGTH_LONG).show();
                                Log.e("Hospital_Registration", errorMsg, task.getException());
                            }
                        }
                    }
                });
    }

    private boolean isValidPhoneNumber(String number) {
        return !number.isEmpty() && number.matches("09\\d{9}");
    }

    private boolean isValidNumber(String number) {
        if (number.isEmpty()) return false;
        try {
            int num = Integer.parseInt(number);
            return num > 0; // Must be a positive number
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void setupPasswordValidation(EditText newPassword, EditText confirmPassword) {
        // Real-time validation for new password
        newPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePasswordRealTime(newPassword, s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Real-time validation for confirm password
        confirmPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateConfirmPasswordRealTime(confirmPassword, newPassword.getText().toString(), s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void validatePasswordRealTime(EditText passwordField, String password) {
        com.google.android.material.textfield.TextInputLayout layout = (com.google.android.material.textfield.TextInputLayout) passwordField.getParent().getParent();
        
        if (password.isEmpty()) {
            layout.setHelperText(getString(R.string.password_requirements));
            layout.setError(null);
            return;
        }

        if (password.length() < 8) {
            layout.setError(getString(R.string.password_too_short));
            return;
        }

        if (!password.matches(".*[a-z].*")) {
            layout.setError(getString(R.string.password_no_lowercase));
            return;
        }

        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,./<>?].*")) {
            layout.setError(getString(R.string.password_no_symbol));
            return;
        }

        layout.setError(null);
        layout.setHelperText("✓ Password meets requirements");
    }

    private void validateConfirmPasswordRealTime(EditText confirmField, String newPassword, String confirmPassword) {
        com.google.android.material.textfield.TextInputLayout layout = (com.google.android.material.textfield.TextInputLayout) confirmField.getParent().getParent();
        
        if (confirmPassword.isEmpty()) {
            layout.setHelperText("Confirm your password");
            layout.setError(null);
            return;
        }

        if (!confirmPassword.equals(newPassword)) {
            layout.setError("Passwords do not match");
            return;
        }

        layout.setError(null);
        layout.setHelperText("✓ Passwords match");
    }

    private String validatePassword(String password, String confirmPassword) {
        // Check if passwords match
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match";
        }

        // Check minimum length
        if (password.length() < 8) {
            return getString(R.string.password_too_short);
        }

        // Check for lowercase letter
        if (!password.matches(".*[a-z].*")) {
            return getString(R.string.password_no_lowercase);
        }

        // Check for symbol
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;:,./<>?].*")) {
            return getString(R.string.password_no_symbol);
        }

        return null; // Password is valid
    }

}