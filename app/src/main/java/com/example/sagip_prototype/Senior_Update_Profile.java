package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Senior_Update_Profile extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView mobileNumberDisplay;
    private EditText addressInput;
    private Button updateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_update_profile);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        mobileNumberDisplay = findViewById(R.id.mobileNumber);
        addressInput = findViewById(R.id.address);
        updateButton = findViewById(R.id.submitButton);

        loadUserData();

        updateButton.setOnClickListener(v -> updateProfile());
    }

    private void loadUserData() {
        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Get data from Firestore
                        String mobileNumber = documentSnapshot.getString("mobileNumber");
                        String address = documentSnapshot.getString("address");
                        // Display data in UI
                        mobileNumberDisplay.setText(mobileNumber);
                        addressInput.setText(address);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Senior_Update_Profile.this,
                            "Failed to load profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateProfile() {
        // Validate inputs
        String address = addressInput.getText().toString().trim();
        String mobileNum = mobileNumberDisplay.getText().toString().trim();


        if (mobileNum.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();
        String userType = "seniors";

        Map<String, Object> updates = new HashMap<>();
        updates.put("address", address);
        updates.put("mobileNumber", mobileNum);

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(Senior_Update_Profile.this,
                            "Profile updated successfully",
                            Toast.LENGTH_SHORT).show();
                    updateButton.setEnabled(true);
                    updateButton.setText("Update Information");
                    Intent intent = new Intent(Senior_Update_Profile.this, Senior_Profile.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(Senior_Update_Profile.this,
                            "Failed to update profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    updateButton.setEnabled(true);
                    updateButton.setText("Update Information");
                });
    }
}