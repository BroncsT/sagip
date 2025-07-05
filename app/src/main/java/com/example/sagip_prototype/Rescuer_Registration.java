package com.example.sagip_prototype;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Rescuer_Registration extends AppCompatActivity {

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    String userType = "rescuer";

    private EditText rescueGroupName;
    private EditText headquartersAddress;
    private EditText primaryContactPerson;
    private EditText contactNumber;

    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_rescuer_registration);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rescueGroupName = findViewById(R.id.rescue_group_name);
        headquartersAddress = findViewById(R.id.headquarters_address);
        primaryContactPerson = findViewById(R.id.primary_contact_person);
        contactNumber = findViewById(R.id.contact_number);

        Button submitRescuer = findViewById(R.id.submit_rescuer);

        submitRescuer.setOnClickListener(v -> {
            String groupname = rescueGroupName.getText().toString().trim();
            String headquarters = headquartersAddress.getText().toString().trim();
            String contact = primaryContactPerson.getText().toString().trim();
            String number = contactNumber.getText().toString().trim();

            if (groupname.isEmpty() || headquarters.isEmpty() || contact.isEmpty() || number.isEmpty()) {
                Toast.makeText(Rescuer_Registration.this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidPhoneNumber(number)) {
                Toast.makeText(Rescuer_Registration.this, "Enter a valid number", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                Toast.makeText(Rescuer_Registration.this, "User not authenticated", Toast.LENGTH_SHORT).show();
                return;
            }

            // Proceed with phone number verification
            verifyPhoneNumber(number);
        });

        setupPhoneAuthCallbacks();
    }

    private void setupPhoneAuthCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                Toast.makeText(Rescuer_Registration.this, "Verification automatically completed", Toast.LENGTH_SHORT).show();
                linkPhoneWithCurrentUser(credential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Toast.makeText(Rescuer_Registration.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                mVerificationId = verificationId;
                mResendToken = token;

                Toast.makeText(Rescuer_Registration.this, "Verification code sent", Toast.LENGTH_SHORT).show();
                showVerificationCodeInputDialog();
            }
        };
    }

    private void verifyPhoneNumber(String phoneNumber) {
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+63" + phoneNumber;
        }

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(mCallbacks)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void showVerificationCodeInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Verification Code");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Submit", (dialog, which) -> {
            String code = input.getText().toString();
            if (!TextUtils.isEmpty(code)) {
                PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
                linkPhoneWithCurrentUser(credential);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void linkPhoneWithCurrentUser(PhoneAuthCredential credential) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.linkWithCredential(credential)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(Rescuer_Registration.this, "Phone verified", Toast.LENGTH_SHORT).show();
                            saveUserDataToFirestore();
                        } else {
                            Toast.makeText(Rescuer_Registration.this, "Phone verification failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void saveUserDataToFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        String email = user.getEmail();

        String groupname = rescueGroupName.getText().toString().trim();
        String headquarters = headquartersAddress.getText().toString().trim();
        String contact = primaryContactPerson.getText().toString().trim();
        String number = contactNumber.getText().toString().trim();

        Map<String, Object> usrData = new HashMap<>();
        usrData.put("rescuegroup", groupname);
        usrData.put("headquarters", headquarters);
        usrData.put("contactPerson", contact);
        usrData.put("mobileNumber", number);
        usrData.put("email", email);
        usrData.put("user-type", userType);

        db.collection("Sagip")
                .document("users")
                .collection(userType)
                .document(uid)
                .set(usrData, SetOptions.merge()) // UPDATE instead of overwrite
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(Rescuer_Registration.this, "Information updated", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Rescuer_Registration.this, Rescuer_Dashboard.class));
                        finish();
                    } else {
                        Toast.makeText(Rescuer_Registration.this, "Update failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isValidPhoneNumber(String number) {
        return !TextUtils.isEmpty(number) && number.matches("\\d{10}");
    }
}
