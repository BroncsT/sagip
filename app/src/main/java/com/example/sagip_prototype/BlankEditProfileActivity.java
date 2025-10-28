package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blank_edit_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        ImageButton backButton = findViewById(R.id.backButton);
        MaterialButton saveButton = findViewById(R.id.saveButton);
        rescueTeamNameText = findViewById(R.id.rescueTeamNameText);
        addressInput = findViewById(R.id.addressInput);
        contactPersonInput = findViewById(R.id.contactPersonInput);
        emailInput = findViewById(R.id.emailInput);
        phoneNumberInput = findViewById(R.id.phoneNumberInput);

        // Load user data based on user type
        loadUserData();

        // Back button functionality
        backButton.setOnClickListener(v -> {
            finish();
        });

        // Save button functionality
        saveButton.setOnClickListener(v -> {
            saveContactInformation();
        });
    }

    private void loadUserData() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "User type not found", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Unknown user type", Toast.LENGTH_SHORT).show();
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
                    } else {
                        phoneNumberInput.setText("");
                    }

                    Log.d(TAG, "Rescuer data loaded and displayed successfully");
                } else {
                    Log.e(TAG, "Rescuer document does not exist");
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading rescuer data: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                    } else {
                        phoneNumberInput.setText("");
                    }

                    Log.d(TAG, "Hospital data loaded and displayed successfully");
                } else {
                    Log.e(TAG, "Hospital document does not exist");
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading hospital data: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                    } else {
                        phoneNumberInput.setText("");
                    }

                    Log.d(TAG, "Barangay data loaded and displayed successfully");
                } else {
                    Log.e(TAG, "Barangay document does not exist");
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading barangay data: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void saveContactInformation() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        String address = addressInput.getText().toString().trim();
        String contactPerson = contactPersonInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String phone = phoneNumberInput.getText().toString().trim();

        // Get user type
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userType = prefs.getString("user_type", null);
        
        if (userType == null) {
            android.content.SharedPreferences sagipPrefs = getSharedPreferences("SagipAppPrefs", MODE_PRIVATE);
            userType = sagipPrefs.getString("userType", null);
        }

        if (userType == null) {
            Toast.makeText(this, "User type not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save based on user type (only update fields that have values)
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

    private void saveRescuerData(String uid, String address, String contactPerson, String email, String phone) {
        // Check if email has changed
        boolean emailChanged = !email.isEmpty() && !email.equals(originalEmail);
        
        if (emailChanged) {
            // Show confirmation dialog for email change
            new AlertDialog.Builder(this)
                .setTitle("Verify Email Change")
                .setMessage("Changing your email requires verification. You will be logged out and need to verify your new email before logging in again. Continue?")
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
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
            });
    }

    private void saveHospitalData(String uid, String address, String email, String phone) {
        // Check if email has changed
        boolean emailChanged = !email.isEmpty() && !email.equals(originalEmail);
        
        if (emailChanged) {
            // Show confirmation dialog for email change
            new AlertDialog.Builder(this)
                .setTitle("Verify Email Change")
                .setMessage("Changing your email requires verification. You will be logged out and need to verify your new email before logging in again. Continue?")
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
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
            });
    }

    private void saveBarangayData(String uid, String address, String contactPerson, String email, String phone) {
        // Check if email has changed
        boolean emailChanged = !email.isEmpty() && !email.equals(originalEmail);
        
        if (emailChanged) {
            // Show confirmation dialog for email change
            new AlertDialog.Builder(this)
                .setTitle("Verify Email Change")
                .setMessage("Changing your email requires verification. You will be logged out and need to verify your new email before logging in again. Continue?")
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
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
            });
    }
    
    private void updateEmailAndSaveData(String uid, String address, String contactPerson, String email, String phone, String userType) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(this, "Failed to update email in authentication system. Email will only be updated in contact info.", Toast.LENGTH_LONG).show();
                            // Still update Firestore as contact info
                            updateFirestoreEmailOnly(uid, address, contactPerson, email, phone, userType);
                        });
                } else {
                    Toast.makeText(this, "Failed to update email: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                        .setTitle("Email Updated")
                        .setMessage("Your email has been updated to " + email + ". A verification email has been sent. Please verify your email to use it for login.\n\nYou will be logged out now.")
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
                Toast.makeText(this, "Failed to save data: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        
        // Redirect to main activity (which will redirect to login)
        Intent loginIntent = new Intent(this, MainActivity.class);
        loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(loginIntent);
        finish();
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

