package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import android.util.Log;
import android.app.ProgressDialog;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.FirebaseTooManyRequestsException;

public class Rescuer_Registration extends BaseRescuerActivity {
    
    private static final String TAG = "RescuerRegistration";

    FirebaseFirestore db;
    String userType = "rescuer";

    private EditText rescueGroupName;
    private EditText headquartersAddress;
    private EditText primaryContactPerson;
    private EditText contactNumber;

    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_rescuer_registration);
        
        // Enable smooth scrolling
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        db = FirebaseFirestore.getInstance();

        rescueGroupName = findViewById(R.id.rescue_group_name);
        headquartersAddress = findViewById(R.id.headquarters_address);
        primaryContactPerson = findViewById(R.id.primary_contact_person);
        contactNumber = findViewById(R.id.contact_number);
        EditText newPassword = findViewById(R.id.newPassword);
        EditText confirmNewPassword = findViewById(R.id.confirmNewPassword);

        Button submitRescuer = findViewById(R.id.submit_rescuer);

        // Setup real-time password validation
        setupPasswordValidation(newPassword, confirmNewPassword);

        submitRescuer.setOnClickListener(v -> {
            String groupname = rescueGroupName.getText().toString().trim();
            String headquarters = headquartersAddress.getText().toString().trim();
            String contact = primaryContactPerson.getText().toString().trim();
            String number = contactNumber.getText().toString().trim();
            String password = newPassword.getText().toString().trim();
            String confirmPassword = confirmNewPassword.getText().toString().trim();
            
            if (groupname.isEmpty() || headquarters.isEmpty() || contact.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(Rescuer_Registration.this, getString(R.string.fill_all_required_fields), Toast.LENGTH_SHORT).show();
                return;
            }

            // Only validate phone number if it's provided
            if (!number.isEmpty() && !isValidPhoneNumber(number)) {
                Toast.makeText(Rescuer_Registration.this, getString(R.string.valid_mobile_error), Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate password
            String passwordError = validatePassword(password, confirmPassword);
            if (passwordError != null) {
                Toast.makeText(Rescuer_Registration.this, passwordError, Toast.LENGTH_SHORT).show();
                return;
            }


            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                Toast.makeText(Rescuer_Registration.this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
                return;
            }

            // Change password first
            user.updatePassword(password)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Password updated successfully, now proceed with phone verification if number provided
                            if (!number.isEmpty()) {
                                verifyPhoneNumber(number);
                            } else {
                                // Skip phone verification and save data directly
                                saveUserDataToFirestore();
                            }
                        } else {
                            Toast.makeText(Rescuer_Registration.this, String.format(getString(R.string.failed_to_update_password_format), task.getException().getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        });


    }

    private void handleVerificationError(FirebaseException e) {
        if (isFinishing() || isDestroyed()) return;
        
        String errorMessage;
        if (e instanceof FirebaseTooManyRequestsException) {
            errorMessage = "Too many attempts. Please try again in 30 minutes.";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = "Invalid phone number format. Please check and try again.";
        } else if (e.getLocalizedMessage() != null && e.getLocalizedMessage().contains("quota")) {
            errorMessage = "Verification quota exceeded. Please try again later.";
        } else {
            errorMessage = "Verification failed: " + e.getMessage();
        }
        
        Log.e(TAG, "Verification failed", e);
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                new AlertDialog.Builder(Rescuer_Registration.this)
                    .setTitle("Verification Error")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK", null)
                    .show();
            }
        });
    }

    private void verifyPhoneNumber(String phoneNumber) {
        try {
            // Ensure proper formatting
            String formattedNumber = phoneNumber.trim();
            if (formattedNumber.startsWith("0")) {
                formattedNumber = formattedNumber.substring(1);
            }
            if (!formattedNumber.startsWith("+")) {
                formattedNumber = "+63" + formattedNumber;
            }
            
            Log.d(TAG, "Verifying phone number: " + formattedNumber);
            
            // Show loading dialog
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Sending verification code...");
            progressDialog.setCancelable(false);
            progressDialog.show();
            
            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                    .setPhoneNumber(formattedNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override
                        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(Rescuer_Registration.this, "Verification completed", Toast.LENGTH_SHORT).show();
                                linkPhoneWithCurrentUser(credential);
                            }
                        }

                        @Override
                        public void onVerificationFailed(@NonNull FirebaseException e) {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            handleVerificationError(e);
                        }

                        @Override
                        public void onCodeSent(@NonNull String verificationId,
                                             @NonNull PhoneAuthProvider.ForceResendingToken token) {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                            mVerificationId = verificationId;
                            mResendToken = token;
                            

                            if (!isFinishing() && !isDestroyed()) {
                                showVerificationCodeInputDialog();
                            }
                        }
                    })
                    .build();
                    
            PhoneAuthProvider.verifyPhoneNumber(options);
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying phone number", e);
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
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
            Log.e("Rescuer_Registration", "Error showing dialog: " + e.getMessage());
        }
    }

    private void linkPhoneWithCurrentUser(PhoneAuthCredential credential) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.linkWithCredential(credential)
                    .addOnCompleteListener(this, task -> {
                        if (!isFinishing() && !isDestroyed()) {
                            if (task.isSuccessful()) {
                                Toast.makeText(Rescuer_Registration.this, getString(R.string.phone_verified), Toast.LENGTH_SHORT).show();
                                saveUserDataToFirestore();
                            } else {
                                Toast.makeText(Rescuer_Registration.this, String.format(getString(R.string.phone_verification_failed_format), task.getException().getMessage()), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void saveUserDataToFirestore() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        String userEmail = user.getEmail(); // Get the email used for login

        String groupname = rescueGroupName.getText().toString().trim();
        String headquarters = headquartersAddress.getText().toString().trim();
        String contact = primaryContactPerson.getText().toString().trim();
        String number = contactNumber.getText().toString().trim();

        Map<String, Object> usrData = new HashMap<>();
        usrData.put("rescuegroup", groupname);
        usrData.put("headquarters", headquarters);
        usrData.put("contactPerson", contact);
        usrData.put("mobileNumber", number);
        if (userEmail != null && !userEmail.isEmpty()) {
            usrData.put("email", userEmail);
        }
        usrData.put("user-type", userType);
        usrData.put("status", "registered");

        Toast.makeText(Rescuer_Registration.this, getString(R.string.starting_registration_process), Toast.LENGTH_SHORT).show();

        // Step 1: Save to Firestore with admin-provided email first
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .set(usrData, SetOptions.merge()) // UPDATE instead of overwrite
                .addOnCompleteListener(task -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (task.isSuccessful()) {
                            Toast.makeText(Rescuer_Registration.this, getString(R.string.data_saved_successfully), Toast.LENGTH_SHORT).show();
                            
                            // Step 2: Admin-provided email is stored in Firestore only
                            // Firebase Auth email remains as phone number (already verified)
                            // This avoids verification requirements and maintains admin control
                            Toast.makeText(Rescuer_Registration.this,
                                    "Registration complete! Redirecting to dashboard...", 
                                    Toast.LENGTH_LONG).show();
                            
                            // Redirect to rescuer dashboard
                            Intent dashboardIntent = new Intent(Rescuer_Registration.this, Rescuer_Dashboard.class);
                            startActivity(dashboardIntent);
                            finish();
                        } else {
                            String errorMsg = "Update failed: " + task.getException().getMessage();
                            Toast.makeText(Rescuer_Registration.this, errorMsg, Toast.LENGTH_LONG).show();
                            Log.e("Rescuer_Registration", errorMsg, task.getException());
                        }
                    }
                });
    }

    private boolean isValidPhoneNumber(String number) {
        return !TextUtils.isEmpty(number) && number.matches("09\\d{9}");
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
