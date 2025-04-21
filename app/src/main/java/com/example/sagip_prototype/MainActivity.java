package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.sagip_prototype.OTP_PAGE;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    FirebaseAuth auth;
    FirebaseFirestore db;
    Long timeout = 60L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check authentication status before setting content view
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String phoneNumber = currentUser.getPhoneNumber();
            if (phoneNumber != null) {
                Log.d(TAG, "User already logged in with phone: " + phoneNumber);
                checkUserTypeAndRedirect(phoneNumber);
                return; // Exit onCreate early to prevent showing login screen
            } else {
                Log.d(TAG, "User logged in but no phone number found");
                auth.signOut();
            }
        }

        // Only set content view and initialize UI if no user is authenticated
        setContentView(R.layout.activity_main);

        Button loginButton = findViewById(R.id.login_btn);
        EditText loginNumber = findViewById(R.id.user_number);
        TextView LoginViaEmail = findViewById(R.id.address);
        TextView errorTextView = findViewById(R.id.errorTextView);
        errorTextView.setVisibility(View.GONE);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String number = loginNumber.getText().toString().trim();

                if (isValidPhoneNumber(number)) {
                    errorTextView.setVisibility(View.GONE);
                    Log.d(TAG, "Checking registration status for: +63" + number);
                    checkUserExistsByPhoneNumber("+63" + number);
                } else {
                    errorTextView.setVisibility(View.VISIBLE);
                    errorTextView.setText("Please enter a valid 10-digit mobile number");
                    Log.e(TAG, "Invalid phone number entered: " + number);
                }
            }
        });

        LoginViaEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent LogInViaEmail = new Intent(MainActivity.this, Log_in_Via_Email.class);
                startActivity(LogInViaEmail);
            }
        });
    }

    // No longer need onStart() check since we do it in onCreate() before setting content view

    // Method to check user type and redirect to appropriate screen by phone number
    private void checkUserTypeAndRedirect(String phoneNumber) {
        // Check all possible user type collections
        String[] userTypes = {"seniors", "user", "rescuer", "admin"};
        checkAuthenticatedUserType(phoneNumber, userTypes, 0);
    }

    private void checkAuthenticatedUserType(String phoneNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            // User not found in any collection, something is wrong
            Log.e(TAG, "User is authenticated but not found in any collection: " + phoneNumber);
            Toast.makeText(MainActivity.this, "Error finding user profile. Please login again.",
                    Toast.LENGTH_SHORT).show();
            auth.signOut();

            // Now load the login UI since we need to sign in again
            setContentView(R.layout.activity_main);
            setupLoginUI();
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("mobileNumber", phoneNumber)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                // User found, check status
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    if (status != null && status.equals("approved")) {
                                        // User is approved, redirect to appropriate dashboard
                                        redirectToUserDashboard(userTypes[index]);
                                    } else {
                                        // User exists but not approved
                                        showPendingApprovalMessage();
                                        auth.signOut();

                                        // Now load the login UI since we need to sign in again
                                        setContentView(R.layout.activity_main);
                                        setupLoginUI();
                                    }
                                    return; // Exit after finding a user
                                }
                            } else {
                                // No user with this phone number in this collection, check next
                                checkAuthenticatedUserType(phoneNumber, userTypes, index + 1);
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, "Error checking user status",
                                    Toast.LENGTH_SHORT).show();

                            // Now load the login UI since we had an error
                            setContentView(R.layout.activity_main);
                            setupLoginUI();
                        }
                    }
                });
    }

    // Helper method to set up login UI elements
    private void setupLoginUI() {
        Button loginButton = findViewById(R.id.login_btn);
        EditText loginNumber = findViewById(R.id.user_number);
        TextView LoginViaEmail = findViewById(R.id.address);
        TextView errorTextView = findViewById(R.id.errorTextView);
        errorTextView.setVisibility(View.GONE);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String number = loginNumber.getText().toString().trim();

                if (isValidPhoneNumber(number)) {
                    errorTextView.setVisibility(View.GONE);
                    Log.d(TAG, "Checking registration status for: +63" + number);
                    checkUserExistsByPhoneNumber("+63" + number);
                } else {
                    errorTextView.setVisibility(View.VISIBLE);
                    errorTextView.setText("Please enter a valid 10-digit mobile number");
                    Log.e(TAG, "Invalid phone number entered: " + number);
                }
            }
        });

        LoginViaEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent LogInViaEmail = new Intent(MainActivity.this, Log_in_Via_Email.class);
                startActivity(LogInViaEmail);
            }
        });
    }

    // Method to handle redirects to the appropriate dashboard
    private void redirectToUserDashboard(String userType) {
        Intent dashboardIntent;

        // Redirect based on user type
        switch (userType) {
            case "seniors":
                dashboardIntent = new Intent(MainActivity.this, Senior_Dashboard.class);
                break;
            case "rescuer":
                dashboardIntent = new Intent(MainActivity.this, Rescuer_Dashboard.class);
                break;
            case "admin":
                dashboardIntent = new Intent(MainActivity.this, Hospital_Dashboard.class);
                break;
            default: // Regular user
                dashboardIntent = new Intent(MainActivity.this, Senior_Dashboard.class);
                break;
        }

        startActivity(dashboardIntent);
        finish(); // Close the login activity
    }

    private void checkUserExistsByPhoneNumber(String formattedNumber) {
        // Check all possible user type collections
        String[] userTypes = {"seniors", "user", "rescuer", "admin"};
        checkPhoneNumberInCollections(formattedNumber, userTypes, 0);
    }

    private void checkPhoneNumberInCollections(String formattedNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            // Phone number not found in any collection, proceed to OTP for registration
            String plainNumber = formattedNumber.substring(3); // Remove the +63 prefix
            sendOtp(plainNumber, true);
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .whereEqualTo("mobileNumber", formattedNumber)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                // User found, check status
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    if (status != null && status.equals("approved")) {
                                        // User is approved, proceed with OTP verification for login
                                        String plainNumber = formattedNumber.substring(3); // Remove the +63 prefix
                                        sendOtp(plainNumber, false);
                                    } else {
                                        // User exists but not approved
                                        showPendingApprovalMessage();
                                    }
                                    return; // Exit after finding a user
                                }
                            } else {
                                // No user with this phone number in this collection, check next
                                checkPhoneNumberInCollections(formattedNumber, userTypes, index + 1);
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, "Error checking registration status", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void showPendingApprovalMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Account Pending Approval")
                .setMessage("Your account is registered but pending administrator approval. Please try again later.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void sendOtp(String number, boolean isNewUser) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber("+63" + number)
                .setTimeout(timeout, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(com.google.firebase.auth.PhoneAuthCredential credential) {
                        Log.d(TAG, "Auto-verification completed");
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        Log.e(TAG, "OTP verification failed: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Failed to send OTP: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCodeSent(String verificationId,
                                           PhoneAuthProvider.ForceResendingToken token) {
                        Log.d(TAG, "OTP code sent successfully");
                        Intent intent = new Intent(MainActivity.this, OTP_PAGE.class);
                        intent.putExtra("VERIFICATION_ID", verificationId);
                        intent.putExtra("MOBILE_NUMBER", "+63" + number);
                        intent.putExtra("IS_NEW_USER", isNewUser);
                        startActivity(intent);
                    }
                }).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private boolean isValidPhoneNumber(String number) {
        return !TextUtils.isEmpty(number) && number.matches("\\d{10}");
    }
}