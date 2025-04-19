package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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

public class Senior_Registration extends AppCompatActivity {

    FirebaseAuth auth;
    FirebaseFirestore db;

    String userType = "seniors";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_senior_registration);

        // View references
        EditText birthdayEditText = findViewById(R.id.birthday);
        EditText getFirstName = findViewById(R.id.firstName);
        EditText getMiddleName = findViewById(R.id.middleName);
        EditText getLastName = findViewById(R.id.lastName);
        EditText getAddress = findViewById(R.id.address);
        TextView getMobileNumber = findViewById(R.id.mobileNumber);
        Button continueButton = findViewById(R.id.continueButton);

        // Firebase initialization
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get passed phone number from intent
        String number = getIntent().getStringExtra("MOBILE_NUMBER");
        getMobileNumber.setText(number);

        // Save data on button click
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String firstName = getFirstName.getText().toString().trim();
                String middleName = getMiddleName.getText().toString().trim();
                String lastName = getLastName.getText().toString().trim();
                String birthday = birthdayEditText.getText().toString().trim();
                String address = getAddress.getText().toString().trim();
                String mobileNumber = getMobileNumber.getText().toString().trim();

                if (firstName.isEmpty() || lastName.isEmpty() || birthday.isEmpty() || address.isEmpty()) {
                    Toast.makeText(Senior_Registration.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = auth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Senior_Registration.this, "User not authenticated", Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = user.getUid();

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
                                usrData.put("address", address);
                                usrData.put("mobileNumber", mobileNumber);
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
                                                    Toast.makeText(Senior_Registration.this, "Verification Process", Toast.LENGTH_SHORT).show();
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

        // Auto-format birthday input as DD - MM - YYYY
        birthdayEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                birthdayEditText.removeTextChangedListener(this);

                String text = editable.toString().replaceAll("[^\\d]", "");
                StringBuilder formatted = new StringBuilder();

                if (text.length() > 0) {
                    formatted.append(text.substring(0, Math.min(2, text.length()))); // DD
                    if (text.length() > 2) {
                        formatted.append(" - ").append(text.substring(2, Math.min(4, text.length()))); // MM
                        if (text.length() > 4) {
                            formatted.append(" - ").append(text.substring(4, Math.min(8, text.length()))); // YYYY
                        }
                    }
                }

                birthdayEditText.setText(formatted.toString());
                birthdayEditText.setSelection(formatted.length());
                birthdayEditText.addTextChangedListener(this);
            }
        });
    }
}
