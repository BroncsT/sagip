package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class Senior_Registration extends AppCompatActivity {

    FirebaseAuth auth;
    FirebaseFirestore db;

    String userType = "seniors";
    private Spinner barangaySpinner;
    private String selectedBarangay = "";
    
    // List of all barangays in Angeles City
    private final String[] barangays = {
        "-- Select your Barangay --",
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
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_senior_registration);

        // View references
        EditText birthdayMonth = findViewById(R.id.birthdayMonth);
        EditText birthdayDay = findViewById(R.id.birthdayDay);
        EditText birthdayYear = findViewById(R.id.birthdayYear);
        EditText getFirstName = findViewById(R.id.firstName);
        EditText getMiddleName = findViewById(R.id.middleName);
        EditText getLastName = findViewById(R.id.lastName);
        barangaySpinner = findViewById(R.id.barangaySpinner);
        EditText getEmailAddress = findViewById(R.id.emailAddress);
        TextView getMobileNumber = findViewById(R.id.mobileNumber);
        Button continueButton = findViewById(R.id.addEmerContact);
        
        // Note: Step indicator views (stepNumberText, stepTitleText) removed from layout
        
        // Firebase initialization
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get passed phone number from intent
        String number = getIntent().getStringExtra("MOBILE_NUMBER");
        getMobileNumber.setText(number);
        
        // Setup barangay spinner
        setupBarangaySpinner();
        // Save data on button click
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String firstName = getFirstName.getText().toString().trim();
                String middleName = getMiddleName.getText().toString().trim();
                String lastName = getLastName.getText().toString().trim();
                String month = birthdayMonth.getText().toString().trim();
                String day = birthdayDay.getText().toString().trim();
                String year = birthdayYear.getText().toString().trim();
                String birthday = month + " - " + day + " - " + year;
                String emailAddress = getEmailAddress.getText().toString().trim();
                String mobileNumber = getMobileNumber.getText().toString().trim();

                if (firstName.isEmpty() || lastName.isEmpty() || month.isEmpty() || day.isEmpty() || year.isEmpty() || selectedBarangay.isEmpty() || selectedBarangay.equals("BARANGAY")) {
                    Toast.makeText(Senior_Registration.this, getString(R.string.error_fill_all_fields_barangay), Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate age - must be 60 years old or above
                try {
                    int birthYear = Integer.parseInt(year);
                    int birthMonth = Integer.parseInt(month);
                    int birthDay = Integer.parseInt(day);

                    Calendar today = Calendar.getInstance();
                    Calendar birthDate = Calendar.getInstance();
                    birthDate.set(birthYear, birthMonth - 1, birthDay);

                    int age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR);
                    if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
                        age--;
                    }

                    if (age < 60) {
                        Toast.makeText(Senior_Registration.this, "You must be 60 years old or above to register as a senior.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(Senior_Registration.this, "Please enter a valid date of birth.", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = auth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Senior_Registration.this, getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = user.getUid();
                String userEmail = user.getEmail(); // Get the email used for login

                // ✅ Check if mobile number already exists
                db.collection("Sagip")
                        .document("users")
                        .collection(userType)
                        .whereEqualTo("mobileNumber", mobileNumber)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            if (!queryDocumentSnapshots.isEmpty()) {
                                // Mobile number already exists
                                Toast.makeText(Senior_Registration.this,
                                        "This mobile number is already registered.",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                // Proceed with registration
                                Map<String, Object> usrData = new HashMap<>();
                                usrData.put("firstName", firstName);
                                usrData.put("middleName", middleName);
                                usrData.put("lastName", lastName);
                                usrData.put("birthday", birthday);
                                usrData.put("barangay", selectedBarangay);
                                usrData.put("mobileNumber", mobileNumber);
                                if (userEmail != null && !userEmail.isEmpty()) {
                                    usrData.put("email", userEmail);
                                }
                                usrData.put("status", "pending");
                                usrData.put("user-type", userType);

                                db.collection("Sagip")
                                        .document("users")
                                        .collection(userType)
                                        .document(uid)
                                        .set(usrData)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    Intent intent = new Intent(Senior_Registration.this,
                                                            Verification_Page.class);
                                                    Toast.makeText(Senior_Registration.this, getString(R.string.verification_process), Toast.LENGTH_SHORT).show();
                                                    startActivity(intent);
                                                    finish();
                                                } else {
                                                    Toast.makeText(Senior_Registration.this,
                                                            "Failed to save data: " + task.getException().getMessage(),
                                                            Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(Senior_Registration.this,
                                    "Error checking mobile number: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
            }
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
