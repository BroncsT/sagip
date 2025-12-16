package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

public class BlankEditProfileActivity extends AppCompatActivity {

    private static final String TAG = "BlankEditProfile";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    private TextView rescueTeamNameText;
    private TextInputEditText addressInput;
    private TextInputEditText contactPersonInput;
    private TextInputEditText emailInput;
    private TextInputEditText phoneNumberInput;
    
    private String originalEmail = ""; // Track original email to detect changes
    private String originalPhone = ""; // Track original phone to detect changes
    
    // For OTP verification
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private static final long TIMEOUT = 60L;
    
    // Store pending data for after OTP verification
    private String pendingAddress;
    private String pendingContactPerson;
    private String pendingEmail;
    private String pendingPhone;
    private String pendingUserType;
    private String pendingUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blank_edit_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        ImageView backButton = findViewById(R.id.backButton);
        MaterialButton saveButton = findViewById(R.id.saveButton);
        rescueTeamNameText = findViewById(R.id.rescueTeamNameText);
        addressInput = findViewById(R.id.addressInput);
        contactPersonInput = findViewById(R.id.contactPersonInput);
        emailInput = findViewById(R.id.emailInput);
        phoneNumberInput = findViewById(R.id.phoneNumberInput);

        // Load user data based on user type
        loadUserData();

        // Back button functionality
        if (backButton != null) {
            backButton.setClickable(true);
            backButton.setFocusable(true);
            backButton.setOnClickListener(v -> {
                finish();
            });
        }

