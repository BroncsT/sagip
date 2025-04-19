package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

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

    private final List<String> userTypes = Arrays.asList("seniors", "hospital", "rescuer", "barangay");
    private int currentUserTypeIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        if (isNewUser) {
                            goToRegistration();
                        } else {
                            currentUserTypeIndex = 0;
                            findUserTypeByMobileNumber();
                        }
                    } else {
                        Toast.makeText(OTP_PAGE.this, "Verification failed: " +
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void findUserTypeByMobileNumber() {
        if (currentUserTypeIndex >= userTypes.size()) {
            // Not found in any userType collection
            goToRegistration();
            return;
        }

        String currentType = userTypes.get(currentUserTypeIndex);
        db.collection("Sagip")
                .document("users")
                .collection(currentType)
                .whereEqualTo("mobileNumber", mobileNumber)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            goToHomeScreen(currentType);
                        } else {
                            currentUserTypeIndex++;
                            findUserTypeByMobileNumber(); // Check next type
                        }
                    } else {
                        Toast.makeText(OTP_PAGE.this, "Error checking user type: " + task.getException(), Toast.LENGTH_SHORT).show();
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
                intent = new Intent(OTP_PAGE.this, Log_in_Via_Email.class);
                break;
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToRegistration() {
        Intent intent = new Intent(OTP_PAGE.this, Senior_Registration.class);
        intent.putExtra("MOBILE_NUMBER", mobileNumber);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
