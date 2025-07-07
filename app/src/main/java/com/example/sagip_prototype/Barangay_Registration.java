package com.example.sagip_prototype;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Barangay_Registration extends AppCompatActivity {

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    String userType = "barangay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_barangay_registration);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        EditText barangayName = findViewById(R.id.emerContact_name);
        EditText address = findViewById(R.id.emerContact_Number);
        EditText captain = findViewById(R.id.emerContact_add);

        Button continueButton = findViewById(R.id.addEmerContact);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String barangayNameText = barangayName.getText().toString();
                String addressText = address.getText().toString();
                String captainText = captain.getText().toString();

                if (!barangayNameText.isEmpty() && !addressText.isEmpty() && !captainText.isEmpty()) {
                    Toast.makeText(Barangay_Registration.this, "Fill all Fiels", Toast.LENGTH_SHORT).show();
                }
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Toast.makeText(Barangay_Registration.this, "User not authenticated", Toast.LENGTH_SHORT).show();
                    return;
                }
                String uid = user.getUid();


                Map<String, Object> usrData = new HashMap<>();
                usrData.put("barangayName", barangayNameText);
                usrData.put("address", address);
                usrData.put("captain", captain);
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
                                    Toast.makeText(Barangay_Registration.this,
                                            "Registration successful! Awaiting approval.",
                                            Toast.LENGTH_LONG).show();
                                    finish();
                                } else {
                                    Toast.makeText(Barangay_Registration.this,
                                            "Failed to save data: " + task.getException().getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });
    }
}