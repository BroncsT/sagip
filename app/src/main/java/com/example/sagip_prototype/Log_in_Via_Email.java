package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class Log_in_Via_Email extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // Ensure this works for your app
        setContentView(R.layout.activity_log_in_via_email);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get references for the EditText and Button
        EditText getEmail = findViewById(R.id.user_email);
        EditText getPassword = findViewById(R.id.user_password);
        Button loginBtn = findViewById(R.id.login_btn);

        // Set the login button click listener
        loginBtn.setOnClickListener(v -> {
            String email = getEmail.getText().toString();
            String password = getPassword.getText().toString();

            if (!email.isEmpty() && !password.isEmpty()) {
                loginWithEmail(email, password);
            } else {
                Toast.makeText(Log_in_Via_Email.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loginWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign-in success, retrieve the current user
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                checkUserTypeAndNavigate(user.getUid());
                            }
                        } else {
                            // Authentication failure
                            Toast.makeText(Log_in_Via_Email.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkUserTypeAndNavigate(String uid) {
        String[] userTypes = {"rescuer", "hospital", "senior", "barangay"};

        // Iterate through the list of user types and check each collection
        for (String userType : userTypes) {
            db.collection("Sagip")
                    .document("users")
                    .collection(userType)  // Query the specific user type collection
                    .document(uid)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            // If a document exists in the current collection, navigate to the corresponding dashboard
                            navigateToDashboard(userType);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Handle failure silently or log it, as we are trying multiple collections
                    });
        }
    }

    private void navigateToDashboard(String userType) {
        // Navigate to the appropriate dashboard based on the user type
        Intent intent;
        switch (userType) {
            case "rescuer":
                intent = new Intent(Log_in_Via_Email.this, Rescuer_Dashboard.class);
                break;
            case "senior":
                intent = new Intent(Log_in_Via_Email.this, Senior_Dashboard.class);
                break;
            case "hospital":
                intent = new Intent(Log_in_Via_Email.this, Hospital_Dashboard.class);
                break;
            case "barangay":
                intent = new Intent(Log_in_Via_Email.this, Barangay_Dashboard.class);
                break;
            default:
                Toast.makeText(this, "Unknown user type", Toast.LENGTH_SHORT).show();
                return; // Return early if no valid user type is found
        }
        startActivity(intent);
        finish(); // Close the current activity
    }
}
