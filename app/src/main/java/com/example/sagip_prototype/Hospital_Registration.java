package com.example.sagip_prototype;

import android.os.Bundle;
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

public class Hospital_Registration extends AppCompatActivity {

    FirebaseAuth auth;
    FirebaseFirestore db;

    String userType = "hospital";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_registration);

        // View references
        EditText getHospitalName = findViewById(R.id.hospitalName);
        EditText getAddress = findViewById(R.id.emerContact_Number);
        TextView getMobileNumber = findViewById(R.id.mobileNumber);
        Button continueButton = findViewById(R.id.addEmerContact);

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
                String hospitalName = getHospitalName.getText().toString().trim();
                String address = getAddress.getText().toString().trim();
                String mobileNumber = getMobileNumber.getText().toString().trim();

                if (hospitalName.isEmpty() || address.isEmpty()) {
                    Toast.makeText(Hospital_Registration.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = auth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Hospital_Registration.this, "User not authenticated", Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = user.getUid();

                // Prepare user data
                Map<String, Object> usrData = new HashMap<>();
                usrData.put("hospitalName", hospitalName);
                usrData.put("address", address);
                usrData.put("mobileNumber", mobileNumber);
                usrData.put("user-type", userType);

                // Save to Firestore using UID as document ID
                db.collection("Sagip")
                        .document("users")
                        .collection(userType)
                        .document(uid)
                        .set(usrData)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Toast.makeText(Hospital_Registration.this,
                                            "Registration successful! Awaiting approval.",
                                            Toast.LENGTH_LONG).show();
                                    finish();
                                } else {
                                    Toast.makeText(Hospital_Registration.this,
                                            "Failed to save data: " + task.getException().getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
    }
}