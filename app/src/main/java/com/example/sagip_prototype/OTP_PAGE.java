package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OTP_PAGE extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String verificationId;
    private String mobileNumber;
    private boolean isNewUser;
    private EditText otpEditText;
    private TextView timerTextView;
    private TextView resendButton;
    private CountDownTimer countDownTimer;
    private static final long TIMER_DURATION = 60000; // 60 seconds

    private final List<String> userTypes = Arrays.asList("seniors", "rescuer", "barangay", "hospital");
    private int currentUserTypeIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved language preference
        String savedLanguage = LanguageSelectionActivity.getSavedLanguage(this);
        LanguageSelectionActivity.setAppLanguage(this, savedLanguage);
        
        setContentView(R.layout.activity_otp_page);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        otpEditText = findViewById(R.id.otpInput);
        Button verifyButton = findViewById(R.id.verifyButton);
        timerTextView = findViewById(R.id.timerTextView);
        resendButton = findViewById(R.id.resendOtpTextView);

        verificationId = getIntent().getStringExtra("VERIFICATION_ID");
        mobileNumber = getIntent().getStringExtra("MOBILE_NUMBER");
        isNewUser = getIntent().getBooleanExtra("IS_NEW_USER", false);
        
        Log.d(TAG, "OTP_PAGE: Mobile number: " + mobileNumber + ", isNewUser: " + isNewUser);

        resendButton.setEnabled(false);
        startTimer();

        verifyButton.setOnClickListener(v -> {
            String otp = otpEditText.getText().toString().trim();
            if (!TextUtils.isEmpty(otp)) {
                verifyOtp(otp);
            } else {
                Toast.makeText(OTP_PAGE.this, "Please enter OTP", Toast.LENGTH_SHORT).show();
            }
        });

        resendButton.setOnClickListener(v -> {
            resendOtp();
            resendButton.setEnabled(false);
            startTimer();
        });
    }

    private void startTimer() {
        countDownTimer = new CountDownTimer(TIMER_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                timerTextView.setText("Resend OTP in " + seconds + " seconds");
            }

            @Override
            public void onFinish() {
                timerTextView.setText("Timer finished");
                resendButton.setEnabled(true);
            }
        }.start();
    }

    private void resendOtp() {
        Toast.makeText(OTP_PAGE.this, "Resending OTP...", Toast.LENGTH_SHORT).show();

        PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {
                verifyWithCredential(phoneAuthCredential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Toast.makeText(OTP_PAGE.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeSent(@NonNull String newVerificationId, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                verificationId = newVerificationId;
                Toast.makeText(OTP_PAGE.this, "New OTP sent successfully", Toast.LENGTH_SHORT).show();
            }
        };

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(mobileNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(callbacks)
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyOtp(String otp) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        verifyWithCredential(credential);
    }

    private void verifyWithCredential(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = task.getResult().getUser();
                            Log.d(TAG, "OTP verification successful for: " + mobileNumber);
                            // Always check user status first, regardless of isNewUser flag
                            // This prevents pending users from bypassing the status check
                            currentUserTypeIndex = 0;
                            findUserTypeByMobileNumber();
                        } else {
                            Toast.makeText(OTP_PAGE.this, "Verification failed: " +
                                    (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void findUserTypeByMobileNumber() {
        if (currentUserTypeIndex >= userTypes.size()) {
            // Not found in any userType collection
            Log.d(TAG, "User not found in any collection after checking all types. Phone: " + mobileNumber + ", isNewUser: " + isNewUser);
            
            // For senior users, check if they might be pending before going to registration
            // This handles cases where the user exists but wasn't found due to search issues
            if (isNewUser) {
                Log.d(TAG, "Treating as new user, going to registration");
                goToRegistration();
            } else {
                Log.d(TAG, "User was expected to exist but not found, going to registration");
                goToRegistration();
            }
            return;
        }

        String currentType = userTypes.get(currentUserTypeIndex);
        Log.d(TAG, "Checking user type: " + currentType + " for mobile: " + mobileNumber);
        
        // Try both with and without +63 prefix
        final String searchNumber = mobileNumber.startsWith("+63") ? mobileNumber.substring(3) : mobileNumber;
        final String finalMobileNumber = mobileNumber;
        Log.d(TAG, "Searching for phone number: " + searchNumber + " in collection: " + currentType + " (original: " + finalMobileNumber + ")");
        
        db.collection("Sagip")
                .document("users")
                .collection(currentType)
                .whereEqualTo("mobileNumber", searchNumber)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                Log.d(TAG, "User found in collection: " + currentType);
                                
                                // Check user status for senior users
                                if (currentType.equals("seniors")) {
                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                        String status = document.getString("status");
                                        String documentId = document.getId();
                                        Log.d(TAG, "Senior user found in OTP_PAGE. Document ID: " + documentId + ", Status: " + status);
                                        if (status != null && status.equals("approved")) {
                                            Log.d(TAG, "Senior user found with approved status, proceeding to dashboard");
                                            goToHomeScreen(currentType);
                                        } else if (status != null && status.equals("pending")) {
                                            Log.d(TAG, "Senior user found but status is pending - BLOCKING ACCESS");
                                            showPendingApprovalMessage();
                                        } else {
                                            Log.d(TAG, "Senior user found but status not approved/pending: " + status);
                                            showPendingApprovalMessage();
                                        }
                                        return;
                                    }
                                } else {
                                    // For non-senior users, allow login regardless of status
                                    Log.d(TAG, "Non-senior user found, proceeding to dashboard");
                                    goToHomeScreen(currentType);
                                }
                            } else {
                                Log.d(TAG, "User not found in collection: " + currentType + " with format " + searchNumber + ", trying with full format");
                                // Try with the full number format (including +63 prefix)
                                if (!finalMobileNumber.equals(searchNumber)) {
                                    db.collection("Sagip")
                                            .document("users")
                                            .collection(currentType)
                                            .whereEqualTo("mobileNumber", finalMobileNumber)
                                            .get()
                                            .addOnCompleteListener(task2 -> {
                                                if (!isFinishing() && !isDestroyed()) {
                                                    if (task2.isSuccessful()) {
                                                        if (!task2.getResult().isEmpty()) {
                                                            Log.d(TAG, "User found with full format: " + finalMobileNumber);
                                                            // Process the found user with the same logic
                                                            if (currentType.equals("seniors")) {
                                                                for (QueryDocumentSnapshot document : task2.getResult()) {
                                                                    String status = document.getString("status");
                                                                    String documentId = document.getId();
                                                                    Log.d(TAG, "Senior user found in OTP_PAGE with full format. Document ID: " + documentId + ", Status: " + status);
                                                                    if (status != null && status.equals("approved")) {
                                                                        Log.d(TAG, "Senior user found with approved status, proceeding to dashboard");
                                                                        goToHomeScreen(currentType);
                                                                    } else if (status != null && status.equals("pending")) {
                                                                        Log.d(TAG, "Senior user found but status is pending - BLOCKING ACCESS");
                                                                        showPendingApprovalMessage();
                                                                    } else {
                                                                        Log.d(TAG, "Senior user found but status not approved/pending: " + status);
                                                                        showPendingApprovalMessage();
                                                                    }
                                                                    return;
                                                                }
                                                            } else {
                                                                // For non-senior users, allow login regardless of status
                                                                Log.d(TAG, "Non-senior user found with full format, proceeding to dashboard");
                                                                goToHomeScreen(currentType);
                                                            }
                                                        } else {
                                                            Log.d(TAG, "User not found in collection: " + currentType + " with either format, checking next type");
                                                            currentUserTypeIndex++;
                                                            findUserTypeByMobileNumber(); // Check next type
                                                        }
                                                    } else {
                                                        Log.e(TAG, "Error checking user type with full format: " + task2.getException());
                                                        currentUserTypeIndex++;
                                                        findUserTypeByMobileNumber(); // Check next type
                                                    }
                                                }
                                            });
                                } else {
                                    Log.d(TAG, "User not found in collection: " + currentType);
                                    currentUserTypeIndex++;
                                    findUserTypeByMobileNumber(); // Check next type
                                }
                            }
                        } else {
                            Log.e(TAG, "Error checking user type: " + task.getException());
                            Toast.makeText(OTP_PAGE.this, "Error checking user type: " + task.getException(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void goToHomeScreen(String userType) {
        Intent intent;
        switch (userType) {
            case "seniors":
                intent = new Intent(OTP_PAGE.this, Senior_Dashboard.class);
                break;
            case "hospital":
                intent = new Intent(OTP_PAGE.this, Hospital_Dashboard.class);
                break;
            case "rescuer":
                intent = new Intent(OTP_PAGE.this, Rescuer_Dashboard.class);
                break;
            case "barangay":
                intent = new Intent(OTP_PAGE.this, Barangay_Dashboard.class);
                break;
            default:
                // For unknown user types, go to registration instead of MainActivity
                Log.d(TAG, "Unknown user type: " + userType + ", redirecting to registration");
                goToRegistration();
                return;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToRegistration() {
        Intent intent = new Intent(OTP_PAGE.this, Senior_Registration.class);
        intent.putExtra("MOBILE_NUMBER", mobileNumber);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showPendingApprovalMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Senior Citizen Account Pending Approval")
                .setMessage("Your Senior Citizen account is registered but pending administrator approval. You cannot access the app until your account is approved. Please contact an administrator or try again later.")
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    // Sign out the user and finish this activity
                    // This will return the user to the previous activity (MainActivity) naturally
                    auth.signOut();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