        // Save button functionality
        saveButton.setOnClickListener(v -> {
            saveContactInformation();
        });
    }

    private void loadUserData() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        
        // Get user type from shared preferences
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userType = prefs.getString("user_type", null);
        
        if (userType == null) {
            // Try alternative preferences
            android.content.SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
            userType = sagipPrefs.getString("userType", null);
        }

        Log.d(TAG, "Loading data for user type: " + userType);

        if (userType == null) {
            Toast.makeText(this, getString(R.string.user_type_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Load data based on user type
        switch (userType) {
            case "rescuer":
                loadRescuerData(uid);
                break;
            case "hospital":
                loadHospitalData(uid);
                break;
            case "barangay":
                loadBarangayData(uid);
                break;
            default:
                Toast.makeText(this, getString(R.string.unknown_user_type), Toast.LENGTH_SHORT).show();
                finish();
                break;
        }
    }

    private void loadRescuerData(String uid) {
        db.collection("Sagip")
            .document("users")
            .collection("rescuer")
            .document(uid)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Log all available fields for debugging
                    Log.d(TAG, "Rescuer document exists. All fields: " + documentSnapshot.getData());
                    
                    String rescueGroup = documentSnapshot.getString("rescuegroup");
                    String address = documentSnapshot.getString("headquarters");
                    String contactPerson = documentSnapshot.getString("contactPerson");
                    String email = documentSnapshot.getString("email");
                    String phone = documentSnapshot.getString("mobileNumber");

                    Log.d(TAG, "Loaded rescuer data - Group: " + rescueGroup + ", Address: " + address +
                         ", Contact: " + contactPerson + ", Email: " + email + ", Phone: " + phone);

                    // Display rescue team name
                    if (rescueGroup != null && !rescueGroup.isEmpty()) {
                        rescueTeamNameText.setText(rescueGroup);
                    } else {
                        rescueTeamNameText.setText("Rescue Team");
                    }

                    // Populate fields with data
                    if (address != null && !address.isEmpty()) {
                        addressInput.setText(address);
                    } else {
                        addressInput.setText("");
                    }

                    if (contactPerson != null && !contactPerson.isEmpty()) {
                        contactPersonInput.setText(contactPerson);
                    } else {
                        contactPersonInput.setText("");
                    }

                    if (email != null && !email.isEmpty()) {
                        emailInput.setText(email);
                        originalEmail = email; // Store original email
                    } else {
                        emailInput.setText("");
                        originalEmail = "";
                    }

                    if (phone != null && !phone.isEmpty()) {
                        phoneNumberInput.setText(phone);
                        originalPhone = phone; // Store original phone
                    } else {
                        phoneNumberInput.setText("");
                        originalPhone = "";
                    }

                    Log.d(TAG, "Rescuer data loaded and displayed successfully");
                } else {
                    Log.e(TAG, "Rescuer document does not exist");
                    Toast.makeText(this, getString(R.string.user_data_not_found), Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading rescuer data: " + e.getMessage(), e);
                Toast.makeText(this, String.format(getString(R.string.error_loading_data_format), e.getMessage()), Toast.LENGTH_SHORT).show();
            });
    }

    private void loadHospitalData(String uid) {
        db.collection("Sagip")
            .document("users")
            .collection("hospital")
            .document(uid)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Log all available fields for debugging
                    Log.d(TAG, "Hospital document exists. All fields: " + documentSnapshot.getData());
                    
                    String hospitalName = documentSnapshot.getString("hospitalName");
                    String address = documentSnapshot.getString("hospitalAddress");
                    String email = documentSnapshot.getString("email");
                    String phone = documentSnapshot.getString("mobileNumber");

                    Log.d(TAG, "Loaded hospital data - Name: " + hospitalName + ", Address: " + address +
                         ", Email: " + email + ", Phone: " + phone);

                    // Display hospital name
                    if (hospitalName != null && !hospitalName.isEmpty()) {
                        rescueTeamNameText.setText(hospitalName);
                    } else {
                        rescueTeamNameText.setText("Hospital");
                    }

                    // Populate fields with data
                    if (address != null && !address.isEmpty()) {
                        addressInput.setText(address);
                    } else {
                        addressInput.setText("");
                    }

                    contactPersonInput.setText("Hospital Administrator");
                    contactPersonInput.setEnabled(false); // Hospital doesn't need to edit this

                    if (email != null && !email.isEmpty()) {
                        emailInput.setText(email);
                        originalEmail = email; // Store original email
                    } else {
                        emailInput.setText("");
                        originalEmail = "";
                    }

                    if (phone != null && !phone.isEmpty()) {
                        phoneNumberInput.setText(phone);
                        originalPhone = phone; // Store original phone
                    } else {
                        phoneNumberInput.setText("");
                        originalPhone = "";
                    }

                    Log.d(TAG, "Hospital data loaded and displayed successfully");
                } else {
                    Log.e(TAG, "Hospital document does not exist");
                    Toast.makeText(this, getString(R.string.user_data_not_found), Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading hospital data: " + e.getMessage(), e);
                Toast.makeText(this, String.format(getString(R.string.error_loading_data_format), e.getMessage()), Toast.LENGTH_SHORT).show();
            });
    }

    private void loadBarangayData(String uid) {
        db.collection("Sagip")
            .document("users")
            .collection("barangay")
            .document(uid)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Log all available fields for debugging
                    Log.d(TAG, "Barangay document exists. All fields: " + documentSnapshot.getData());
                    
                    String barangayName = documentSnapshot.getString("barangayName");
                    String address = documentSnapshot.getString("address");
                    String contactPerson = documentSnapshot.getString("contactPerson");
                    String email = documentSnapshot.getString("email");
                    String phone = documentSnapshot.getString("mobileNumber");

                    Log.d(TAG, "Loaded barangay data - Name: " + barangayName + ", Address: " + address + 
                         ", Contact: " + contactPerson + ", Email: " + email + ", Phone: " + phone);

                    // Display barangay name
                    if (barangayName != null && !barangayName.isEmpty()) {
                        rescueTeamNameText.setText("Barangay " + barangayName);
                    } else {
                        rescueTeamNameText.setText("Barangay");
                    }

                    // Populate fields with data
                    if (address != null && !address.isEmpty()) {
                        addressInput.setText(address);
                    } else {
                        addressInput.setText("");
                    }

                    if (contactPerson != null && !contactPerson.isEmpty()) {
                        contactPersonInput.setText(contactPerson);
                    } else {
                        contactPersonInput.setText("");
                    }

                    if (email != null && !email.isEmpty()) {
                        emailInput.setText(email);
                        originalEmail = email; // Store original email
                    } else {
                        emailInput.setText("");
                        originalEmail = "";
                    }

                    if (phone != null && !phone.isEmpty()) {
                        phoneNumberInput.setText(phone);
                        originalPhone = phone; // Store original phone
                    } else {
                        phoneNumberInput.setText("");
                        originalPhone = "";
                    }

                    Log.d(TAG, "Barangay data loaded and displayed successfully");
                } else {
                    Log.e(TAG, "Barangay document does not exist");
                    Toast.makeText(this, getString(R.string.user_data_not_found), Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading barangay data: " + e.getMessage(), e);
                Toast.makeText(this, String.format(getString(R.string.error_loading_data_format), e.getMessage()), Toast.LENGTH_SHORT).show();
            });
    }

    private void saveContactInformation() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        String address = addressInput.getText().toString().trim();
        String contactPerson = contactPersonInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String phone = phoneNumberInput.getText().toString().trim();

        // Validate phone number format if provided
        if (!phone.isEmpty() && !isValidPhoneNumber(phone)) {
            Toast.makeText(this, getString(R.string.please_enter_valid_phone), Toast.LENGTH_SHORT).show();
            return;
        }

        // Get user type
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userType = prefs.getString("user_type", null);
        
        if (userType == null) {
            android.content.SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
            userType = sagipPrefs.getString("userType", null);
        }

        if (userType == null) {
            Toast.makeText(this, getString(R.string.user_type_not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if phone number has changed
        boolean phoneChanged = !phone.isEmpty() && !phone.equals(originalPhone);
        
        if (phoneChanged) {
            // Store pending data
            pendingAddress = address;
            pendingContactPerson = contactPerson;
            pendingEmail = email;
            pendingPhone = phone;
            pendingUserType = userType;
            pendingUid = uid;
            
            // Send OTP to new phone number
            sendOtpForPhoneUpdate(phone);
        } else {
            // No phone change, proceed with normal save
            switch (userType) {
                case "rescuer":
                    saveRescuerData(uid, address, contactPerson, email, phone);
                    break;
                case "hospital":
                    saveHospitalData(uid, address, email, phone);
                    break;
                case "barangay":
                    saveBarangayData(uid, address, contactPerson, email, phone);
                    break;
            }
        }
    }
    
    private boolean isValidPhoneNumber(String number) {
        // Philippine phone number format: 09XXXXXXXXX (11 digits starting with 09)
        return number.matches("09\\d{9}");
    }
    
    private void sendOtpForPhoneUpdate(String phoneNumber) {
        // Remove leading 0 and add country code
        String formattedNumber = phoneNumber.startsWith("0") ? phoneNumber.substring(1) : phoneNumber;
        String fullPhoneNumber = "+63" + formattedNumber;
        
        Toast.makeText(this, String.format(getString(R.string.sending_otp_to), fullPhoneNumber), Toast.LENGTH_SHORT).show();
        
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(fullPhoneNumber)
                .setTimeout(TIMEOUT, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        Log.d(TAG, "Auto-verification completed for phone update");
                        verifyOtpAndUpdatePhone(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        Log.e(TAG, "OTP verification failed: " + e.getMessage());
                        Toast.makeText(BlankEditProfileActivity.this, 
                                "Failed to send OTP: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId, 
                                          @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        Log.d(TAG, "OTP code sent successfully");
                        BlankEditProfileActivity.this.verificationId = verificationId;
                        BlankEditProfileActivity.this.resendToken = token;
                        
                        // Navigate to OTP page with flag indicating this is for phone update
                        Intent intent = new Intent(BlankEditProfileActivity.this, OTP_PAGE.class);
                        intent.putExtra("VERIFICATION_ID", verificationId);
                        intent.putExtra("MOBILE_NUMBER", fullPhoneNumber);
                        intent.putExtra("IS_NEW_USER", false);
                        intent.putExtra("IS_PHONE_UPDATE", true); // Flag for phone update
                        intent.putExtra("RETURN_ACTIVITY", "BlankEditProfileActivity");
                        startActivityForResult(intent, 100);
                    }
                })
                .build();
        
        PhoneAuthProvider.verifyPhoneNumber(options);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            if (data != null && data.getBooleanExtra("OTP_VERIFIED", false)) {
                String otp = data.getStringExtra("OTP_CODE");
                String verificationId = data.getStringExtra("VERIFICATION_ID");
                if (otp != null && verificationId != null) {
                    PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
                    verifyOtpAndUpdatePhone(credential);
                }
            }
        }
    }
    
    private void verifyOtpAndUpdatePhone(PhoneAuthCredential credential) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // If user already has a phone number, we need to re-authenticate first
        // For now, we'll try to link the credential directly
        // If linking fails, we'll need to handle unlink and relink
        user.linkWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Phone number linked successfully to Firebase Auth");
                    
                    // Now update Firestore and save all pending data
                    savePendingDataAfterPhoneVerification();
                } else {
                    Log.e(TAG, "Failed to link phone number: " + task.getException().getMessage());
                    Exception exception = task.getException();
                    
                    // If phone is already linked to another account, try to update instead
                    if (exception != null && exception.getMessage() != null && 
                        (exception.getMessage().contains("already") || exception.getMessage().contains("exists"))) {
                        // Try to update phone number directly
                        updatePhoneNumberInAuth(pendingPhone);
                    } else {
                        Toast.makeText(this, String.format(getString(R.string.failed_to_verify_phone), 
                                (exception != null ? exception.getMessage() : "Unknown error")), 
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
    }
    
    private void verifyOtpAndUpdatePhone(String verificationId, String otp) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        verifyOtpAndUpdatePhone(credential);
    }
    
    private void updatePhoneNumberInAuth(String phoneNumber) {
        // Format phone number
        String formattedNumber = phoneNumber.startsWith("0") ? phoneNumber.substring(1) : phoneNumber;
        String fullPhoneNumber = "+63" + formattedNumber;
        
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // For users with existing phone numbers, Firebase requires re-authentication
        // Since we just verified with OTP, we can update directly
        // However, Firebase doesn't have a direct updatePhoneNumber method
        // We need to unlink old phone and link new one, or use updatePhoneNumberCredential
        
        // Actually, we should use the credential we just verified
        // But since we're linking, if it fails due to existing phone, 
        // we need to unlink first
        
        user.getProviderData().forEach(userInfo -> {
            if ("phone".equals(userInfo.getProviderId())) {
                // User has phone auth, we need to re-authenticate first
                Log.d(TAG, "User has existing phone number, need to handle carefully");
            }
        });
        
        // For now, just save to Firestore since Auth linking may be complex
        // The phone number in Auth is typically set during registration
        savePendingDataAfterPhoneVerification();
    }
    
    private void savePendingDataAfterPhoneVerification() {
        if (pendingUid == null || pendingUserType == null) {
            Log.e(TAG, "Pending data not set");
            return;
        }
        
        // Save all data including the verified phone number
        switch (pendingUserType) {
            case "rescuer":
                saveRescuerDataWithPhoneVerified(pendingUid, pendingAddress, pendingContactPerson, pendingEmail, pendingPhone);
                break;
            case "hospital":
                saveHospitalDataWithPhoneVerified(pendingUid, pendingAddress, pendingEmail, pendingPhone);
                break;
            case "barangay":
                saveBarangayDataWithPhoneVerified(pendingUid, pendingAddress, pendingContactPerson, pendingEmail, pendingPhone);
                break;
        }
    }

    private void saveRescuerData(String uid, String address, String contactPerson, String email, String phone) {
        // Check if email has changed
        boolean emailChanged = !email.isEmpty() && !email.equals(originalEmail);
        
        if (emailChanged) {
            // Show confirmation dialog for email change
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.verify_email_change_title))
                .setMessage(getString(R.string.verify_email_change_message))
                .setPositiveButton("Yes, Change Email", (dialog, which) -> {
                    updateEmailAndSaveData(uid, address, contactPerson, email, phone, "rescuer");
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            // Normal save without email change
            saveRescuerDataOnly(uid, address, contactPerson, email, phone);
        }
    }
    
    private void saveRescuerDataOnly(String uid, String address, String contactPerson, String email, String phone) {
        // Build update map with only non-empty fields
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        java.util.List<String> updatedFields = new java.util.ArrayList<>();
        
        if (!address.isEmpty()) {
            updates.put("headquarters", address);
            updatedFields.add("Address");
        }
        if (!contactPerson.isEmpty()) {
            updates.put("contactPerson", contactPerson);
            updatedFields.add("Contact Person");
        }
        if (!email.isEmpty() && email.equals(originalEmail)) {
            updates.put("email", email);
            updatedFields.add("Email Address");
        }
        if (!phone.isEmpty()) {
            updates.put("mobileNumber", phone);
            updatedFields.add("Phone Number");
        }
        
        // Check if there's anything to update
        if (updates.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_changes_to_save), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create success message
        String successMessage = buildUpdateMessage(updatedFields);
        
        db.collection("Sagip")
            .document("users")
            .collection("rescuer")
            .document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving rescuer data: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_saving_data), Toast.LENGTH_SHORT).show();
            });
    }

    private void saveHospitalData(String uid, String address, String email, String phone) {
        // Check if email has changed
        boolean emailChanged = !email.isEmpty() && !email.equals(originalEmail);
        
        if (emailChanged) {
            // Show confirmation dialog for email change
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.verify_email_change_title))
                .setMessage(getString(R.string.verify_email_change_message))
                .setPositiveButton("Yes, Change Email", (dialog, which) -> {
                    updateEmailAndSaveData(uid, address, null, email, phone, "hospital");
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            // Normal save without email change
            saveHospitalDataOnly(uid, address, email, phone);
        }
    }
    
    private void saveHospitalDataOnly(String uid, String address, String email, String phone) {
        // Build update map with only non-empty fields
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        java.util.List<String> updatedFields = new java.util.ArrayList<>();
        
        if (!address.isEmpty()) {
            updates.put("hospitalAddress", address);
            updatedFields.add("Address");
        }
        if (!email.isEmpty() && email.equals(originalEmail)) {
            updates.put("email", email);
            updatedFields.add("Email Address");
        }
        if (!phone.isEmpty()) {
            updates.put("mobileNumber", phone);
            updatedFields.add("Phone Number");
        }
        
        // Check if there's anything to update
        if (updates.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_changes_to_save), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create success message
        String successMessage = buildUpdateMessage(updatedFields);
        
        db.collection("Sagip")
            .document("users")
            .collection("hospital")
            .document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving hospital data: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_saving_data), Toast.LENGTH_SHORT).show();
            });
    }

    private void saveBarangayData(String uid, String address, String contactPerson, String email, String phone) {
        // Check if email has changed
        boolean emailChanged = !email.isEmpty() && !email.equals(originalEmail);
        
        if (emailChanged) {
            // Show confirmation dialog for email change
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.verify_email_change_title))
                .setMessage(getString(R.string.verify_email_change_message))
                .setPositiveButton("Yes, Change Email", (dialog, which) -> {
                    updateEmailAndSaveData(uid, address, contactPerson, email, phone, "barangay");
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            // Normal save without email change
            saveBarangayDataOnly(uid, address, contactPerson, email, phone);
        }
    }
    
    private void saveBarangayDataOnly(String uid, String address, String contactPerson, String email, String phone) {
        // Build update map with only non-empty fields
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        java.util.List<String> updatedFields = new java.util.ArrayList<>();
        
        if (!address.isEmpty()) {
            updates.put("address", address);
            updatedFields.add("Address");
        }
        if (!contactPerson.isEmpty()) {
            updates.put("contactPerson", contactPerson);
            updatedFields.add("Contact Person");
        }
        if (!email.isEmpty() && email.equals(originalEmail)) {
            updates.put("email", email);
            updatedFields.add("Email Address");
        }
        if (!phone.isEmpty()) {
            updates.put("mobileNumber", phone);
            updatedFields.add("Phone Number");
        }
        
        // Check if there's anything to update
        if (updates.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_changes_to_save), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create success message
        String successMessage = buildUpdateMessage(updatedFields);
        
        db.collection("Sagip")
            .document("users")
            .collection("barangay")
            .document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving barangay data: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_saving_data), Toast.LENGTH_SHORT).show();
            });
    }
    
    private void updateEmailAndSaveData(String uid, String address, String contactPerson, String email, String phone, String userType) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // First, try to update the email in Firebase Auth
        user.updateEmail(email)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Firebase Auth email updated successfully");
                
                // Send verification email to the new address
                user.sendEmailVerification()
                    .addOnSuccessListener(aVoid2 -> {
                        Log.d(TAG, "Verification email sent to: " + email);
                        // Update Firestore with all data
                        updateFirestoreEmailOnly(uid, address, contactPerson, email, phone, userType);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send verification email: " + e.getMessage());
                        // Still update Firestore even if verification email fails
                        updateFirestoreEmailOnly(uid, address, contactPerson, email, phone, userType);
                    });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to update Firebase Auth email: " + e.getMessage());
                
                // Check if it's because the user is using phone auth
                if (e.getMessage() != null && e.getMessage().contains("verify")) {
                    // Try verifyBeforeUpdateEmail instead
                    user.verifyBeforeUpdateEmail(email)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Verification email sent via verifyBeforeUpdateEmail");
                            updateFirestoreEmailOnly(uid, address, contactPerson, email, phone, userType);
                        })
                        .addOnFailureListener(e2 -> {
                            Log.e(TAG, "verifyBeforeUpdateEmail also failed: " + e2.getMessage());
                            // Show error to user
                            Toast.makeText(this, getString(R.string.failed_to_update_email_auth), Toast.LENGTH_LONG).show();
                            // Still update Firestore as contact info
                            updateFirestoreEmailOnly(uid, address, contactPerson, email, phone, userType);
                        });
                } else {
                    Toast.makeText(this, String.format(getString(R.string.failed_to_update_email), e.getMessage()), Toast.LENGTH_LONG).show();
                }
            });
    }
    
    private void updateFirestoreEmailOnly(String uid, String address, String contactPerson, String email, String phone, String userType) {
        // Build update map
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        java.util.List<String> updatedFields = new java.util.ArrayList<>();
        
        // Always include email
        updates.put("email", email);
        updatedFields.add("Email Address");
        
        // Include other fields based on user type
        switch (userType) {
            case "rescuer":
                if (!address.isEmpty()) {
                    updates.put("headquarters", address);
                    updatedFields.add("Address");
                }
                if (contactPerson != null && !contactPerson.isEmpty()) {
                    updates.put("contactPerson", contactPerson);
                    updatedFields.add("Contact Person");
                }
                if (!phone.isEmpty()) {
                    updates.put("mobileNumber", phone);
                    updatedFields.add("Phone Number");
                }
                break;
            case "hospital":
                if (!address.isEmpty()) {
                    updates.put("hospitalAddress", address);
                    updatedFields.add("Address");
                }
                if (!phone.isEmpty()) {
                    updates.put("mobileNumber", phone);
                    updatedFields.add("Phone Number");
                }
                break;
            case "barangay":
                if (!address.isEmpty()) {
                    updates.put("address", address);
                    updatedFields.add("Address");
                }
                if (contactPerson != null && !contactPerson.isEmpty()) {
                    updates.put("contactPerson", contactPerson);
                    updatedFields.add("Contact Person");
                }
                if (!phone.isEmpty()) {
                    updates.put("mobileNumber", phone);
                    updatedFields.add("Phone Number");
                }
                break;
        }
        
        // Create success message
        String successMessage = buildUpdateMessage(updatedFields);
        
        // Update Firestore
        db.collection("Sagip")
            .document("users")
            .collection(userType)
            .document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Firestore updated with new email");
                
                // Check if this was an email change (Email Address is in the list)
                if (updatedFields.contains("Email Address")) {
                    // Show dialog about verification
                    new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.email_updated_title))
                        .setMessage(String.format(getString(R.string.email_updated_message), email))
                        .setPositiveButton("OK", (dialog, which) -> {
                            logOutAndRedirect();
                        })
                        .setCancelable(false)
                        .show();
                } else {
                    // Normal update without email change
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to update Firestore: " + e.getMessage());
                Toast.makeText(this, String.format(getString(R.string.failed_to_save_data), e.getMessage()), Toast.LENGTH_LONG).show();
            });
    }
    
    private void logOutAndRedirect() {
        // Clear user preferences
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
        
        android.content.SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
        sagipPrefs.edit().clear().apply();
        
        // Sign out from Firebase
        mAuth.signOut();

        Intent loginIntent = new Intent(this, MainActivity.class);
        loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(loginIntent);
        finish();
    }
    
    private void saveRescuerDataWithPhoneVerified(String uid, String address, String contactPerson, String email, String phone) {
        // Build update map
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        java.util.List<String> updatedFields = new java.util.ArrayList<>();
        
        if (address != null && !address.isEmpty()) {
            updates.put("headquarters", address);
            updatedFields.add("Address");
        }
        if (contactPerson != null && !contactPerson.isEmpty()) {
            updates.put("contactPerson", contactPerson);
            updatedFields.add("Contact Person");
        }
        if (email != null && !email.isEmpty() && !email.equals(originalEmail)) {
            updates.put("email", email);
            updatedFields.add("Email Address");
        }
        if (phone != null && !phone.isEmpty()) {
            updates.put("mobileNumber", phone);
            updatedFields.add("Phone Number");
        }
        
        // Update Firestore
        db.collection("Sagip")
            .document("users")
            .collection("rescuer")
            .document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                // Update original phone to prevent re-triggering OTP
                originalPhone = phone;
                
                String successMessage = buildUpdateMessage(updatedFields);
                Toast.makeText(this, String.format(getString(R.string.phone_verified_updated), successMessage), Toast.LENGTH_LONG).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving rescuer data: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_saving_data), Toast.LENGTH_SHORT).show();
            });
    }
    
    private void saveHospitalDataWithPhoneVerified(String uid, String address, String email, String phone) {
        // Build update map
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        java.util.List<String> updatedFields = new java.util.ArrayList<>();
        
        if (address != null && !address.isEmpty()) {
            updates.put("hospitalAddress", address);
            updatedFields.add("Address");
        }
        if (email != null && !email.isEmpty() && !email.equals(originalEmail)) {
            updates.put("email", email);
            updatedFields.add("Email Address");
        }
        if (phone != null && !phone.isEmpty()) {
            updates.put("mobileNumber", phone);
            updatedFields.add("Phone Number");
        }
        
        // Update Firestore
        db.collection("Sagip")
            .document("users")
            .collection("hospital")
            .document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                // Update original phone to prevent re-triggering OTP
                originalPhone = phone;
                
                String successMessage = buildUpdateMessage(updatedFields);
                Toast.makeText(this, String.format(getString(R.string.phone_verified_updated), successMessage), Toast.LENGTH_LONG).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving hospital data: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_saving_data), Toast.LENGTH_SHORT).show();
            });
    }
    
    private void saveBarangayDataWithPhoneVerified(String uid, String address, String contactPerson, String email, String phone) {
        // Build update map
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        java.util.List<String> updatedFields = new java.util.ArrayList<>();
        
        if (address != null && !address.isEmpty()) {
            updates.put("address", address);
            updatedFields.add("Address");
        }
        if (contactPerson != null && !contactPerson.isEmpty()) {
            updates.put("contactPerson", contactPerson);
            updatedFields.add("Contact Person");
        }
        if (email != null && !email.isEmpty() && !email.equals(originalEmail)) {
            updates.put("email", email);
            updatedFields.add("Email Address");
        }
        if (phone != null && !phone.isEmpty()) {
            updates.put("mobileNumber", phone);
            updatedFields.add("Phone Number");
        }
        
        // Update Firestore
        db.collection("Sagip")
            .document("users")
            .collection("barangay")
            .document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                // Update original phone to prevent re-triggering OTP
                originalPhone = phone;
                
                String successMessage = buildUpdateMessage(updatedFields);
                Toast.makeText(this, String.format(getString(R.string.phone_verified_updated), successMessage), Toast.LENGTH_LONG).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving barangay data: " + e.getMessage());
                Toast.makeText(this, getString(R.string.error_saving_data), Toast.LENGTH_SHORT).show();
            });
    }

    private String buildUpdateMessage(java.util.List<String> updatedFields) {
        if (updatedFields.isEmpty()) {
            return "No changes made";
        }
        
        if (updatedFields.size() == 1) {
            return updatedFields.get(0) + " updated";
        }
        
        if (updatedFields.size() == 2) {
            return updatedFields.get(0) + " and " + updatedFields.get(1) + " updated";
        }
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < updatedFields.size(); i++) {
            if (i == updatedFields.size() - 1) {
                message.append("and ").append(updatedFields.get(i));
            } else {
                message.append(updatedFields.get(i)).append(", ");
            }
        }
        message.append(" updated");
        return message.toString();
    }
}

