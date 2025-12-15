package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.google.firebase.FirebaseTooManyRequestsException;

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

    // SharedPreferences for session persistence
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "SagipPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_USER_PHONE = "userPhone";

    private static final int MAX_OTP_RESENDS_PER_WINDOW = 2;
    private static final long OTP_RESEND_WINDOW_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long OTP_RESEND_LOCKOUT_MS = TimeUnit.MINUTES.toMillis(15);
    private static final String PREF_OTP_RESEND = "SagipOtpResendPrefs";
    private static final String KEY_RESEND_WINDOW_START_PREFIX = "otpResendWindowStart_";
    private static final String KEY_RESEND_COUNT_PREFIX = "otpResendCount_";
    private static final String KEY_RESEND_LOCKOUT_UNTIL_PREFIX = "otpResendLockoutUntil_";

    // Only seniors use phone number authentication
    private final List<String> userTypes = Arrays.asList("seniors");
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
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        otpEditText = findViewById(R.id.otpInput);
        Button verifyButton = findViewById(R.id.verifyButton);
        timerTextView = findViewById(R.id.timerTextView);
        resendButton = findViewById(R.id.resendOtpTextView);

        verificationId = getIntent().getStringExtra("VERIFICATION_ID");
        mobileNumber = getIntent().getStringExtra("MOBILE_NUMBER");
        isNewUser = getIntent().getBooleanExtra("IS_NEW_USER", false);
        boolean isPhoneUpdate = getIntent().getBooleanExtra("IS_PHONE_UPDATE", false);
        String returnActivity = getIntent().getStringExtra("RETURN_ACTIVITY");
        
        Log.d(TAG, "OTP_PAGE: Mobile number: " + mobileNumber + ", isNewUser: " + isNewUser + ", isPhoneUpdate: " + isPhoneUpdate);

        resendButton.setEnabled(false);
        startTimer();

        verifyButton.setOnClickListener(v -> {
            String otp = otpEditText.getText().toString().trim();
            if (!TextUtils.isEmpty(otp)) {
                verifyOtp(otp);
            } else {
                Toast.makeText(OTP_PAGE.this, getString(R.string.please_enter_otp), Toast.LENGTH_SHORT).show();
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
                timerTextView.setText(String.format(getString(R.string.resend_otp_timer), seconds));
            }

            @Override
            public void onFinish() {
                timerTextView.setText(getString(R.string.timer_finished));
                resendButton.setEnabled(true);
            }
        }.start();
    }

    private void resendOtp() {
        String otpKey = mobileNumber != null ? mobileNumber.trim() : "";
        long now = System.currentTimeMillis();

        if (!otpKey.isEmpty()) {
            android.content.SharedPreferences prefs = getSharedPreferences(PREF_OTP_RESEND, MODE_PRIVATE);
            long lockoutUntil = prefs.getLong(KEY_RESEND_LOCKOUT_UNTIL_PREFIX + otpKey, 0L);
            if (lockoutUntil > now) {
                long remainingMs = lockoutUntil - now;
                long remainingMinutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(remainingMs));
                Toast.makeText(OTP_PAGE.this,
                        "Too many OTP requests. Try again in " + remainingMinutes + " minute(s).",
                        Toast.LENGTH_LONG).show();
                return;
            }

            long windowStart = prefs.getLong(KEY_RESEND_WINDOW_START_PREFIX + otpKey, 0L);
            int resendCount = prefs.getInt(KEY_RESEND_COUNT_PREFIX + otpKey, 0);
            if (windowStart <= 0L || now - windowStart > OTP_RESEND_WINDOW_MS) {
                windowStart = now;
                resendCount = 0;
            }

            if (resendCount >= MAX_OTP_RESENDS_PER_WINDOW) {
                prefs.edit()
                        .putLong(KEY_RESEND_LOCKOUT_UNTIL_PREFIX + otpKey, now + OTP_RESEND_LOCKOUT_MS)
                        .apply();
                Toast.makeText(OTP_PAGE.this,
                        "Too many OTP requests. Please wait 15 minutes and try again.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            prefs.edit()
                    .putLong(KEY_RESEND_WINDOW_START_PREFIX + otpKey, windowStart)
                    .putInt(KEY_RESEND_COUNT_PREFIX + otpKey, resendCount + 1)
                    .apply();
        }

        Toast.makeText(OTP_PAGE.this, getString(R.string.resending_otp), Toast.LENGTH_SHORT).show();

        PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {
                verifyWithCredential(phoneAuthCredential);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                if (e instanceof FirebaseTooManyRequestsException) {
                    Toast.makeText(OTP_PAGE.this,
                            "Too many OTP requests. Please wait 15 minutes and try again.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(OTP_PAGE.this, String.format(getString(R.string.verification_failed_format), e.getMessage()), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeSent(@NonNull String newVerificationId, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                verificationId = newVerificationId;
                Toast.makeText(OTP_PAGE.this, getString(R.string.new_otp_sent_successfully), Toast.LENGTH_SHORT).show();
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
        boolean isPhoneUpdate = getIntent().getBooleanExtra("IS_PHONE_UPDATE", false);
        String returnActivity = getIntent().getStringExtra("RETURN_ACTIVITY");
        
        if (isPhoneUpdate && "BlankEditProfileActivity".equals(returnActivity)) {
            // For phone update, just verify the credential and return result
            // Don't sign in, just verify it's valid
            Log.d(TAG, "OTP verification successful for phone update: " + mobileNumber);
            
            // Return result to BlankEditProfileActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("OTP_VERIFIED", true);
            resultIntent.putExtra("OTP_CODE", otpEditText.getText().toString().trim());
            resultIntent.putExtra("VERIFICATION_ID", verificationId);
            setResult(RESULT_OK, resultIntent);
            finish();
            return;
        }
        
        // Normal flow - sign in with credential
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
                            String errorMsg = task.getException() != null ? task.getException().getMessage() : getString(R.string.unknown_error_occurred);
                            Toast.makeText(OTP_PAGE.this, String.format(getString(R.string.verification_failed_format), errorMsg), Toast.LENGTH_SHORT).show();
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
        
        // Try multiple formats for backward compatibility
        // New correct format: +639XXXXXXXXX
        // Old wrong format: +6309XXXXXXXXX or 09XXXXXXXXX
        final String searchNumber = mobileNumber.startsWith("+63") ? mobileNumber.substring(3) : mobileNumber;
        final String finalMobileNumber = mobileNumber;
        // Generate legacy formats for backward compatibility (with leading 0)
        final String legacyLocalFormat = "0" + searchNumber; // e.g., "09123456789"
        final String legacyInternationalFormat = "+630" + searchNumber; // e.g., "+6309123456789"
        Log.d(TAG, "Searching for phone number: " + searchNumber + " in collection: " + currentType + 
            " (original: " + finalMobileNumber + ", legacyLocal: " + legacyLocalFormat + 
            ", legacyIntl: " + legacyInternationalFormat + ")");
        
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
                                    // For non-senior users, check status before proceeding
                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                        String status = document.getString("status");
                                        String documentId = document.getId();
                                        Log.d(TAG, "Non-senior user found in OTP_PAGE. Document ID: " + documentId + ", Status: " + status);
                                        
                                        if ("new".equals(status)) {
                                            // User needs to complete registration
                                            Log.d(TAG, "User status is 'new', redirecting to registration");
                                            goToRegistration();
                                        } else {
                                            // User is registered, proceed to dashboard
                                            Log.d(TAG, "User is registered, proceeding to dashboard");
                                            goToHomeScreen(currentType);
                                        }
                                        return;
                                    }
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
                                                                // For non-senior users, check status before proceeding
                                                                for (QueryDocumentSnapshot document : task2.getResult()) {
                                                                    String status = document.getString("status");
                                                                    String documentId = document.getId();
                                                                    Log.d(TAG, "Non-senior user found in OTP_PAGE with full format. Document ID: " + documentId + ", Status: " + status);
                                                                    
                                                                    if ("new".equals(status)) {
                                                                        // User needs to complete registration
                                                                        Log.d(TAG, "User status is 'new', redirecting to registration");
                                                                        goToRegistration();
                                                                    } else {
                                                                        // User is registered, proceed to dashboard
                                                                        Log.d(TAG, "User is registered, proceeding to dashboard");
                                                                        goToHomeScreen(currentType);
                                                                    }
                                                                    return;
                                                                }
                                                            }
                                                        } else {
                                                            // Try legacy formats for backward compatibility
                                                            Log.d(TAG, "User not found in collection: " + currentType + " with new formats, trying legacy formats");
                                                            tryLegacyFormatSearchOTP(currentType, legacyInternationalFormat, legacyLocalFormat);
                                                        }
                                                    } else {
                                                        Log.e(TAG, "Error checking user type with full format: " + task2.getException());
                                                        // Try legacy formats as fallback
                                                        tryLegacyFormatSearchOTP(currentType, legacyInternationalFormat, legacyLocalFormat);
                                                    }
                                                }
                                            });
                                } else {
                                    // Try legacy formats for backward compatibility
                                    Log.d(TAG, "User not found in collection: " + currentType + ", trying legacy formats");
                                    tryLegacyFormatSearchOTP(currentType, legacyInternationalFormat, legacyLocalFormat);
                                }
                            }
                        } else {
                            Log.e(TAG, "Error checking user type: " + task.getException());
                            Toast.makeText(OTP_PAGE.this, String.format(getString(R.string.error_checking_user_type_format), task.getException()), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Try searching with legacy phone number formats for backward compatibility.
     */
    private void tryLegacyFormatSearchOTP(String currentType, String legacyInternationalFormat, String legacyLocalFormat) {
        // First try the legacy international format (+6309XXXXXXXXX)
        db.collection("Sagip")
                .document("users")
                .collection(currentType)
                .whereEqualTo("mobileNumber", legacyInternationalFormat)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            Log.d(TAG, "User found with legacy international format: " + legacyInternationalFormat);
                            handleFoundUserOTP(task.getResult(), currentType);
                        } else {
                            // Try the legacy local format (09XXXXXXXXX)
                            db.collection("Sagip")
                                    .document("users")
                                    .collection(currentType)
                                    .whereEqualTo("mobileNumber", legacyLocalFormat)
                                    .get()
                                    .addOnCompleteListener(task2 -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            if (task2.isSuccessful() && !task2.getResult().isEmpty()) {
                                                Log.d(TAG, "User found with legacy local format: " + legacyLocalFormat);
                                                handleFoundUserOTP(task2.getResult(), currentType);
                                            } else {
                                                Log.d(TAG, "User not found with any format in collection: " + currentType + ", checking next type");
                                                currentUserTypeIndex++;
                                                findUserTypeByMobileNumber();
                                            }
                                        }
                                    });
                        }
                    }
                });
    }

    /**
     * Handle a found user from any of the search formats in OTP flow.
     */
    private void handleFoundUserOTP(QuerySnapshot querySnapshot, String currentType) {
        for (QueryDocumentSnapshot document : querySnapshot) {
            String status = document.getString("status");
            String documentId = document.getId();
            Log.d(TAG, "Found user in " + currentType + " collection. Document ID: " + documentId + ", Status: " + status);
            
            if (currentType.equals("seniors")) {
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
            } else {
                // For non-senior users, check status
                if ("new".equals(status)) {
                    Log.d(TAG, "User status is 'new', redirecting to registration");
                    goToRegistration();
                } else {
                    Log.d(TAG, "User is registered, proceeding to dashboard");
                    goToHomeScreen(currentType);
                }
            }
            return;
        }
    }

    private void goToHomeScreen(String userType) {
        // CRITICAL: Save user credentials before navigating to dashboard
        // This ensures the session persists when the app is closed
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            saveUserCredentials(currentUser.getUid(), userType, mobileNumber);
            Log.d(TAG, "Saved credentials for user: " + currentUser.getUid() + ", type: " + userType);
        } else {
            Log.w(TAG, "Warning: No Firebase user when navigating to home screen");
        }
        
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
    
    private void saveUserCredentials(String userId, String userType, String phoneNumber) {
        Log.d(TAG, "Saving user credentials: " + userId + ", " + userType);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_TYPE, userType);
        if (phoneNumber != null) {
            editor.putString(KEY_USER_PHONE, phoneNumber);
        }
        editor.putBoolean("FRESH_INSTALL_FLAG", false);
        // Use commit() instead of apply() for immediate, synchronous persistence
        // This is critical for seniors to prevent session loss when app is closed
        editor.commit();
        
        // Also save to user_prefs for notification services
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor userEditor = userPrefs.edit();
        userEditor.putString("user_id", userId);
        userEditor.putString("user_type", userType);
        if (phoneNumber != null) {
            userEditor.putString("user_phone", phoneNumber);
        }
        userEditor.putBoolean("user_logged_out", false);
        userEditor.commit();
    }

    private void goToRegistration() {
        Intent intent = new Intent(OTP_PAGE.this, Senior_Registration.class);
        intent.putExtra("MOBILE_NUMBER", mobileNumber);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goToRegistrationByType(String userType) {
        Intent intent;
        switch (userType) {
            case "rescuer":
                intent = new Intent(OTP_PAGE.this, Rescuer_Registration.class);
                break;
            case "hospital":
                intent = new Intent(OTP_PAGE.this, Hospital_Registration.class);
                break;
            case "barangay":
                intent = new Intent(OTP_PAGE.this, Barangay_Registration.class);
                break;
            case "seniors":
            default:
                intent = new Intent(OTP_PAGE.this, Senior_Registration.class);
                break;
        }
        intent.putExtra("MOBILE_NUMBER", mobileNumber);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showPendingApprovalMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.senior_account_pending_approval_title))
                .setMessage(getString(R.string.senior_account_pending_approval_message))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
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
