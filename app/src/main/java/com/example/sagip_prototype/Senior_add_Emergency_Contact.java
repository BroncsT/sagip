package com.example.sagip_prototype;

import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Senior_add_Emergency_Contact extends AppCompatActivity {

    private Spinner relationshipSpinner;
    private String selectedRelationship = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_add_emergency_contact);

        EditText emerName = findViewById(R.id.emerContact_name);
        EditText emerNumber = findViewById(R.id.emerContact_Number);
        EditText emerAddress = findViewById(R.id.emerContact_add);
        relationshipSpinner = findViewById(R.id.emerContact_relationship);
        Button addEmergencyContact = findViewById(R.id.addEmerContact);
        ImageView backArrow = findViewById(R.id.backArrow);

        // Setup relationship spinner
        setupRelationshipSpinner();

        // Setup back arrow click listener
        backArrow.setOnClickListener(v -> finish());

        // Add input filter to restrict phone number input to digits only
        emerNumber.setFilters(new InputFilter[]{new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (!Character.isDigit(source.charAt(i))) {
                        return "";
                    }
                }
                return null;
            }
        }});

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();

        addEmergencyContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = emerName.getText().toString().trim();
                String number = emerNumber.getText().toString().trim();
                String address = emerAddress.getText().toString().trim();
                // Get current relationship value
                String relationship = relationshipSpinner.getSelectedItem().toString();
                
                if (name.isEmpty() || number.isEmpty() || address.isEmpty() || relationship.equals(getString(R.string.select_relationship))) {
                    Toast.makeText(Senior_add_Emergency_Contact.this, getString(R.string.toast_fill_all_fields), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isValidPhoneNumber(number)) {
                    Toast.makeText(Senior_add_Emergency_Contact.this, getString(R.string.valid_mobile_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Senior_add_Emergency_Contact.this, getString(R.string.toast_user_not_authenticated), Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = user.getUid();
                String userType = "seniors";

                // Check for duplicate phone numbers before adding
                checkForDuplicateAndAdd(uid, userType, name, number, address, relationship, db);
            }
        });
    }

    private void setupRelationshipSpinner() {
        // Create adapter for the spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.relationship_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        relationshipSpinner.setAdapter(adapter);

        // Set up spinner selection listener
        relationshipSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) { // Skip the first item which is "Select Relationship"
                    selectedRelationship = parent.getItemAtPosition(position).toString();
                } else {
                    selectedRelationship = "";
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedRelationship = "";
            }
        });
    }

    private void checkForDuplicateAndAdd(String uid, String userType, String name, String number, String address, String relationship, FirebaseFirestore db) {
        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<Map<String, Object>> existingContacts = (List<Map<String, Object>>) documentSnapshot.get("emergencyContacts");
                        
                        if (existingContacts != null) {
                            // Check for duplicate phone numbers
                            for (Map<String, Object> contact : existingContacts) {
                                String existingNumber = contact.get("number").toString();
                                if (existingNumber.equals(number)) {
                                    Toast.makeText(Senior_add_Emergency_Contact.this, getString(R.string.toast_phone_number_exists), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                            }
                        }
                        
                        // No duplicate found, add the contact
                        addEmergencyContact(uid, userType, name, number, address, relationship, db);
                    } else {
                        // No existing contacts, add the contact
                        addEmergencyContact(uid, userType, name, number, address, relationship, db);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Senior_add_Emergency_Contact.this, getString(R.string.toast_failed_check_contacts, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    private void addEmergencyContact(String uid, String userType, String name, String number, String address, String relationship, FirebaseFirestore db) {
        HashMap<String, Object> newContact = new HashMap<>();
        newContact.put("name", name);
        newContact.put("number", number);
        newContact.put("address", address);
        newContact.put("relationship", relationship);

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update("emergencyContacts", FieldValue.arrayUnion(newContact))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(Senior_add_Emergency_Contact.this, getString(R.string.toast_contact_added_success), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Senior_add_Emergency_Contact.this, getString(R.string.toast_contact_add_failed), Toast.LENGTH_SHORT).show();
                });
    }

    private boolean isValidPhoneNumber(String number) {
        return !number.isEmpty() && number.matches("09\\d{9}");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d("Senior_add_Emergency_Contact", "Configuration changed - language change detected");
        
        // Show toast to confirm language change detection
        Toast.makeText(this, getString(R.string.toast_language_change_detected), Toast.LENGTH_SHORT).show();
        
        // Refresh the relationship spinner with new language
        if (relationshipSpinner != null) {
            setupRelationshipSpinner();
            // Reset selection to default
            relationshipSpinner.setSelection(0);
            selectedRelationship = "";
        }
        
        // Log the current language for debugging
        String currentLang = getResources().getConfiguration().locale.getLanguage();
        Log.d("Senior_add_Emergency_Contact", "Current language: " + currentLang);
    }
}
