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
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;

public class Hospital_Registration extends AppCompatActivity {

    FirebaseAuth auth;
    FirebaseFirestore db;

    String userType = "hospital";
    
    // Phone verification variables
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    private ProgressDialog progressDialog;

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
        
        // Setup phone auth callbacks
        setupPhoneAuthCallbacks();

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
                
                if (hospitalName.isEmpty() || address.isEmpty() || erBeds.isEmpty() || erDoctors.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || phoneNumber.isEmpty()) {
                    Toast.makeText(Hospital_Registration.this, getString(R.string.please_fill_all_required_fields), Toast.LENGTH_SHORT).show();
                    return;
                }

                // Only validate phone number if it's provided
                if (!phoneNumber.isEmpty() && !isValidPhoneNumber(phoneNumber)) {
                    Toast.makeText(Hospital_Registration.this, getString(R.string.valid_mobile_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate numeric fields
                if (!isValidNumber(erBeds) || !isValidNumber(erDoctors)) {
                    Toast.makeText(Hospital_Registration.this, getString(R.string.please_enter_valid_numbers_beds_doctors), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(Hospital_Registration.this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
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
                                // Password updated successfully, now proceed with phone verification if number provided
                                if (!phoneNumber.isEmpty()) {
                                    verifyPhoneNumber(phoneNumber, hospitalName, address, erBeds, erDoctors, uid, userEmail);
                                } else {
                                    // Skip phone verification and save data directly
                                    saveUserData(hospitalName, address, phoneNumber, erBeds, erDoctors, uid, userEmail);
                                }
                            } else {
                                Toast.makeText(Hospital_Registration.this, String.format(getString(R.string.failed_to_update_password_format), task.getException().getMessage()), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            }
        });
    }

    private void saveUserData(String hospitalName, String address, String phoneNumber, String erBeds, String erDoctors, String uid, String userEmail) {
        // Prepare user data
        Map<String, Object> usrData = new HashMap<>();
        usrData.put("hospitalName", hospitalName);
        usrData.put("hospitalAddress", address); // Changed from "address" to "hospitalAddress" for consistency
        usrData.put("mobileNumber", phoneNumber);
        if (userEmail != null && !userEmail.isEmpty()) {
            usrData.put("email", userEmail);
        }
        usrData.put("totalBeds", Integer.parseInt(erBeds)); // Changed to "totalBeds" for consistency
        usrData.put("emergencyRoomBeds", Integer.parseInt(erBeds)); // Keep both for backward compatibility
        usrData.put("totalDoctors", Integer.parseInt(erDoctors)); // Add "totalDoctors" for consistency
        usrData.put("emergencyRoomDoctors", Integer.parseInt(erDoctors)); // Keep both for backward compatibility
        usrData.put("user-type", userType);
        usrData.put("status", "registered");
        
        // Add logging for debugging
        Log.d("Hospital_Registration", "Saving data: " + usrData.toString());
        Log.d("Hospital_Registration", "Saving to path: Sagip/users/" + userType + "/" + uid);

        Toast.makeText(Hospital_Registration.this, getString(R.string.starting_registration_process), Toast.LENGTH_SHORT).show();

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
                                Toast.makeText(Hospital_Registration.this, getString(R.string.data_saved_successfully), Toast.LENGTH_SHORT).show();
                                
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
    
    private void setupPhoneAuthCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                dismissProgressDialog();
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(Hospital_Registration.this, getString(R.string.verification_automatically_completed), Toast.LENGTH_SHORT).show();
                    linkPhoneWithCurrentUser(credential);
                }
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                dismissProgressDialog();
                if (!isFinishing() && !isDestroyed()) {
                    Log.e("Hospital_Registration", "Firebase verification failed: " + e.getMessage(), e);
                    String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    
                    // Check if error is due to MFA incompatibility
                    if (errorMsg.contains("first factor") || 
                        errorMsg.contains("sms based mfa") ||
                        errorMsg.contains("multi-factor") ||
                        errorMsg.contains("second factor")) {
                        // MFA conflict - skip phone verification and save data without linking phone
                        Log.d("Hospital_Registration", "Phone verification failed due to MFA. Saving data without phone linking.");
                        Toast.makeText(Hospital_Registration.this, getString(R.string.phone_auth_no_mfa_needed), Toast.LENGTH_SHORT).show();
                        saveUserData(pendingHospitalName, pendingAddress, pendingPhoneNumber, pendingErBeds, pendingErDoctors, pendingUid, pendingUserEmail);
                    } else {
                        String errorMessage = "Verification failed. Please check your internet connection and try again.";
                        Toast.makeText(Hospital_Registration.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                dismissProgressDialog();
                mVerificationId = verificationId;
                mResendToken = token;

                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(Hospital_Registration.this, getString(R.string.verification_code_sent), Toast.LENGTH_SHORT).show();
                    showVerificationCodeInputDialog();
                }
            }
        };
    }
    
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
    
    // Store pending registration data
    private String pendingHospitalName;
    private String pendingAddress;
    private String pendingPhoneNumber;
    private String pendingErBeds;
    private String pendingErDoctors;
    private String pendingUid;
    private String pendingUserEmail;
    
    private void verifyPhoneNumber(String phoneNumber, String hospitalName, String address, String erBeds, String erDoctors, String uid, String userEmail) {
        // Store pending data
        pendingHospitalName = hospitalName;
        pendingAddress = address;
        pendingPhoneNumber = phoneNumber;
        pendingErBeds = erBeds;
        pendingErDoctors = erDoctors;
        pendingUid = uid;
        pendingUserEmail = userEmail;
        
        // Remove leading 0 if present and format as +63XXXXXXXXXX
        String formattedNumber = phoneNumber.trim();
        if (formattedNumber.startsWith("0")) {
            formattedNumber = formattedNumber.substring(1);
        }
        if (!formattedNumber.startsWith("+")) {
            formattedNumber = "+63" + formattedNumber;
        }
        
        Log.d("Hospital_Registration", "Verifying phone number: " + formattedNumber);
        
        // Show loading dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending verification code...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(formattedNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        Log.d("Hospital_Registration", "onVerificationCompleted called");
                        dismissProgressDialog();
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(Hospital_Registration.this, getString(R.string.verification_automatically_completed), Toast.LENGTH_SHORT).show();
                            linkPhoneWithCurrentUser(credential);
                        }
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        Log.e("Hospital_Registration", "onVerificationFailed: " + e.getMessage(), e);
                        dismissProgressDialog();
                        if (!isFinishing() && !isDestroyed()) {
                            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                            
                            if (errorMsg.contains("first factor") || 
                                errorMsg.contains("sms based mfa") ||
                                errorMsg.contains("multi-factor") ||
                                errorMsg.contains("second factor")) {
                                Log.d("Hospital_Registration", "MFA conflict - saving data without phone linking");
                                Toast.makeText(Hospital_Registration.this, getString(R.string.phone_auth_no_mfa_needed), Toast.LENGTH_SHORT).show();
                                saveUserData(pendingHospitalName, pendingAddress, pendingPhoneNumber, pendingErBeds, pendingErDoctors, pendingUid, pendingUserEmail);
                            } else {
                                Toast.makeText(Hospital_Registration.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        Log.d("Hospital_Registration", "onCodeSent called - verificationId: " + verificationId);
                        dismissProgressDialog();
                        mVerificationId = verificationId;
                        mResendToken = token;

                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(Hospital_Registration.this, getString(R.string.verification_code_sent), Toast.LENGTH_SHORT).show();
                            showVerificationCodeInputDialog();
                        }
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }
    
    private void showVerificationCodeInputDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Verification Code");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Submit", (dialog, which) -> {
            if (!isFinishing() && !isDestroyed()) {
                String code = input.getText().toString();
                if (!TextUtils.isEmpty(code)) {
                    PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
                    linkPhoneWithCurrentUser(credential);
                }
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        try {
            builder.show();
        } catch (Exception e) {
            Log.e("Hospital_Registration", "Error showing dialog: " + e.getMessage());
        }
    }

    private void linkPhoneWithCurrentUser(PhoneAuthCredential credential) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            user.linkWithCredential(credential)
                    .addOnCompleteListener(this, task -> {
                        if (!isFinishing() && !isDestroyed()) {
                            if (task.isSuccessful()) {
                                Toast.makeText(Hospital_Registration.this, getString(R.string.phone_verified), Toast.LENGTH_SHORT).show();
                                saveUserData(pendingHospitalName, pendingAddress, pendingPhoneNumber, pendingErBeds, pendingErDoctors, pendingUid, pendingUserEmail);
                            } else {
                                // Check if error is due to MFA incompatibility
                                String errorMsg = task.getException() != null ? task.getException().getMessage() : "";
                                if (errorMsg.toLowerCase().contains("first factor") || 
                                    errorMsg.toLowerCase().contains("sms based mfa") ||
                                    errorMsg.toLowerCase().contains("multi-factor") ||
                                    errorMsg.toLowerCase().contains("second factor")) {
                                    // MFA conflict - phone verified but can't link to email account
                                    // Save phone number to Firestore anyway (for contact purposes)
                                    Log.d("Hospital_Registration", "Phone verified but can't link due to MFA. Saving to Firestore only.");
                                    Toast.makeText(Hospital_Registration.this, getString(R.string.phone_verified), Toast.LENGTH_SHORT).show();
                                    saveUserData(pendingHospitalName, pendingAddress, pendingPhoneNumber, pendingErBeds, pendingErDoctors, pendingUid, pendingUserEmail);
                                } else {
                                    Toast.makeText(Hospital_Registration.this, String.format(getString(R.string.phone_verification_failed_format), task.getException().getMessage()), Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    });
        }
    }

}