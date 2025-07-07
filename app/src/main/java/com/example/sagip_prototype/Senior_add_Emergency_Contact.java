package com.example.sagip_prototype;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;

public class Senior_add_Emergency_Contact extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_senior_add_emergency_contact);

        EditText emerName = findViewById(R.id.emerContact_name);
        EditText emerNumber = findViewById(R.id.emerContact_Number);
        EditText emerAddress = findViewById(R.id.emerContact_add);
        Button addEmergencyContact = findViewById(R.id.addEmerContact);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();

        addEmergencyContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = emerName.getText().toString().trim();
                String number = emerNumber.getText().toString().trim();
                String address = emerAddress.getText().toString().trim();

                if (name.isEmpty() || number.isEmpty() || address.isEmpty()) {
                    Toast.makeText(Senior_add_Emergency_Contact.this, "Fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Senior_add_Emergency_Contact.this, "User not authenticated", Toast.LENGTH_SHORT).show();
                    return;
                }

                String uid = user.getUid();
                String userType = "seniors";

                HashMap<String, Object> newContact = new HashMap<>();
                newContact.put("name", name);
                newContact.put("number", number);
                newContact.put("address", address);

                db.collection("Sagip")
                        .document("users")
                        .collection(userType)
                        .document(uid)
                        .update("emergencyContacts", FieldValue.arrayUnion(newContact))
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(Senior_add_Emergency_Contact.this, "Emergency contact added successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(Senior_add_Emergency_Contact.this, "Failed to add emergency contact", Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }
}
