package com.example.sagip_prototype;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "SagipAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_TYPE = "userType";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_PHONE = "userPhone";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private final Long timeout = 60L;
    private SharedPreferences sharedPreferences;

    // UI Components for Phone Login
    private View phoneLoginLayout;
    private EditText phoneNumberInput;
    private Button phoneLoginButton;
    private TextView phoneErrorTextView;

    // UI Components for Email Login
    private View emailLoginLayout;
    private EditText emailInput;
    private EditText passwordInput;
    private Button emailLoginButton;

    private boolean isPhoneLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Check if this is a logout action
        Bundle extras = getIntent().getExtras();
        boolean isLogoutAction = false;
        if (extras != null) {
            isLogoutAction = extras.getBoolean("LOGOUT_ACTION", false);
        }

        // If it's a logout action, clear stored credentials and force logout
        if (isLogoutAction) {
            Log.d(TAG, "Logout action detected, clearing credentials and signing out");
            auth.signOut();
            clearStoredCredentials();
            // Set content view and show login screen
            setContentView(R.layout.activity_main);
            initializeUI();
            setupPhoneLogin();
            setupEmailLogin();
            return;
        }

        // Check if user is already logged in
        if (isUserLoggedIn()) {
            Log.d(TAG, "User already logged in, redirecting to dashboard");
            redirectToStoredUserDashboard();
            return;
        }

        // Check authentication status before setting content view
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String phoneNumber = currentUser.getPhoneNumber();
            String email = currentUser.getEmail();

            if (phoneNumber != null) {
                Log.d(TAG, "User already logged in with phone: " + phoneNumber);
                checkUserTypeAndRedirect(phoneNumber, true);
                return;
            } else if (email != null) {
                Log.d(TAG, "User already logged in with email: " + email);
                checkUserTypeAndRedirect(currentUser.getUid(), false);
                return;
            } else {
                Log.d(TAG, "User logged in but no phone number or email found");
                auth.signOut();
            }
        }

        // Set content view and initialize UI
        setContentView(R.layout.activity_main);
        initializeUI();
        setupPhoneLogin();
        setupEmailLogin();
    }

    private boolean isUserLoggedIn() {
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
        String userId = sharedPreferences.getString(KEY_USER_ID, null);
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);

        return isLoggedIn && userId != null && userType != null;
    }

    private void redirectToStoredUserDashboard() {
        String userType = sharedPreferences.getString(KEY_USER_TYPE, null);
        if (userType != null) {
            redirectToUserDashboard(userType);
        } else {
            // Clear invalid stored data
            clearStoredCredentials();
        }
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
        editor.apply();
    }

    private void clearStoredCredentials() {
        Log.d(TAG, "Clearing stored credentials");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_IS_LOGGED_IN);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_TYPE);
        editor.remove(KEY_USER_PHONE);
        editor.apply();
    }

    private void initializeUI() {
        // Custom Tab Buttons
        TextView phoneTabButton = findViewById(R.id.phoneTabButton);
        TextView emailTabButton = findViewById(R.id.emailTabButton);

        // Phone Login Components
        phoneLoginLayout = findViewById(R.id.phoneInputCard);
        phoneNumberInput = findViewById(R.id.user_number);
        phoneLoginButton = findViewById(R.id.login_btn);
        phoneErrorTextView = findViewById(R.id.errorTextView);

        // Email Login Components
        emailLoginLayout = findViewById(R.id.emailInputSection);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        emailLoginButton = findViewById(R.id.login_btn); // Same button for both modes

        // Set up tab click listeners
        phoneTabButton.setOnClickListener(v -> {
            showPhoneLogin();
            updateTabAppearance(phoneTabButton, emailTabButton);
        });

        emailTabButton.setOnClickListener(v -> {
            showEmailLogin();
            updateTabAppearance(emailTabButton, phoneTabButton);
        });

        // Initially show phone login
        showPhoneLogin();
        updateTabAppearance(phoneTabButton, emailTabButton);
    }

    private void updateTabAppearance(TextView selectedTab, TextView unselectedTab) {
        // Update selected tab
        selectedTab.setTextColor(getResources().getColor(android.R.color.white, null));
        selectedTab.setBackgroundResource(R.drawable.tab_selected);
        selectedTab.setTypeface(null, Typeface.BOLD);

        // Update unselected tab
        unselectedTab.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
        unselectedTab.setBackgroundResource(R.drawable.tab_unselected);
        unselectedTab.setTypeface(null, Typeface.NORMAL);
    }

    private void showPhoneLogin() {
        isPhoneLoginMode = true;
        phoneLoginLayout.setVisibility(View.VISIBLE);
        emailLoginLayout.setVisibility(View.GONE);
        phoneErrorTextView.setVisibility(View.GONE);

        // Update button text and prompt
        phoneLoginButton.setText("Continue");
        TextView loginPrompt = findViewById(R.id.loginPromptText);
        loginPrompt.setText("Enter your mobile number to continue");
    }

    private void showEmailLogin() {
        isPhoneLoginMode = false;
        phoneLoginLayout.setVisibility(View.GONE);
        emailLoginLayout.setVisibility(View.VISIBLE);

        // Update button text and prompt
        phoneLoginButton.setText("Login with Email");
        TextView loginPrompt = findViewById(R.id.loginPromptText);
        loginPrompt.setText("Enter your email and password");
    }

    private void setupPhoneLogin() {
        phoneLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPhoneLoginMode) {
                    // Handle phone login
                    String number = phoneNumberInput.getText().toString().trim();

                    if (isValidPhoneNumber(number)) {
                        phoneErrorTextView.setVisibility(View.GONE);
                        Log.d(TAG, "Checking registration status for: +63" + number);
                        checkUserExistsByPhoneNumber("+63" + number);
                    } else {
                        phoneErrorTextView.setVisibility(View.VISIBLE);
                        phoneErrorTextView.setText("Please enter a valid 10-digit mobile number");
                        Log.e(TAG, "Invalid phone number entered: " + number);
                    }
                } else {
                    // Handle email login
                    String email = emailInput.getText().toString().trim();
                    String password = passwordInput.getText().toString().trim();

                    if (!email.isEmpty() && !password.isEmpty()) {
                        loginWithEmail(email, password);
                    } else {
                        Toast.makeText(MainActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private void setupEmailLogin() {
        View passwordToggle = findViewById(R.id.passwordToggle);
        if (passwordToggle != null) {
            passwordToggle.setOnClickListener(v -> {
                if (passwordInput.getInputType() == (android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                } else {
                    passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                passwordInput.setSelection(passwordInput.getText().length());
            });
        }

        // Handle forgot password
        TextView forgotPassword = findViewById(R.id.forgotPasswordText);
        if (forgotPassword != null) {
            forgotPassword.setOnClickListener(v -> {
                showForgotPasswordDialog();
            });
        }
    }

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");
        builder.setMessage("Enter your email address to receive a password reset link:");

        // Create an EditText for email input
        final EditText emailEditText = new EditText(this);
        emailEditText.setHint("Enter your email");
        emailEditText.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        // Add some padding to the EditText
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        emailEditText.setPadding(padding, padding, padding, padding);

        builder.setView(emailEditText);

        builder.setPositiveButton("Send Reset Link", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String email = emailEditText.getText().toString().trim();
                if (isValidEmail(email)) {
                    sendPasswordResetEmail(email);
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Pre-fill with current email if available
        String currentEmail = emailInput.getText().toString().trim();
        if (!currentEmail.isEmpty() && isValidEmail(currentEmail)) {
            emailEditText.setText(currentEmail);
            emailEditText.setSelection(currentEmail.length());
        }
    }

    private void sendPasswordResetEmail(String email) {
        // Show progress
        showProgressBar(true);

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        showProgressBar(false);

                        if (task.isSuccessful()) {
                            Log.d(TAG, "Password reset email sent successfully to: " + email);
                            showPasswordResetSuccessDialog(email);
                        } else {
                            Log.e(TAG, "Failed to send password reset email", task.getException());
                            String errorMessage = getPasswordResetErrorMessage(task.getException());
                            showPasswordResetErrorDialog(errorMessage);
                        }
                    }
                });
    }

    private void showPasswordResetSuccessDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Password Reset Email Sent");
        builder.setMessage("A password reset link has been sent to:\n\n" + email +
                "\n\nPlease check your email and follow the instructions to reset your password. " +
                "Don't forget to check your spam/junk folder if you don't see the email in your inbox.");
        builder.setIcon(android.R.drawable.ic_dialog_info);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                // Optionally switch to email login tab
                if (isPhoneLoginMode) {
                    TextView emailTabButton = findViewById(R.id.emailTabButton);
                    TextView phoneTabButton = findViewById(R.id.phoneTabButton);
                    showEmailLogin();
                    updateTabAppearance(emailTabButton, phoneTabButton);
                }
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void showPasswordResetErrorDialog(String errorMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Password Reset Failed");
        builder.setMessage(errorMessage);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.setNegativeButton("Try Again", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showForgotPasswordDialog();
            }
        });

        builder.show();
    }

    private String getPasswordResetErrorMessage(Exception exception) {
        if (exception == null) {
            return "An unknown error occurred. Please try again.";
        }

        String errorMessage = exception.getMessage();
        if (errorMessage == null) {
            return "An unknown error occurred. Please try again.";
        }

        // Handle common Firebase Auth error codes
        if (errorMessage.contains("There is no user record")) {
            return "No account found with this email address. Please check your email or create a new account.";
        } else if (errorMessage.contains("The email address is badly formatted")) {
            return "Please enter a valid email address.";
        } else if (errorMessage.contains("too-many-requests")) {
            return "Too many requests. Please wait a moment before trying again.";
        } else if (errorMessage.contains("network-request-failed")) {
            return "Network error. Please check your internet connection and try again.";
        } else {
            return "Failed to send password reset email. Please try again or contact support if the problem persists.";
        }
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    // Email Login Methods
    private void loginWithEmail(String email, String password) {
        showProgressBar(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        showProgressBar(false);

                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                Log.d(TAG, "Email login successful for: " + email);
                                checkUserTypeAndRedirect(user.getUid(), false);
                            }
                        } else {
                            Log.e(TAG, "Email login failed", task.getException());
                            String errorMessage = getLoginErrorMessage(task.getException());
                            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private String getLoginErrorMessage(Exception exception) {
        if (exception == null) {
            return "Authentication failed. Please try again.";
        }

        String errorMessage = exception.getMessage();
        if (errorMessage == null) {
            return "Authentication failed. Please try again.";
        }

        // Handle common Firebase Auth error codes
        if (errorMessage.contains("There is no user record")) {
            return "No account found with this email. Please check your email or register.";
        } else if (errorMessage.contains("The password is invalid")) {
            return "Incorrect password. Please try again or use 'Forgot Password'.";
        } else if (errorMessage.contains("The email address is badly formatted")) {
            return "Please enter a valid email address.";
        } else if (errorMessage.contains("too-many-requests")) {
            return "Too many failed attempts. Please try again later.";
        } else if (errorMessage.contains("user-disabled")) {
            return "This account has been disabled. Please contact support.";
        } else {
            return "Authentication failed. Please check your credentials and try again.";
        }
    }

    // Phone Login Methods
    private void checkUserTypeAndRedirect(String identifier, boolean isPhoneNumber) {
        if (isPhoneNumber) {
            // Check all possible user type collections for phone number
            String[] userTypes = {"seniors", "user", "rescuer", "barangay"};
            checkAuthenticatedUserTypeByPhone(identifier, userTypes, 0);
        } else {
            // Check all possible user type collections for UID (email users)
            String[] userTypes = {"rescuer", "hospital", "seniors", "barangay"};
            checkAuthenticatedUserTypeByUID(identifier, userTypes, 0);
        }
    }

    private void checkAuthenticatedUserTypeByPhone(String phoneNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User is authenticated but not found in any collection: " + phoneNumber);
            Toast.makeText(MainActivity.this, "Error finding user profile. Please login again.", Toast.LENGTH_SHORT).show();
            auth.signOut();
            clearStoredCredentials();
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
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    if (status != null && status.equals("approved")) {
                                        // Save user credentials before redirecting
                                        FirebaseUser currentUser = auth.getCurrentUser();
                                        if (currentUser != null) {
                                            saveUserCredentials(currentUser.getUid(), userTypes[index], phoneNumber);
                                        }
                                        redirectToUserDashboard(userTypes[index]);
                                    } else {
                                        showPendingApprovalMessage();
                                        auth.signOut();
                                        clearStoredCredentials();
                                    }
                                    return;
                                }
                            } else {
                                checkAuthenticatedUserTypeByPhone(phoneNumber, userTypes, index + 1);
                            }
                        } else {
                            Log.e(TAG, "Error checking user", task.getException());
                            Toast.makeText(MainActivity.this, "Error checking user status", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkAuthenticatedUserTypeByUID(String uid, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            Log.e(TAG, "User is authenticated but not found in any collection: " + uid);
            Toast.makeText(MainActivity.this, "Error finding user profile. Please login again.", Toast.LENGTH_SHORT).show();
            auth.signOut();
            clearStoredCredentials();
            return;
        }

        db.collection("Sagip")
                .document("users")
                .collection(userTypes[index])
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String status = document.getString("status");
                        if (status != null && status.equals("approved")) {
                            // Save user credentials before redirecting
                            FirebaseUser currentUser = auth.getCurrentUser();
                            if (currentUser != null) {
                                saveUserCredentials(uid, userTypes[index], currentUser.getEmail());
                            }
                            redirectToUserDashboard(userTypes[index]);
                        } else {
                            showPendingApprovalMessage();
                            auth.signOut();
                            clearStoredCredentials();
                        }
                    } else {
                        checkAuthenticatedUserTypeByUID(uid, userTypes, index + 1);
                    }
                })
                .addOnFailureListener(e -> {
                    checkAuthenticatedUserTypeByUID(uid, userTypes, index + 1);
                });
    }

    private void redirectToUserDashboard(String userType) {
        Intent dashboardIntent;

        switch (userType) {
            case "seniors":
            case "senior":
                dashboardIntent = new Intent(MainActivity.this, Senior_Dashboard.class);
                break;
            case "rescuer":
                dashboardIntent = new Intent(MainActivity.this, Rescuer_Dashboard.class);
                break;
            case "hospital":
                dashboardIntent = new Intent(MainActivity.this, Hospital_Dashboard.class);
                break;
            case "barangay":
                dashboardIntent = new Intent(MainActivity.this, Barangay_Dashboard.class);
                break;
            default:
                dashboardIntent = new Intent(MainActivity.this, Senior_Dashboard.class);
                break;
        }

        startActivity(dashboardIntent);
        finish();
    }

    private void checkUserExistsByPhoneNumber(String formattedNumber) {
        String[] userTypes = {"seniors", "user", "rescuer", "barangay"};
        checkPhoneNumberInCollections(formattedNumber, userTypes, 0);
    }

    private void checkPhoneNumberInCollections(String formattedNumber, String[] userTypes, int index) {
        if (index >= userTypes.length) {
            String plainNumber = formattedNumber.substring(3);
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
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String status = document.getString("status");
                                    if (status != null && status.equals("approved")) {
                                        String plainNumber = formattedNumber.substring(3);
                                        sendOtp(plainNumber, false);
                                    } else {
                                        showPendingApprovalMessage();
                                    }
                                    return;
                                }
                            } else {
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
                        Toast.makeText(MainActivity.this, "Failed to send OTP: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                        Log.d(TAG, "OTP code sent successfully");
                        Intent intent = new Intent(MainActivity.this, OTP_PAGE.class);
                        intent.putExtra("VERIFICATION_ID", verificationId);
                        intent.putExtra("MOBILE_NUMBER", "+63" + number);
                        intent.putExtra("IS_NEW_USER", isNewUser);
                        startActivity(intent);
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private boolean isValidPhoneNumber(String number) {
        return !TextUtils.isEmpty(number) && number.matches("\\d{10}");
    }

    private void showProgressBar(boolean show) {
        ProgressBar progressBar = findViewById(R.id.progressBar);
        if (progressBar != null) {
            if (show) {
                progressBar.setVisibility(View.VISIBLE);
                phoneLoginButton.setEnabled(false);
            } else {
                progressBar.setVisibility(View.GONE);
                phoneLoginButton.setEnabled(true);
            }
        }
    }
}