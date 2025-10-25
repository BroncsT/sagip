package com.example.sagip_prototype;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

public class Barangay_Registration extends AppCompatActivity {

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    String userType = "barangay";
    
    // Broadcast receiver for language changes
    private android.content.BroadcastReceiver languageChangeReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if ("com.example.sagip_prototype.LANGUAGE_CHANGED".equals(intent.getAction())) {
                String languageCode = intent.getStringExtra("language");
                Log.d("Barangay_Registration", "Received language change broadcast: " + languageCode);
                
                // Apply the new language
                LanguageSelectionActivity.setAppLanguage(Barangay_Registration.this, languageCode);
                
                // Update UI elements
                updateUILanguage();
                
                // Show confirmation toast
                Toast.makeText(Barangay_Registration.this, getString(R.string.toast_language_change_detected), Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_barangay_registration);
        
        // Register language change receiver
        registerLanguageChangeReceiver();
        
        // Enable smooth scrolling
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        EditText barangayName = findViewById(R.id.emerContact_name);
        EditText address = findViewById(R.id.emerContact_Number);
        EditText contactPerson = findViewById(R.id.emerContact_add);
        EditText phoneNumber = findViewById(R.id.phoneNumber);
        EditText newPassword = findViewById(R.id.newPassword);
        EditText confirmNewPassword = findViewById(R.id.confirmNewPassword);

        // Setup real-time password validation
        setupPasswordValidation(newPassword, confirmNewPassword);

        Button continueButton = findViewById(R.id.addEmerContact);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String barangayNameText = barangayName.getText().toString().trim();
                String addressText = address.getText().toString().trim();
                String contactPersonText = contactPerson.getText().toString().trim();
                String phoneNumberText = phoneNumber.getText().toString().trim();
                String password = newPassword.getText().toString().trim();
                String confirmPassword = confirmNewPassword.getText().toString().trim();
                
                if (barangayNameText.isEmpty() || addressText.isEmpty() || contactPersonText.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(Barangay_Registration.this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Only validate phone number if it's provided
                if (!phoneNumberText.isEmpty() && !isValidPhoneNumber(phoneNumberText)) {
                    Toast.makeText(Barangay_Registration.this, getString(R.string.valid_mobile_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate password
                String passwordError = validatePassword(password, confirmPassword);
                if (passwordError != null) {
                    Toast.makeText(Barangay_Registration.this, passwordError, Toast.LENGTH_SHORT).show();
                    return;
                }

                
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Barangay_Registration.this, "User not authenticated", Toast.LENGTH_SHORT).show();
                    return;
                }
                String uid = user.getUid();
                String userEmail = user.getEmail(); // Get the email used for login

                // Change password first
                user.updatePassword(password)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Password updated successfully, now save user data
                                saveUserData(barangayNameText, addressText, contactPersonText, phoneNumberText, uid, userEmail);
                            } else {
                                Toast.makeText(Barangay_Registration.this, "Failed to update password: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            }
        });
    }

    private void saveUserData(String barangayNameText, String addressText, String contactPersonText, String phoneNumberText, String uid, String userEmail) {
        Map<String, Object> usrData = new HashMap<>();
        usrData.put("barangayName", barangayNameText);
        usrData.put("address", addressText);
        usrData.put("contactPerson", contactPersonText);
        usrData.put("mobileNumber", phoneNumberText);
        if (userEmail != null && !userEmail.isEmpty()) {
            usrData.put("email", userEmail);
        }
        usrData.put("user-type", userType);
        usrData.put("status", "registered");

        Toast.makeText(Barangay_Registration.this, "Starting registration process...", Toast.LENGTH_SHORT).show();

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
                                Toast.makeText(Barangay_Registration.this, "Data saved to Firestore successfully!", Toast.LENGTH_SHORT).show();
                                
                                // Step 2: Admin-provided email is stored in Firestore only
                                // Firebase Auth email remains as phone number (already verified)
                                // This avoids verification requirements and maintains admin control
                                Toast.makeText(Barangay_Registration.this,
                                        "Registration complete! Redirecting to dashboard...", 
                                        Toast.LENGTH_LONG).show();
                                
                                // Redirect to barangay dashboard
                                Intent dashboardIntent = new Intent(Barangay_Registration.this, Barangay_Dashboard.class);
                                startActivity(dashboardIntent);
                                finish();
                            } else {
                                String errorMsg = "Failed to save data: " + task.getException().getMessage();
                                Toast.makeText(Barangay_Registration.this, errorMsg, Toast.LENGTH_LONG).show();
                                Log.e("Barangay_Registration", errorMsg, task.getException());
                            }
                        }
                    }
                });
    }

    private boolean isValidPhoneNumber(String number) {
        return !number.isEmpty() && number.matches("09\\d{9}");
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
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle language change without recreating activity
        Log.d("Barangay_Registration", "Configuration changed - language change detected");
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        // Update UI elements with new language
        updateUILanguage();
        
        // Show toast to confirm language change
        Toast.makeText(this, getString(R.string.toast_language_change_detected), Toast.LENGTH_SHORT).show();
    }
    
    private void updateUILanguage() {
        // Update UI elements with new language
        // The form fields will be updated automatically when the activity recreates
        // or when the user navigates back to this activity
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Update UI language when returning
        updateUILanguage();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister language change receiver
        unregisterLanguageChangeReceiver();
    }
    
    private void registerLanguageChangeReceiver() {
        android.content.IntentFilter filter = new android.content.IntentFilter("com.example.sagip_prototype.LANGUAGE_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(languageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(languageChangeReceiver, filter);
        }
    }
    
    private void unregisterLanguageChangeReceiver() {
        try {
            unregisterReceiver(languageChangeReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
            Log.d("Barangay_Registration", "Language change receiver was not registered");
        }
    }

}